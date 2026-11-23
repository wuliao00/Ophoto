package com.ophoto.engine.image

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 光学柔化与胶片质感引擎（Optical Soft & Film Rendering）
 *
 * 这里实现的是「实体滤镜的光学模拟」，而不是简单的高斯模糊叠加：
 *
 * 1. **柔光 / 雾化滤镜**（HDF、1/8–1/2 黑柔、白柔、Pro-Mist、珠光、柔焦）
 *    真实效果来自光在胶体里的多次散射。每种滤镜用一条**光锥角度分布**描述：
 *    大部分光原样通过（core），其余按散射角分布到周围（halo）。
 *    把散射角换算成像素尺度（取决于画面尺寸），再积分到倍增金字塔的各层，
 *    就得到每层、每通道的权重——这正是物理滤镜在画面上高光溢散的来源，
 *    也是「黑柔压高光、而白柔抬高整片亮部」这种差别的原因。
 *
 * 2. **光晕 / 溢光**（Halation）——胶片片基反射造成的红色光束扩散，
 *    只作用于接近剪切的高光，所以是「高光边缘泛红」而不是整体加红。
 *
 * 3. **胶片锐度**（乳剂边缘效应）——高光核清晰、平坦区域柔和，
 *    用「按局部对比度自适应的锐化」实现，模拟 Portra 类的细腻锐度。
 *
 * 4. **理光 GR 风格对比度曲线**——保留中间调、压缩两端，用于成片前的影调微调。
 *
 * 全部是纯 Kotlin 实现（无 NDK、无第三方代码），只依赖 android.graphics.Bitmap。
 */
object OpticalSoftEngine {

    /** 一层金字塔：内部以可分离盒式模糊近似高斯 / 光锥散射。 */
    private class Pyramid(val width: Int, val height: Int, val rgb: FloatArray)

    /**
     * 柔光滤镜的物理描述。
     *
     * @param core      原样通过的光比例（0..1）
     * @param scatterUm 散射角半径（单位：微米级的光学散射量，换算为画面比例）
     * @param warmth    散射光的色温偏移（正 = 偏暖，负 = 偏冷）
     * @param highlightBias 散射只取高光（1 = 只散射高光，0 = 均匀散射全场光）
     */
    data class SoftFilterSpec(
        val id: String,
        val displayName: String,
        val core: Float,
        val scatterRatio: Float,
        val warmth: Float,
        val highlightBias: Float,
        val note: String,
    )

    val SOFT_FILTERS: List<SoftFilterSpec> = listOf(
        SoftFilterSpec(
            id = "hdf",
            displayName = "高光衍射（HDF）",
            core = 0.80f,
            scatterRatio = 0.055f,
            warmth = 0.04f,
            highlightBias = 1.0f,
            note = "只溢出灯源与高光，画面其余部分保持通透",
        ),
        SoftFilterSpec(
            id = "mist_1_8",
            displayName = "1/8 黑柔",
            core = 0.90f,
            scatterRatio = 0.030f,
            warmth = 0.0f,
            highlightBias = 0.85f,
            note = "最轻的一档，只给高光一点呼吸",
        ),
        SoftFilterSpec(
            id = "mist_1_4",
            displayName = "1/4 黑柔",
            core = 0.84f,
            scatterRatio = 0.045f,
            warmth = 0.0f,
            highlightBias = 0.85f,
            note = "人像常用档，皮肤高光柔而不糊",
        ),
        SoftFilterSpec(
            id = "mist_1_2",
            displayName = "1/2 黑柔",
            core = 0.76f,
            scatterRatio = 0.065f,
            warmth = 0.0f,
            highlightBias = 0.80f,
            note = "高光明显扩散，黑位仍然扎实",
        ),
        SoftFilterSpec(
            id = "white_mist",
            displayName = "白柔",
            core = 0.70f,
            scatterRatio = 0.075f,
            warmth = 0.05f,
            highlightBias = 0.35f,
            note = "散射取自全场光，整片提亮、对比下降",
        ),
        SoftFilterSpec(
            id = "pro_mist",
            displayName = "Pro-Mist",
            core = 0.74f,
            scatterRatio = 0.085f,
            warmth = 0.06f,
            highlightBias = 0.55f,
            note = "雾面更宽，灯源周围形成一层薄纱",
        ),
        SoftFilterSpec(
            id = "pearl",
            displayName = "珠光",
            core = 0.82f,
            scatterRatio = 0.040f,
            warmth = 0.10f,
            highlightBias = 0.65f,
            note = "散射偏暖珍珠色，奢华感强",
        ),
        SoftFilterSpec(
            id = "diffusion",
            displayName = "柔焦镜",
            core = 0.88f,
            scatterRatio = 0.022f,
            warmth = 0.02f,
            highlightBias = 0.45f,
            note = "整体轻微柔化，老镜头味道",
        ),
        SoftFilterSpec(
            id = "fog",
            displayName = "雾面镜",
            core = 0.62f,
            scatterRatio = 0.110f,
            warmth = 0.03f,
            highlightBias = 0.30f,
            note = "大范围散射，适合雾天与逆光氛围",
        ),
    )

