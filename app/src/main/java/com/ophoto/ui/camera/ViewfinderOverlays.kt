package com.ophoto.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ophoto.data.local.CompositionGuideType
import com.ophoto.engine.filter.HistogramData
import com.ophoto.ui.theme.AlertCrimson
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.CyanTelemetry
import com.ophoto.ui.theme.OpticalEmerald
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberDeviceRollDegrees(enabled: Boolean): Float {
    val context = LocalContext.current
    var angleDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context, enabled) {
        if (!enabled) {
            angleDeg = 0f
            onDispose { }
        } else {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val gx = event.values.getOrElse(0) { 0f }
                    val gy = event.values.getOrElse(1) { 9.8f }
                    val rawDeg = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
                    // Low-pass filter for smooth optical level motion
                    angleDeg = angleDeg * 0.82f + rawDeg.coerceIn(-45f, 45f) * 0.18f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (sensor != null) {
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose {
                sm?.unregisterListener(listener)
            }
        }
    }
    return angleDeg
}

@Composable
fun CompositionOverlayCanvas(
    guideType: CompositionGuideType,
    showLeveler: Boolean,
    rollDegrees: Float,
    focusTapNormalized: Pair<Float, Float>?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.34f)
        val goldAccent = BrassGold.copy(alpha = 0.62f)
        val strokeThin = 1.dp.toPx()

        when (guideType) {
            CompositionGuideType.RULE_OF_THIRDS -> {
                for (i in 1..2) {
                    drawLine(lineColor, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeThin)
                    drawLine(lineColor, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeThin)
                }
                // Optical power-point dots
                for (ix in 1..2) {
                    for (iy in 1..2) {
                        drawCircle(goldAccent, radius = 3.dp.toPx(), center = Offset(w * ix / 3f, h * iy / 3f))
                    }
                }
            }

            CompositionGuideType.GOLDEN_SPIRAL -> {
                val phi = 0.618f
                var left = 0f
                var top = 0f
                var right = w
                var bottom = h
                val spiralPath = Path()
                for (step in 0 until 6) {
                    val rw = right - left
                    val rh = bottom - top
                    when (step % 4) {
                        0 -> {
                            val split = left + rw * phi
                            drawLine(lineColor, Offset(split, top), Offset(split, bottom), strokeThin)
                            spiralPath.addArc(
                                androidx.compose.ui.geometry.Rect(left, top, split + (split - left), bottom),
                                180f,
                                90f,
                            )
                            left = split
                        }
                        1 -> {
                            val split = top + rh * phi
                            drawLine(lineColor, Offset(left, split), Offset(right, split), strokeThin)
                            spiralPath.addArc(
                                androidx.compose.ui.geometry.Rect(left - (right - left), top, right, split + (split - top)),
                                270f,
                                90f,
                            )
                            top = split
                        }
                        2 -> {
                            val split = right - rw * phi
                            drawLine(lineColor, Offset(split, top), Offset(split, bottom), strokeThin)
                            right = split
                        }
                        3 -> {
                            val split = bottom - rh * phi
                            drawLine(lineColor, Offset(left, split), Offset(right, split), strokeThin)
                            bottom = split
                        }
                    }
                }
                drawPath(spiralPath, goldAccent, style = Stroke(width = 1.6.dp.toPx()))
                drawCircle(
                    BrassGold,
                    radius = 4.dp.toPx(),
                    center = Offset((left + right) * 0.5f, (top + bottom) * 0.5f),
                )
            }

            CompositionGuideType.DIAGONAL -> {
                drawLine(lineColor, Offset(0f, 0f), Offset(w, h), strokeThin)
                drawLine(lineColor, Offset(w, 0f), Offset(0f, h), strokeThin)
                drawLine(goldAccent, Offset(0f, h * 0.5f), Offset(w * 0.5f, 0f), strokeThin)
                drawLine(goldAccent, Offset(w * 0.5f, h), Offset(w, h * 0.5f), strokeThin)
            }

            CompositionGuideType.CENTER_CROSS -> {
                val cx = w * 0.5f
                val cy = h * 0.5f
                val arm = 22.dp.toPx()
                drawLine(BrassGold, Offset(cx - arm, cy), Offset(cx + arm, cy), 1.5.dp.toPx())
                drawLine(BrassGold, Offset(cx, cy - arm), Offset(cx, cy + arm), 1.5.dp.toPx())
                drawCircle(lineColor, radius = 38.dp.toPx(), center = Offset(cx, cy), style = Stroke(strokeThin))
            }

            CompositionGuideType.CINEMA_SAFE -> {
                val padX = w * 0.05f
                val padY = h * 0.05f
                drawRect(
                    lineColor,
                    topLeft = Offset(padX, padY),
                    size = Size(w - padX * 2, h - padY * 2),
                    style = Stroke(strokeThin),
                )
                val barH = (h - w / 2.39f).coerceAtLeast(0f) * 0.5f
                if (barH > 4f) {
                    drawLine(goldAccent, Offset(0f, barH), Offset(w, barH), 1.4.dp.toPx())
                    drawLine(goldAccent, Offset(0f, h - barH), Offset(w, h - barH), 1.4.dp.toPx())
                }
            }

            CompositionGuideType.NONE -> Unit
        }

        // Electronic Horizon Leveler
        if (showLeveler) {
            val isLevel = abs(rollDegrees) <= 0.85f
            val levelColor = if (isLevel) OpticalEmerald else BrassGold.copy(alpha = 0.85f)
            val cx = w * 0.5f
            val cy = h * 0.5f
            val rad = Math.toRadians((-rollDegrees).toDouble())
            val halfSpan = 42.dp.toPx()
            val dx = (cos(rad) * halfSpan).toFloat()
            val dy = (sin(rad) * halfSpan).toFloat()

            // Fixed reference ticks
            drawLine(
                Color.White.copy(alpha = 0.45f),
                Offset(cx - halfSpan - 14.dp.toPx(), cy),
                Offset(cx - halfSpan - 4.dp.toPx(), cy),
                1.5.dp.toPx(),
            )
            drawLine(
                Color.White.copy(alpha = 0.45f),
                Offset(cx + halfSpan + 4.dp.toPx(), cy),
                Offset(cx + halfSpan + 14.dp.toPx(), cy),
                1.5.dp.toPx(),
            )
            // Dynamic horizon needle
            drawLine(
                color = levelColor,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = if (isLevel) 2.4.dp.toPx() else 1.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Touch Focus Reticle
        focusTapNormalized?.let { (nx, ny) ->
            val fx = nx.coerceIn(0.08f, 0.92f) * w
            val fy = ny.coerceIn(0.08f, 0.92f) * h
            val box = 32.dp.toPx()
            drawRect(
                color = BrassGold,
                topLeft = Offset(fx - box, fy - box),
                size = Size(box * 2f, box * 2f),
                style = Stroke(width = 1.8.dp.toPx()),
            )
            drawCircle(BrassGold, radius = 2.5.dp.toPx(), center = Offset(fx, fy))
        }
    }
}

