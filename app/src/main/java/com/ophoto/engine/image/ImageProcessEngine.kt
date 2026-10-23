package com.ophoto.engine.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import com.ophoto.engine.filter.FilterEngineImpl
import com.ophoto.engine.filter.FilterPreset
import com.ophoto.engine.metadata.CaptureExifMetadata
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CropAspectPreset(val label: String, val ratioW: Float?, val ratioH: Float?) {
    ORIGINAL("原比例", null, null),
    R_4_3("4:3", 4f, 3f),
    R_3_2("3:2", 3f, 2f),
    R_16_9("16:9", 16f, 9f),
    R_1_1("1:1", 1f, 1f),
    R_21_9("21:9", 21f, 9f),
}

enum class HslBand(val label: String, val centerDeg: Float, val swatchHex: Long) {
    RED("红", 0f, 0xFFE05252),
    ORANGE("橙", 30f, 0xFFE68A3E),
    YELLOW("黄", 60f, 0xFFE5C84B),
    GREEN("绿", 120f, 0xFF52B76A),
    AQUA("青", 180f, 0xFF3CC2C2),
    BLUE("蓝", 225f, 0xFF4D82E8),
    PURPLE("紫", 275f, 0xFF9B68E8),
    MAGENTA("品红", 320f, 0xFFD95BB8),
}

data class HslShift(
    val hue: Int = 0,        // -100 .. +100
    val saturation: Int = 0, // -100 .. +100
    val luminance: Int = 0,  // -100 .. +100
)

data class CurvePoint(
    val x: Float, // 0f .. 1f
    val y: Float, // 0f .. 1f
)

enum class LocalMaskType(val label: String) {
    NONE("无蒙版"),
    LINEAR_TOP("天空渐变"),
    RADIAL_CENTER("主体聚光"),
}

enum class FrameStyle(val label: String) {
    NONE("无边框"),
    CLASSIC_WHITE("纯白画廊"),
    OBSIDIAN_DARK("曜石黑金"),
    FILM_CONTACT("胶片底片"),
}

data class PhotoEditRecipe(
    val preset: FilterPreset? = null,
    val presetStrength: Float = 0.85f,
    val cropPreset: CropAspectPreset = CropAspectPreset.ORIGINAL,
    val rotationQuarterTurns: Int = 0,
    val straightenDegrees: Float = 0f,
    val mirrorHorizontal: Boolean = false,
    val masterCurve: List<CurvePoint> = defaultLinearCurve(),
    val redCurve: List<CurvePoint> = defaultLinearCurve(),
    val greenCurve: List<CurvePoint> = defaultLinearCurve(),
    val blueCurve: List<CurvePoint> = defaultLinearCurve(),
    val hslAdjustments: Map<HslBand, HslShift> = emptyMap(),
    val localMaskType: LocalMaskType = LocalMaskType.NONE,
    val localMaskEv: Float = 0f,
    val localMaskWarmth: Int = 0,
    val frameStyle: FrameStyle = FrameStyle.NONE,
    val customWatermarkText: String = "",
) {
    companion object {
        fun defaultLinearCurve(): List<CurvePoint> = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.25f, 0.25f),
            CurvePoint(0.50f, 0.50f),
            CurvePoint(0.75f, 0.75f),
            CurvePoint(1f, 1f),
        )
    }
}

object ImageProcessEngine {

    private val filterEngine = FilterEngineImpl()