    fun findSoftFilter(id: String): SoftFilterSpec? = SOFT_FILTERS.firstOrNull { it.id == id }

    // ─────────────────────────────────────────────────────────────────────
    // 对外入口
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 应用物理柔光。
     *
     * @param amount 0..1，滤镜强度（浓度）
     */
    fun applySoftFilter(
        source: Bitmap,
        spec: SoftFilterSpec,
        amount: Float = 1f,
        /** 光晕（Halation）：在柔光之外再叠加一层红向散射，0..1。 */
        halation: Float = 0f,
        /** 额外柔化半径倍率，1 为滤镜原生光学尺寸。 */
        radiusScale: Float = 1f,
    ): Bitmap {
        if (amount <= 0.001f) return source.copy(Bitmap.Config.ARGB_8888, false)
        val w = source.width
        val h = source.height
        if (w < 8 || h < 8) return source.copy(Bitmap.Config.ARGB_8888, false)

        val src = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }

        // 散射只需在低分辨率上求解（光学尺度本身就是模糊的），
        // 原图分辨率只承载 core 与最终合成，保证锐度不丢。
        val workLong = 1280
        val longEdge = max(w, h)
        val scale = if (longEdge > workLong) workLong.toFloat() / longEdge else 1f
        val ww = max(8, (w * scale).roundToInt())
        val wh = max(8, (h * scale).roundToInt())

