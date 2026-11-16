package com.ophoto.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ophoto.engine.filter.HistogramData
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 取景画面亮度统计（从真实预览帧采样，用于自动提亮、直方图与黑场自诊断）。
 */
@Immutable
data class FrameLuma(
    val mean: Float = 0f,          // 0..1
    val p05: Float = 0f,
    val p95: Float = 1f,
    val isBlack: Boolean = false,
    val isDim: Boolean = false,
    val sampledWidth: Int = 0,
    val sampledHeight: Int = 0,
) {
    val meanPercent: Int get() = (mean * 100f).roundToInt().coerceIn(0, 100)
}

/**
 * 低开销预览采样器：复用像素数组，单次采样 ~20k 像素，
 * 1Hz 级别的调用对 ColorOS 60fps 预览几乎无感。
 */
class PreviewSampler {

    private var pixels: IntArray = IntArray(0)

    fun sample(bitmap: Bitmap?, maxEdge: Int = 160): FrameLuma? {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) return null
        val scale = minOf(1f, maxEdge.toFloat() / max(bitmap.width, bitmap.height))
        val w = (bitmap.width * scale).toInt().coerceAtLeast(8)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(8)
        val needed = w * h
        if (pixels.size < needed) pixels = IntArray(needed)
        val scaled = if (w != bitmap.width || h != bitmap.height) {
            runCatching { Bitmap.createScaledBitmap(bitmap, w, h, false) }.getOrNull() ?: return null
        } else {
            bitmap
        }
        return runCatching {
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            if (scaled !== bitmap) scaled.recycle()

            var sum = 0f
            var minL = 255
            var maxL = 0
            val hist = IntArray(32)
            for (i in 0 until needed) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt()
                sum += l
                if (l < minL) minL = l
                if (l > maxL) maxL = l
                hist[(l shr 3).coerceIn(0, 31)]++
            }
            val mean = sum / needed / 255f
            FrameLuma(
                mean = mean,
                p05 = minL / 255f,
                p95 = maxL / 255f,
                isBlack = mean <= 0.045f,
                isDim = mean in 0.046f..0.22f,
                sampledWidth = w,
                sampledHeight = h,
            )
        }.getOrNull()
    }

    /** 由采样帧生成 64 段 RGB/亮度直方图，喂给取景 HUD。 */
    fun histogram(bitmap: Bitmap?): HistogramData? {
        if (bitmap == null || bitmap.isRecycled) return null
        val scale = minOf(1f, 120f / max(bitmap.width, bitmap.height))
        val w = (bitmap.width * scale).toInt().coerceAtLeast(8)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(8)
        val scaled = runCatching { Bitmap.createScaledBitmap(bitmap, w, h, false) }.getOrNull() ?: return null
        return runCatching {
            val px = IntArray(w * h)
            scaled.getPixels(px, 0, w, 0, 0, w, h)
            if (scaled !== bitmap) scaled.recycle()
            val red = IntArray(64)
            val green = IntArray(64)
            val blue = IntArray(64)
            val luma = IntArray(64)
            var clipHi = 0
            var clipLo = 0
            var sumLuma = 0f
            for (p in px) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                red[r ushr 2]++
                green[g ushr 2]++
                blue[b ushr 2]++
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
                luma[l ushr 2]++
                if (r >= 250 || g >= 250 || b >= 250) clipHi++
                if (l <= 6) clipLo++
                sumLuma += l / 255f
            }
            val total = px.size.toFloat()
            HistogramData(
                redBins = red,
                greenBins = green,
                blueBins = blue,
                lumaBins = luma,
                highlightClipPercent = clipHi / total * 100f,
                shadowClipPercent = clipLo / total * 100f,
                averageEvOffset = (sumLuma / total),
            )
        }.getOrNull()
    }

    companion object {
        /**
         * 根据画面均值反推需要的观感提亮量（0..0.34），只影响取景显示，不影响成片。
         * - 目标观感亮度 42%：低于该值才抬亮，正常画面完全不干预；
         * - 接近全黑时额外补一点，保证「能看清构图」而不是把黑抬成灰雾；
         * - 上限 0.34，避免夜景被洗成白天。
         */
        fun requiredLift(luma: FrameLuma?, target: Float = 0.42f): Float {
            if (luma == null) return 0f
            if (luma.mean >= target) return 0f
            val deficit = ((target - luma.mean) / target).coerceIn(0f, 1f)
            val base = deficit * 0.46f
            val blackBoost = if (luma.mean <= 0.05f) 0.08f else 0f
            return (base + blackBoost).coerceIn(0f, 0.34f)
        }
    }
}

