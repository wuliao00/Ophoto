package com.ophoto.engine.filter

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Full 12-stage Color Grading & LUT Processing Engine per Doc §6:
 * 1. Base normalization
 * 2. Exposure stop adjustment
 * 3. White balance (Temperature + Green/Magenta Tint)
 * 4. Highlights / Shadows / Contrast / Saturation & Vibrance
 * 5. 3D .cube LUT trilinear mapping
 * 6. Split toning & highlight/shadow tint cast
 * 7. Black-point fade & soft glow lift
 * 8. Procedural film grain
 * 9. Optical radial vignette
 * 10. Warm corner film light leak
 * 11. 3x3 Unsharp luminance sharpening
 * 12. Strength blend & sRGB clamp
 */
class FilterEngineImpl : IFilterEngine {

    @Volatile
    private var activePreset: FilterPreset? = null

    @Volatile
    private var activeLut: CubeLut3D? = null

    @Volatile
    private var strength: Float = 1.0f

    override fun loadFilter(filter: FilterPreset): Boolean {
        activePreset = filter
        strength = filter.defaultStrength.coerceIn(0f, 1f)
        activeLut = filter.lutPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                val f = File(path)
                if (f.isFile) {
                    runCatching { f.inputStream().use { CubeLutParser.parse(it, filter.name) } }.getOrNull()
                } else {
                    null
                }
            }
        return true
    }

    override fun loadFilterWithExplicitLut(filter: FilterPreset, lut: CubeLut3D?) {
        activePreset = filter
        activeLut = lut
        strength = filter.defaultStrength.coerceIn(0f, 1f)
    }

    override fun applyToPreviewTexture(textureId: Int): Int {
        // Real-time GPU rendering passes textureId through GlFilterShaderProgram; when no external FBO
        // redirect is needed, returns the bound texture handle.
        return textureId
    }

    override fun setStrength(strength: Float) {
        this.strength = strength.coerceIn(0f, 1f)
    }

    override fun reset() {
        activePreset = null
        activeLut = null
        strength = 1.0f
    }

    override fun exportConfig(): FilterConfig {
        return FilterConfig(
            presetId = activePreset?.id,
            strength = strength,
            paramsOverride = activePreset?.params,
        )
    }

    override fun currentLut(): CubeLut3D? = activeLut

    override fun applyToImage(input: Bitmap): Bitmap {
        val preset = activePreset
        val mix = strength.coerceIn(0f, 1f)
        val width = input.width
        val height = input.height
        if (width <= 0 || height <= 0) return input.copy(Bitmap.Config.ARGB_8888, true)
        if (preset == null || mix <= 0.001f) {
            return input.copy(Bitmap.Config.ARGB_8888, true)
        }

        val total = width * height
        val srcPixels = IntArray(total)
        input.getPixels(srcPixels, 0, width, 0, 0, width, height)
        val gradedPixels = IntArray(total)

        val p = preset.params
        val lut = activeLut
        val lutOut = FloatArray(3)

        val expMul = 2.0f.pow(p.exposureEv)
        val tempShift = p.temperature / 100f * 0.14f
        val tintShift = p.tint / 100f * 0.10f
        val contrastFactor = 1.0f + (p.contrast / 100f) * 0.65f
        val satFactor = (1.0f + p.saturation / 100f).coerceAtLeast(0f)
        val vibFactor = p.vibrance / 100f * 0.5f
        val hiAdj = p.highlights / 100f * 0.25f
        val shAdj = p.shadows / 100f * 0.25f
        val blackLift = (p.fade / 100f) * 0.11f + (p.blacks / 100f) * 0.08f
        val whiteGain = 1.0f + (p.whites / 100f) * 0.12f

        val hiTint = parseHexRgb(p.highlightTintHex)
        val shTint = parseHexRgb(p.shadowTintHex)
        val castMix = (p.colorCastStrength / 100f).coerceIn(0f, 1f)
        val grainAmp = (p.grainStrength / 100f) * 0.085f
        val vignetteAmt = (p.vignette / 100f) * 0.62f
        val leakAmt = (p.lightLeak / 100f) * 0.35f
        val softAmt = (p.softGlow / 100f) * 0.22f

        val halfW = max(1f, width * 0.5f)
        val halfH = max(1f, height * 0.5f)

        var idx = 0
        for (y in 0 until height) {
            val ny = (y - halfH) / halfH
            val leakY = (1f - y.toFloat() / max(1, height)).coerceIn(0f, 1f)
            for (x in 0 until width) {
                val nx = (x - halfW) / halfW
                val argb = srcPixels[idx]
                val a = (argb ushr 24) and 0xFF
                val origR = ((argb ushr 16) and 0xFF) / 255f
                val origG = ((argb ushr 8) and 0xFF) / 255f
                val origB = (argb and 0xFF) / 255f

                // 1 & 2. Exposure
                var r = origR * expMul
                var g = origG * expMul
                var b = origB * expMul

                // 3. White balance & tint
                r += tempShift + tintShift * 0.35f
                g -= tintShift * 0.70f
                b -= tempShift - tintShift * 0.35f

                // 4. Shadows / Highlights / Contrast / Saturation
                var luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val shadowMask = (1f - luma).coerceIn(0f, 1f).let { it * it }
                val highlightMask = luma.coerceIn(0f, 1f).let { it * it }
                val toneDelta = shAdj * shadowMask + hiAdj * highlightMask
                r = (r + toneDelta) * whiteGain
                g = (g + toneDelta) * whiteGain
                b = (b + toneDelta) * whiteGain

                r = ((r - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
                g = ((g - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
                b = ((b - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)

                luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                if (p.monochrome) {
                    r = luma
                    g = luma
                    b = luma
                } else {
                    val maxC = max(r, max(g, b))
                    val minC = min(r, min(g, b))
                    val curSat = (maxC - minC).coerceIn(0f, 1f)
                    val effSat = (satFactor + vibFactor * (1f - curSat)).coerceAtLeast(0f)
                    r = (luma + (r - luma) * effSat).coerceIn(0f, 1f)
                    g = (luma + (g - luma) * effSat).coerceIn(0f, 1f)
                    b = (luma + (b - luma) * effSat).coerceIn(0f, 1f)
                }

                // 5. 3D LUT trilinear mapping
                if (lut != null) {
                    lut.sampleTrilinear(r, g, b, lutOut)
                    r = lutOut[0]
                    g = lutOut[1]
                    b = lutOut[2]
                }

                // 6. Split toning (Highlight / Shadow color cast)
                if (castMix > 0.001f) {
                    val lumNow = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
                    val shW = (1f - lumNow) * castMix * 0.35f
                    val hiW = lumNow * castMix * 0.28f
                    r = r * (1f - shW) + shTint[0] * shW
                    g = g * (1f - shW) + shTint[1] * shW
                    b = b * (1f - shW) + shTint[2] * shW
                    r = r * (1f - hiW) + hiTint[0] * hiW
                    g = g * (1f - hiW) + hiTint[1] * hiW
                    b = b * (1f - hiW) + hiTint[2] * hiW
                }

                // 7. Black-point fade & halation soft lift
                if (blackLift != 0f || softAmt > 0f) {
                    val lift = blackLift.coerceIn(-0.15f, 0.22f)
                    r = r * (1f - lift.coerceAtLeast(0f)) + lift
                    g = g * (1f - lift.coerceAtLeast(0f)) + lift
                    b = b * (1f - lift.coerceAtLeast(0f)) + lift
                    if (softAmt > 0f) {
                        val bright = max(r, max(g, b))
                        if (bright > 0.68f) {
                            val bloom = (bright - 0.68f) * softAmt
                            r += bloom * 1.05f
                            g += bloom * 0.96f
                            b += bloom * 0.88f
                        }
                    }
                }

                // 8. Procedural deterministic film grain
                if (grainAmp > 0.001f) {
                    val hash = ((x * 73856093) xor (y * 19349663) xor 0x5A17) and 0xFFFF
                    val noise = (hash / 32767.5f) - 1f
                    val midWeight = 1f - kotlin.math.abs(2f * luma - 1f) * 0.45f
                    val grain = noise * grainAmp * midWeight
                    r += grain
                    g += grain
                    b += grain
                }

                // 9. Optical radial vignette
                if (vignetteAmt > 0.001f) {
                    val dist = hypot(nx, ny) / 1.4142f
                    val falloff = (1f - vignetteAmt * dist * dist).coerceIn(0.2f, 1f)
                    r *= falloff
                    g *= falloff
                    b *= falloff
                }

                // 10. Warm corner light leak
                if (leakAmt > 0.001f) {
                    val leakX = (1f - x.toFloat() / max(1, width)).coerceIn(0f, 1f)
                    val corner = (leakX * 0.7f + leakY * 0.3f).pow(2.4f) * leakAmt
                    r += corner * 0.95f
                    g += corner * 0.44f
                    b += corner * 0.12f
                }

                // 12. Blend with strength
                val outR = ((origR + (r.coerceIn(0f, 1f) - origR) * mix) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outG = ((origG + (g.coerceIn(0f, 1f) - origG) * mix) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outB = ((origB + (b.coerceIn(0f, 1f) - origB) * mix) * 255f + 0.5f).toInt().coerceIn(0, 255)

                gradedPixels[idx] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
                idx++
            }
        }

        // 11. Optional 3x3 Unsharp Mask pass when sharpness > 0
        val finalPixels = if (p.sharpness > 4 && width >= 3 && height >= 3) {
            applyUnsharp3x3(gradedPixels, width, height, (p.sharpness / 100f) * 0.55f * mix)
        } else {
            gradedPixels
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(finalPixels, 0, width, 0, 0, width, height)
        }
    }

    override fun computeHistogram(bitmap: Bitmap): HistogramData {
        val red = IntArray(64)
        val green = IntArray(64)
        val blue = IntArray(64)
        val luma = IntArray(64)

        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return HistogramData(red, green, blue, luma)

        val stepX = max(1, w / 96)
        val stepY = max(1, h / 96)
        var samples = 0
        var overCount = 0
        var underCount = 0
        var sumLuma = 0f

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
                red[r ushr 2]++
                green[g ushr 2]++
                blue[b ushr 2]++
                luma[lum ushr 2]++
                if (r >= 250 || g >= 250 || b >= 250) overCount++
                if (lum <= 6) underCount++
                sumLuma += lum / 255f
                samples++
                x += stepX
            }
            y += stepY
        }

        val total = max(1, samples).toFloat()
        val meanLuma = (sumLuma / total).coerceIn(0.01f, 0.99f)
        val evOffset = (ln(meanLuma / 0.42f) / ln(2.0f)).coerceIn(-3f, 3f)

        return HistogramData(
            redBins = red,
            greenBins = green,
            blueBins = blue,
            lumaBins = luma,
            highlightClipPercent = (overCount / total) * 100f,
            shadowClipPercent = (underCount / total) * 100f,
            averageEvOffset = evOffset,
        )
    }

    companion object {
        fun parseHexRgb(hex: String): FloatArray {
            val cleaned = hex.trim().removePrefix("#")
            val rgbInt = cleaned.toLongOrNull(16)?.toInt() ?: 0x808080
            return floatArrayOf(
                ((rgbInt ushr 16) and 0xFF) / 255f,
                ((rgbInt ushr 8) and 0xFF) / 255f,
                (rgbInt and 0xFF) / 255f,
            )
        }

        fun applyUnsharp3x3(src: IntArray, width: Int, height: Int, amount: Float): IntArray {
            val dst = src.copyOf()
            for (y in 1 until height - 1) {
                val row = y * width
                for (x in 1 until width - 1) {
                    val i = row + x
                    val c = src[i]
                    val top = src[i - width]
                    val bottom = src[i + width]
                    val left = src[i - 1]
                    val right = src[i + 1]

                    val cr = (c ushr 16) and 0xFF
                    val cg = (c ushr 8) and 0xFF
                    val cb = c and 0xFF

                    val blurR = (((top ushr 16) and 0xFF) + ((bottom ushr 16) and 0xFF) + ((left ushr 16) and 0xFF) + ((right ushr 16) and 0xFF)) * 0.25f
                    val blurG = (((top ushr 8) and 0xFF) + ((bottom ushr 8) and 0xFF) + ((left ushr 8) and 0xFF) + ((right ushr 8) and 0xFF)) * 0.25f
                    val blurB = ((top and 0xFF) + (bottom and 0xFF) + (left and 0xFF) + (right and 0xFF)) * 0.25f

                    val nr = (cr + (cr - blurR) * amount).toInt().coerceIn(0, 255)
                    val ng = (cg + (cg - blurG) * amount).toInt().coerceIn(0, 255)
                    val nb = (cb + (cb - blurB) * amount).toInt().coerceIn(0, 255)

                    dst[i] = (c and -0x1000000) or (nr shl 16) or (ng shl 8) or nb
                }
            }
            return dst
        }
    }
}