    fun process(
        source: Bitmap,
        recipe: PhotoEditRecipe,
        metadata: CaptureExifMetadata = CaptureExifMetadata(),
    ): Bitmap {
        // 1. Geometric transforms (rotate / mirror / straighten / aspect crop)
        var current = applyGeometry(source, recipe)

        // 2. Filter / LUT / base tone grading
        if (recipe.preset != null && recipe.presetStrength > 0.001f) {
            filterEngine.loadFilter(recipe.preset)
            filterEngine.setStrength(recipe.presetStrength)
            val graded = filterEngine.applyToImage(current)
            if (current !== source) current.recycle()
            current = graded
        }

        // 3. Curves + 8-band HSL + Local Mask pass
        val hasCurveChange = !isIdentityCurve(recipe.masterCurve) ||
            !isIdentityCurve(recipe.redCurve) ||
            !isIdentityCurve(recipe.greenCurve) ||
            !isIdentityCurve(recipe.blueCurve)
        val hasHslChange = recipe.hslAdjustments.values.any {
            it.hue != 0 || it.saturation != 0 || it.luminance != 0
        }
        val hasLocalMask = recipe.localMaskType != LocalMaskType.NONE &&
            (abs(recipe.localMaskEv) > 0.01f || recipe.localMaskWarmth != 0)

        if (hasCurveChange || hasHslChange || hasLocalMask) {
            val advanced = applyCurvesHslAndMask(current, recipe, hasHslChange, hasLocalMask)
            if (current !== source) current.recycle()
            current = advanced
        }

        // 4. Optional Gallery Frame & EXIF Signature Watermark
        if (recipe.frameStyle != FrameStyle.NONE || recipe.customWatermarkText.isNotBlank()) {
            val framed = renderSignatureFrame(current, recipe.frameStyle, metadata, recipe.customWatermarkText)
            if (current !== source) current.recycle()
            current = framed
        }

        return current
    }