/**
 * 取景提亮层：以 Screen 混合叠一层中性光，把 ColorOS 压黑的预览「抬起来」，
 * 只作用于屏幕观感，拍照/录像的 RAW/JPEG 完全不受影响。
 */
@Composable
fun ViewfinderLiftOverlay(
    lift: Float,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF3F6FF),
) {
    if (lift <= 0.002f) return
    Canvas(modifier = modifier) {
        drawRect(color = tint, alpha = lift.coerceIn(0f, 0.55f), blendMode = BlendMode.Screen)
    }
}

/**
 * 取景暗部柔光：黑场时靠近中心的补偿更强，避免整屏发灰。
 */
@Composable
fun ViewfinderBloomOverlay(
    strength: Float,
    modifier: Modifier = Modifier,
) {
    if (strength <= 0.002f) return
    Canvas(modifier = modifier) {
        val radial = androidx.compose.ui.graphics.Brush.radialGradient(
            0f to Color.White.copy(alpha = strength * 0.5f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = size.minDimension * 0.78f,
        )
        drawRect(brush = radial, blendMode = BlendMode.Screen)
    }
}

/**
 * 高光斑马纹（96% 裁切预警）：在采样网格上标出过曝区域，
 * 相比逐像素着色器实现更省电，也能给出「哪片区域」正在溢出的直觉。
 */
@Composable
fun ZebraOverlay(
    bitmap: Bitmap?,
    threshold: Float = 0.96f,
    modifier: Modifier = Modifier,
    columns: Int = 24,
    rows: Int = 32,
) {
    if (bitmap == null || bitmap.isRecycled) return
    Canvas(modifier = modifier) {
        val cellW = size.width / columns
        val cellH = size.height / rows
        val stepX = (bitmap.width / columns).coerceAtLeast(1)
        val stepY = (bitmap.height / rows).coerceAtLeast(1)
        val cut = (threshold * 255f).toInt()
        for (ry in 0 until rows) {
            for (rx in 0 until columns) {
                val px = (rx * stepX).coerceIn(0, bitmap.width - 1)
                val py = (ry * stepY).coerceIn(0, bitmap.height - 1)
                val c = runCatching { bitmap.getPixel(px, py) }.getOrNull() ?: continue
                val r = android.graphics.Color.red(c)
                val g = android.graphics.Color.green(c)
                val b = android.graphics.Color.blue(c)
                if (r >= cut || g >= cut || b >= cut) {
                    val left = rx * cellW
                    val top = ry * cellH
                    // 斜向条纹，避免填满整格造成视觉噪声
                    var offset = -cellH
                    while (offset < cellW) {
                        drawLine(
                            color = Color(0xFFFFD34D).copy(alpha = 0.72f),
                            start = Offset(left + offset, top + cellH),
                            end = Offset(left + offset + cellH * 0.7f, top),
                            strokeWidth = 1.6.dp.toPx(),
                        )
                        offset += 7.dp.toPx()
                    }
                }
            }
        }
    }
}

/** 取景框边角的测距刻度，提升专业仪器感（纯装饰，开销可忽略）。 */
@Composable
fun FinderCornerTicks(
    accent: Color = Color.White.copy(alpha = 0.5f),
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val arm = 18.dp.toPx()
        val inset = 10.dp.toPx()
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        // 左上
        drawLine(accent, Offset(inset, inset), Offset(inset + arm, inset), stroke)
        drawLine(accent, Offset(inset, inset), Offset(inset, inset + arm), stroke)
        // 右上
        drawLine(accent, Offset(w - inset, inset), Offset(w - inset - arm, inset), stroke)
        drawLine(accent, Offset(w - inset, inset), Offset(w - inset, inset + arm), stroke)
        // 左下
        drawLine(accent, Offset(inset, h - inset), Offset(inset + arm, h - inset), stroke)
        drawLine(accent, Offset(inset, h - inset), Offset(inset, h - inset - arm), stroke)
        // 右下
        drawLine(accent, Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), stroke)
        drawLine(accent, Offset(w - inset, h - inset), Offset(w - inset, h - inset - arm), stroke)
        // 中心对焦框刻度
        drawRect(
            color = accent.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.5f - 26.dp.toPx(), h * 0.5f - 26.dp.toPx()),
            size = Size(52.dp.toPx(), 52.dp.toPx()),
            style = Stroke(1.dp.toPx()),
        )
    }
}

/** 取景容器尺寸（预览可见区域），用于把点击对焦坐标换算成 0..1。 */
internal fun normalizedTap(x: Float, y: Float, width: Int, height: Int): Pair<Float, Float> {
    val nx = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
    val ny = (y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
    return nx to ny
}