@Composable
fun LiveHistogramHud(
    histogram: HistogramData,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xAD0B0D12), RoundedCornerShape(10.dp))
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "HIST",
                style = MaterialTheme.typography.labelSmall,
                color = BrassGold,
            )
            val clipText = if (histogram.highlightClipPercent > 1.5f) {
                "高光溢出 ${"%.1f".format(histogram.highlightClipPercent)}%"
            } else {
                "动态正常"
            }
            Text(
                text = clipText,
                style = MaterialTheme.typography.labelSmall,
                color = if (histogram.highlightClipPercent > 1.5f) AlertCrimson else CyanTelemetry,
            )
        }
        Box(
            modifier = Modifier
                .width(116.dp)
                .height(40.dp)
                .padding(top = 3.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bins = histogram.lumaBins
                val rBins = histogram.redBins
                val bBins = histogram.blueBins
                val peak = (bins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                val stepW = size.width / bins.size.toFloat()

                val lumaPath = Path()
                val redPath = Path()
                val bluePath = Path()

                bins.forEachIndexed { i, count ->
                    val x = i * stepW
                    val ly = size.height * (1f - (count / peak).coerceIn(0f, 1f))
                    val ry = size.height * (1f - (rBins.getOrElse(i) { 0 } / peak).coerceIn(0f, 1f))
                    val by = size.height * (1f - (bBins.getOrElse(i) { 0 } / peak).coerceIn(0f, 1f))
                    if (i == 0) {
                        lumaPath.moveTo(x, ly)
                        redPath.moveTo(x, ry)
                        bluePath.moveTo(x, by)
                    } else {
                        lumaPath.lineTo(x, ly)
                        redPath.lineTo(x, ry)
                        bluePath.lineTo(x, by)
                    }
                }
                drawPath(redPath, Color(0x99F87171), style = Stroke(1.dp.toPx()))
                drawPath(bluePath, Color(0x9960A5FA), style = Stroke(1.dp.toPx()))
                drawPath(lumaPath, Color.White.copy(alpha = 0.9f), style = Stroke(1.4.dp.toPx()))
            }
        }
    }
}