    fun buildCurveLut256(points: List<CurvePoint>): IntArray {
        val sorted = points
            .map { CurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
            .sortedBy { it.x }
            .distinctBy { (it.x * 1000).roundToInt() }

        if (sorted.isEmpty()) return IntArray(256) { it }
        if (sorted.size == 1) {
            val v = (sorted[0].y * 255f).roundToInt().coerceIn(0, 255)
            return IntArray(256) { v }
        }

        val n = sorted.size
        val xs = FloatArray(n) { sorted[it].x }
        val ys = FloatArray(n) { sorted[it].y }

        // Fritsch-Carlson monotone piecewise cubic interpolation (prevents overshoot ringing!)
        val deltas = FloatArray(n - 1) { i ->
            val dx = (xs[i + 1] - xs[i]).coerceAtLeast(1e-4f)
            (ys[i + 1] - ys[i]) / dx
        }
        val slopes = FloatArray(n)
        slopes[0] = deltas[0]
        slopes[n - 1] = deltas[n - 2]
        for (i in 1 until n - 1) {
            slopes[i] = if (deltas[i - 1] * deltas[i] <= 0f) 0f else (deltas[i - 1] + deltas[i]) * 0.5f
        }
        for (i in 0 until n - 1) {
            if (abs(deltas[i]) < 1e-5f) {
                slopes[i] = 0f
                slopes[i + 1] = 0f
            } else {
                val a = slopes[i] / deltas[i]
                val b = slopes[i + 1] / deltas[i]
                val h = hypot(a, b)
                if (h > 3f) {
                    val t = 3f / h
                    slopes[i] = t * a * deltas[i]
                    slopes[i + 1] = t * b * deltas[i]
                }
            }
        }

        val lut = IntArray(256)
        var seg = 0
        for (idx in 0..255) {
            val x = idx / 255f
            val y = when {
                x <= xs[0] -> ys[0]
                x >= xs[n - 1] -> ys[n - 1]
                else -> {
                    while (seg < n - 2 && x > xs[seg + 1]) seg++
                    val h = (xs[seg + 1] - xs[seg]).coerceAtLeast(1e-4f)
                    val t = ((x - xs[seg]) / h).coerceIn(0f, 1f)
                    val t2 = t * t
                    val t3 = t2 * t
                    val h00 = 2f * t3 - 3f * t2 + 1f
                    val h10 = t3 - 2f * t2 + t
                    val h01 = -2f * t3 + 3f * t2
                    val h11 = t3 - t2
                    h00 * ys[seg] + h10 * h * slopes[seg] + h01 * ys[seg + 1] + h11 * h * slopes[seg + 1]
                }
            }
            lut[idx] = (y.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun isIdentityCurve(points: List<CurvePoint>): Boolean {
        return points.all { abs(it.x - it.y) < 0.004f }
    }

    private fun applyGeometry(source: Bitmap, recipe: PhotoEditRecipe): Bitmap {
        val turns = ((recipe.rotationQuarterTurns % 4) + 4) % 4
        val hasMatrixTransform = turns != 0 || recipe.mirrorHorizontal || abs(recipe.straightenDegrees) > 0.05f

        var transformed = if (hasMatrixTransform) {
            val matrix = Matrix()
            if (recipe.mirrorHorizontal) {
                matrix.postScale(-1f, 1f, source.width * 0.5f, source.height * 0.5f)
            }
            val totalAngle = turns * 90f + recipe.straightenDegrees.coerceIn(-15f, 15f)
            if (abs(totalAngle) > 0.05f) {
                matrix.postRotate(totalAngle, source.width * 0.5f, source.height * 0.5f)
            }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val rw = recipe.cropPreset.ratioW
        val rh = recipe.cropPreset.ratioH
        if (rw != null && rh != null && transformed.width > 4 && transformed.height > 4) {
            val targetAspect = if (transformed.height > transformed.width) {
                min(rw, rh) / max(rw, rh)
            } else {
                max(rw, rh) / min(rw, rh)
            }
            val curAspect = transformed.width.toFloat() / transformed.height.toFloat()
            val (cropW, cropH) = if (curAspect > targetAspect) {
                (transformed.height * targetAspect).roundToInt().coerceAtLeast(1) to transformed.height
            } else {
                transformed.width to (transformed.width / targetAspect).roundToInt().coerceAtLeast(1)
            }
            val left = ((transformed.width - cropW) / 2).coerceAtLeast(0)
            val top = ((transformed.height - cropH) / 2).coerceAtLeast(0)
            if (cropW < transformed.width || cropH < transformed.height) {
                val cropped = Bitmap.createBitmap(transformed, left, top, cropW, cropH)
                if (transformed !== source) transformed.recycle()
                transformed = cropped
            }
        }
        return transformed
    }

    private fun applyCurvesHslAndMask(
        input: Bitmap,
        recipe: PhotoEditRecipe,
        hasHsl: Boolean,
        hasMask: Boolean,
    ): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)

        val masterLut = buildCurveLut256(recipe.masterCurve)
        val rLut = buildCurveLut256(recipe.redCurve)
        val gLut = buildCurveLut256(recipe.greenCurve)
        val bLut = buildCurveLut256(recipe.blueCurve)

        val hsv = FloatArray(3)
        val maskMul = 2.0f.pow(recipe.localMaskEv)
        val maskWarm = recipe.localMaskWarmth / 100f * 0.12f

        var i = 0
        for (y in 0 until h) {
            val ny = y.toFloat() / max(1, h - 1)
            for (x in 0 until w) {
                val nx = x.toFloat() / max(1, w - 1)
                val c = pixels[i]
                val a = (c ushr 24) and 0xFF
                var r = masterLut[rLut[(c ushr 16) and 0xFF]]
                var g = masterLut[gLut[(c ushr 8) and 0xFF]]
                var b = masterLut[bLut[c and 0xFF]]

                if (hasHsl) {
                    Color.RGBToHSV(r, g, b, hsv)
                    var hueDeg = hsv[0]
                    var sat = hsv[1]
                    var v = hsv[2]
                    for ((band, shift) in recipe.hslAdjustments) {
                        if (shift.hue == 0 && shift.saturation == 0 && shift.luminance == 0) continue
                        var dist = abs(hueDeg - band.centerDeg)
                        if (dist > 180f) dist = 360f - dist
                        if (dist < 42f) {
                            val weight = (1f - dist / 42f) * sat.coerceIn(0f, 1f)
                            hueDeg = (hueDeg + shift.hue * 0.30f * weight + 360f) % 360f
                            sat = (sat + (shift.saturation / 100f) * 0.65f * weight).coerceIn(0f, 1f)
                            v = (v + (shift.luminance / 100f) * 0.40f * weight).coerceIn(0f, 1f)
                        }
                    }
                    hsv[0] = hueDeg
                    hsv[1] = sat
                    hsv[2] = v
                    val adjusted = Color.HSVToColor(a, hsv)
                    r = Color.red(adjusted)
                    g = Color.green(adjusted)
                    b = Color.blue(adjusted)
                }

                if (hasMask) {
                    val weight = when (recipe.localMaskType) {
                        LocalMaskType.NONE -> 0f
                        LocalMaskType.LINEAR_TOP -> (1f - ny * 1.55f).coerceIn(0f, 1f)
                        LocalMaskType.RADIAL_CENTER -> {
                            val d = hypot(nx - 0.5f, ny - 0.5f) / 0.62f
                            (1f - d * d).coerceIn(0f, 1f)
                        }
                    }
                    if (weight > 0.002f) {
                        val effGain = 1f + (maskMul - 1f) * weight
                        r = ((r * effGain) + maskWarm * 255f * weight).roundToInt().coerceIn(0, 255)
                        g = (g * effGain).roundToInt().coerceIn(0, 255)
                        b = ((b * effGain) - maskWarm * 255f * weight).roundToInt().coerceIn(0, 255)
                    }
                }

                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                i++
            }
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    private fun renderSignatureFrame(
        photo: Bitmap,
        frameStyle: FrameStyle,
        metadata: CaptureExifMetadata,
        customText: String,
    ): Bitmap {
        if (frameStyle == FrameStyle.NONE) {
            val copy = photo.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(copy)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                textSize = (photo.width * 0.028f).coerceAtLeast(16f)
                typeface = Typeface.MONOSPACE
                setShadowLayer(4f, 0f, 2f, 0x99000000.toInt())
            }
            canvas.drawText(
                customText,
                photo.width * 0.035f,
                photo.height - photo.width * 0.035f,
                paint,
            )
            return copy
        }

        val sidePad = (photo.width * 0.045f).roundToInt().coerceAtLeast(16)
        val topPad = sidePad
        val bottomPad = (photo.width * 0.145f).roundToInt().coerceAtLeast(64)
        val totalW = photo.width + sidePad * 2
        val totalH = photo.height + topPad + bottomPad

        val bgColor = when (frameStyle) {
            FrameStyle.CLASSIC_WHITE -> Color.WHITE
            FrameStyle.OBSIDIAN_DARK -> Color.rgb(12, 13, 17)
            FrameStyle.FILM_CONTACT -> Color.rgb(20, 18, 16)
            FrameStyle.NONE -> Color.BLACK
        }
        val primaryTextColor = if (frameStyle == FrameStyle.CLASSIC_WHITE) Color.rgb(24, 26, 30) else Color.rgb(242, 238, 228)
        val secondaryTextColor = if (frameStyle == FrameStyle.CLASSIC_WHITE) Color.rgb(115, 120, 130) else Color.rgb(229, 184, 105)

        val output = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(bgColor)
        canvas.drawBitmap(photo, sidePad.toFloat(), topPad.toFloat(), null)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextColor
            textSize = (bottomPad * 0.25f).coerceAtLeast(16f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextColor
            textSize = (bottomPad * 0.19f).coerceAtLeast(13f)
            typeface = Typeface.MONOSPACE
        }

        val footerCenterY = topPad + photo.height + bottomPad * 0.52f
        val leftTitle = customText.ifBlank { metadata.model.ifBlank { "Ophoto Optical Studio" } }
        canvas.drawText(leftTitle, sidePad.toFloat(), footerCenterY - bottomPad * 0.06f, titlePaint)
        canvas.drawText(metadata.summaryLine(), sidePad.toFloat(), footerCenterY + bottomPad * 0.22f, subPaint)

        // Minimalist golden shutter accent mark on right side of footer
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 106, 43)
        }
        val dotR = (bottomPad * 0.075f).coerceAtLeast(4f)
        canvas.drawCircle(totalW - sidePad - dotR * 2f, footerCenterY, dotR, accentPaint)

        return output
    }
}