        val workSrc = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, ww, wh, true)
        } else {
            source
        }
        val workArgb = IntArray(ww * wh).also { workSrc.getPixels(it, 0, ww, 0, 0, ww, wh) }
        if (workSrc !== source) workSrc.recycle()

        // 线性化到 scene light（去 gamma），散射必须在线性光上做才符合物理
        val lin = linearize(workArgb, ww * wh)

        val scatterPx = (max(ww, wh) * spec.scatterRatio * radiusScale).coerceIn(1.5f, max(ww, wh) * 0.45f)
        val levels = pyramidLevels(scatterPx)
        val weights = scatterWeights(levels, scatterPx)

        val pyramid = buildPyramid(lin, ww, wh, levels)
        val haloSmall = reconstructHalo(pyramid, weights)
        val halo = if (ww == w && wh == h) haloSmall else upsample(haloSmall, ww, wh, w, h)

        // 散射通道色偏：暖滤镜的散射偏红，冷滤镜偏青
        val wR = 1f + spec.warmth * 0.9f
        val wG = 1f - spec.warmth * 0.15f
        val wB = 1f - spec.warmth * 0.95f

        val out = IntArray(w * h)
        val core = spec.core
        val trans = 1f - core
        val hiBias = spec.highlightBias
        val halR = halation.coerceIn(0f, 1f)
        val amt = amount.coerceIn(0f, 1f)

        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src[i]
                val lr = srgbToLinear(((c ushr 16) and 0xFF) / 255f)
                val lg = srgbToLinear(((c ushr 8) and 0xFF) / 255f)
                val lb = srgbToLinear((c and 0xFF) / 255f)
                val h3 = i * 3
                val sr = halo[h3]
                val sg = halo[h3 + 1]
                val sb = halo[h3 + 2]

                val lum = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb
                // 高光门控：亮度越接近上剪切，越允许散射进入
                val gate = mix(1f, smoothstep(0.30f, 0.92f, lum), hiBias)

                var or = lr * core + trans * sr * gate * wR
                var og = lg * core + trans * sg * gate * wG
                var ob = lb * core + trans * sb * gate * wB

                if (halR > 0.001f) {
                    val hot = smoothstep(0.62f, 1.0f, lum)
                    if (hot > 0.001f) {
                        val k = halR * hot * 0.55f
                        or += sr * k * 1.0f
                        og += sg * k * 0.42f
                        ob += sb * k * 0.22f
                    }
                }

                out[i] = pack(
                    srgb(or * amt + lr * (1f - amt)),
                    srgb(og * amt + lg * (1f - amt)),
                    srgb(ob * amt + lb * (1f - amt)),
                    (c ushr 24) and 0xFF,
                )
                i++
            }
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    /**
     * 胶片乳剂锐度：只在有局部对比度的位置锐化，平坦区域保持干净。
     * 这是 Portra 这类胶片的观感来源——细节清晰但不「脆」。
     */
    fun applyFilmAcutance(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.001f) return source.copy(Bitmap.Config.ARGB_8888, false)
        val w = source.width
        val h = source.height
        if (w < 3 || h < 3) return source.copy(Bitmap.Config.ARGB_8888, false)
        val src = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        val out = IntArray(w * h)
        val k = amount.coerceIn(0f, 1f) * 0.85f

        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src[y * w + x]
                val r = ((c ushr 16) and 0xFF).toFloat()
                val g = ((c ushr 8) and 0xFF).toFloat()
                val b = (c and 0xFF).toFloat()

                val xl = if (x > 0) src[y * w + x - 1] else c
                val xr = if (x < w - 1) src[y * w + x + 1] else c
                val yu = if (y > 0) src[(y - 1) * w + x] else c
                val yd = if (y < h - 1) src[(y + 1) * w + x] else c

                val rl = ((xl ushr 16) and 0xFF).toFloat()
                val gl = ((xl ushr 8) and 0xFF).toFloat()
                val bl = (xl and 0xFF).toFloat()
                val rr = ((xr ushr 16) and 0xFF).toFloat()
                val gr = ((xr ushr 8) and 0xFF).toFloat()
                val br = (xr and 0xFF).toFloat()
                val ru = ((yu ushr 16) and 0xFF).toFloat()
                val gu = ((yu ushr 8) and 0xFF).toFloat()
                val bu = (yu and 0xFF).toFloat()
                val rd = ((yd ushr 16) and 0xFF).toFloat()
                val gd = ((yd ushr 8) and 0xFF).toFloat()
                val bd = (yd and 0xFF).toFloat()

                // 局部对比度（4 邻域梯度）
                val local0 = max(abs(r - rl), max(abs(g - gl), abs(b - bl)))
                val local1 = max(abs(r - rr), max(abs(g - gr), abs(b - br)))
                val local2 = max(abs(r - ru), max(abs(g - gu), abs(b - bu)))
                val local3 = max(abs(r - rd), max(abs(g - gd), abs(b - bd)))
                val edge = max(max(local0, local1), max(local2, local3)) / 255f

                // 乳剂效应：细节处锐化，平坦处几乎不动（门限 0.06）
                val gate = smoothstep(0.06f, 0.32f, edge)
                val gain = k * (0.35f + gate * 0.65f)

                val nr = (r + (r - (rl + rr + ru + rd) / 4f) * gain).coerceIn(0f, 255f)
                val ng = (g + (g - (gl + gr + gu + gd) / 4f) * gain).coerceIn(0f, 255f)
                val nb = (b + (b - (bl + br + bu + bd) / 4f) * gain).coerceIn(0f, 255f)

                out[y * w + x] = pack(nr / 255f, ng / 255f, nb / 255f, (c ushr 24) and 0xFF)
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    /**
     * 理光 GR 风格的对比度曲线：中间调保持，肩部与趾部平滑压缩。
     * @param amount -100..100
     */
    fun applyGrContrast(source: Bitmap, amount: Int): Bitmap {
        if (amount == 0) return source.copy(Bitmap.Config.ARGB_8888, false)
        val lut = grContrastLut(amount)
        val w = source.width
        val h = source.height
        val src = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        val out = IntArray(w * h)
        for (i in out.indices) {
            val c = src[i]
            val r = lut[(c ushr 16) and 0xFF]
            val g = lut[(c ushr 8) and 0xFF]
            val b = lut[c and 0xFF]
            out[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    /** 给 UI 预览曲线用。 */
    fun grContrastLut(amount: Int): IntArray {
        val k = amount.coerceIn(-100, 100) / 100f
        // 以 0.18 中灰为支点的 S 形，两端用有理函数压肩 / 抬趾
        val out = IntArray(256)
        for (i in 0..255) {
            val x = i / 255f
            val s = if (k >= 0f) {
                // 增强：smoothstep 混合
                val shaped = x * x * (3f - 2f * x)
                x + (shaped - x) * k * 0.75f
            } else {
                // 减弱：向中灰压缩
                0.5f + (x - 0.5f) * (1f + k * 0.6f)
            }
            // 肩部：接近 1 时自然收敛，避免死白
            val shoulder = if (s > 0.82f) 0.82f + (s - 0.82f) * 0.78f else s
            val toe = if (shoulder < 0.06f) shoulder * 0.88f else shoulder
            out[i] = (toe.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        return out
    }

    // ─────────────────────────────────────────────────────────────────────
    // 金字塔与权重
    // ─────────────────────────────────────────────────────────────────────

    private fun pyramidLevels(scatterPx: Float): Int {
        // 覆盖到散射半径的 2 倍即可，最多 6 层
        val n = (log2(scatterPx.toDouble()).coerceAtLeast(0.0) + 2.0).toInt()
        return n.coerceIn(2, 6)
    }

    /**
     * 光锥散射角分布 → 各层权重。
     *
     * 分布取「核心 + 指数拖尾」：p(θ) ∝ exp(-θ/θ0)，θ0 为滤镜的特征散射角。
     * 第 i 层像素尺度 d_i = scatterPx / 2^i，落在 [d_i/√2, d_i·√2] 的光被该层承载，
     * 于是权重 = p 在该区间的积分（归一化后）。这就是实体滤镜高光「近处亮、远处淡」的原因。
     */
    private fun scatterWeights(levels: Int, scatterPx: Float): FloatArray {
        val theta0 = scatterPx
        val raw = FloatArray(levels)
        for (i in 0 until levels) {
            val d = scatterPx / (1 shl i)
            val lo = d / sqrt(2f)
            val hi = d * sqrt(2f)
            // ∫ exp(-θ/θ0) dθ / θ0
            raw[i] = (exp(-lo / theta0) - exp(-hi / theta0)).coerceAtLeast(0f)
        }
        // 最后一层吸收剩余能量，保证 halo 亮度不随层数变化
        val sum = raw.sum()
        return if (sum <= 1e-6f) {
            FloatArray(levels) { if (it == 0) 1f else 0f }
        } else {
            FloatArray(levels) { raw[it] / sum }
        }
    }

    private fun buildPyramid(lin: FloatArray, w: Int, h: Int, levels: Int): List<Pyramid> {
        val list = ArrayList<Pyramid>(levels)
        var cw = w
        var ch = h
        var cur = lin
        list.add(Pyramid(cw, ch, cur))
        for (l in 1 until levels) {
            val nw = max(1, cw / 2)
            val nh = max(1, ch / 2)
            cur = downsample(cur, cw, ch, nw, nh)
            cw = nw
            ch = nh
            list.add(Pyramid(cw, ch, cur))
        }
        return list
    }

    /** 逐层放大并累加：U_i = w_i · L_i + tentUp(U_{i+1})。 */
    private fun reconstructHalo(pyramid: List<Pyramid>, weights: FloatArray): FloatArray {
        val base = pyramid.first()
        var acc: FloatArray? = null
        var accW = base.width
        var accH = base.height
        for (i in pyramid.indices.reversed()) {
            val lvl = pyramid[i]
            if (acc == null) {
                acc = FloatArray(lvl.rgb.size) { lvl.rgb[it] * weights[i] }
                accW = lvl.width
                accH = lvl.height
            } else {
                val up = upsample(acc, accW, accH, lvl.width, lvl.height)
                for (k in up.indices) {
                    up[k] = up[k] + lvl.rgb[k] * weights[i]
                }
                acc = up
                accW = lvl.width
                accH = lvl.height
            }
        }
        val result = acc ?: FloatArray(base.rgb.size)
        // 散射总能量按权重和归一（weights 已归一，这里只做通道数对齐）
        return result
    }

    private fun downsample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val dst = FloatArray(dw * dh * 3)
        for (y in 0 until dh) {
            val sy0 = (y * 2).coerceAtMost(sh - 1)
            val sy1 = (y * 2 + 1).coerceAtMost(sh - 1)
            for (x in 0 until dw) {
                val sx0 = (x * 2).coerceAtMost(sw - 1)
                val sx1 = (x * 2 + 1).coerceAtMost(sw - 1)
                val d3 = (y * dw + x) * 3
                for (c in 0..2) {
                    val a = src[(sy0 * sw + sx0) * 3 + c]
                    val b = src[(sy0 * sw + sx1) * 3 + c]
                    val cc = src[(sy1 * sw + sx0) * 3 + c]
                    val d = src[(sy1 * sw + sx1) * 3 + c]
                    dst[d3 + c] = (a + b + cc + d) * 0.25f
                }
            }
        }
        return dst
    }

    /** 双线性上采样（对应 tent up）。 */
    private fun upsample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val dst = FloatArray(dw * dh * 3)
        val xr = if (dw > 1) (sw - 1).toFloat() / (dw - 1) else 0f
        val yr = if (dh > 1) (sh - 1).toFloat() / (dh - 1) else 0f
        for (y in 0 until dh) {
            val fy = y * yr
            val y0 = fy.toInt().coerceIn(0, sh - 1)
            val y1 = (y0 + 1).coerceAtMost(sh - 1)
            val ty = fy - y0
            for (x in 0 until dw) {
                val fx = x * xr
                val x0 = fx.toInt().coerceIn(0, sw - 1)
                val x1 = (x0 + 1).coerceAtMost(sw - 1)
                val tx = fx - x0
                val d3 = (y * dw + x) * 3
                for (c in 0..2) {
                    val a = src[(y0 * sw + x0) * 3 + c]
                    val b = src[(y0 * sw + x1) * 3 + c]
                    val cc = src[(y1 * sw + x0) * 3 + c]
                    val d = src[(y1 * sw + x1) * 3 + c]
                    val top = a + (b - a) * tx
                    val bot = cc + (d - cc) * tx
                    dst[d3 + c] = top + (bot - top) * ty
                }
            }
        }
        return dst
    }

    // ─────────────────────────────────────────────────────────────────────
    // 色彩工具（sRGB ⇄ 线性光）
    // ─────────────────────────────────────────────────────────────────────

    private fun linearize(src: IntArray, n: Int): FloatArray {
        val out = FloatArray(n * 3)
        for (i in 0 until n) {
            val c = src[i]
            out[i * 3] = srgbToLinear(((c ushr 16) and 0xFF) / 255f)
            out[i * 3 + 1] = srgbToLinear(((c ushr 8) and 0xFF) / 255f)
            out[i * 3 + 2] = srgbToLinear((c and 0xFF) / 255f)
        }
        return out
    }

    private fun srgbToLinear(v: Float): Float =
        if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

    private fun srgb(v: Float): Float {
        val x = v.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
    }

    private fun pack(r: Float, g: Float, b: Float, a: Int): Int {
        val ri = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (a shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 供 UI 展示：把柔光滤镜的散射强度换算成 0..100 的直观读数。 */
    fun scatterPercent(spec: SoftFilterSpec): Int = (spec.scatterRatio * 900f).roundToInt().coerceIn(1, 100)
}
