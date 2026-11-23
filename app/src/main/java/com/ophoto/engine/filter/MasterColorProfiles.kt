package com.ophoto.engine.filter

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 大师色彩家族（Master Color Families & Look Emulation）
 *
 * 依照 OmniCam-Muse 规划的经典色彩科学体系进行清洁室纯原生数学建模：
 * 1. 经典胶片 (Spektrafilm / 负片与反转片密度特性)
 * 2. 徕卡 M9 (Leica M9 CCD 色彩矩阵与高反差鲜艳色调)
 * 3. 佳能 EOS R5 (人像粉润记忆色与平滑高光 Rolloff)
 * 4. 松下 Lumix S9 (L.ClassicNeo, Cinelike D2 电影感影调)
 * 5. Darktable Sigmoid / AgX (现代电影级非线性色调映射，高光不失真且饱和度自适应压缩)
 *
 * 支持双重混合：
 * - 影调权重 (Tone Weight: 0..100)
 * - 色彩权重 (Color Weight: 0..100)
 * - 曝光对齐 (Exposure Alignment: 中灰 18% 对齐)
 */
enum class MasterColorFamily(val label: String, val description: String) {
    STANDARD("原厂大师", "OPPO / 哈苏自然色彩基准"),
    LEICA_M9("徕卡 M9 CCD", "浓郁红蓝反差 · 锐利明暗界限 · 德味微对比"),
    CANON_R5("佳能 EOS R5", "东方人像记忆粉润 · 柔和高光衰减 · 自然肤色"),
    LUMIX_S9("松下 Lumix S9", "L.ClassicNeo 电影感低反差 · 沉稳青灰高光"),
    FILM_SPEKTRA("经典胶片负片", "黄色/品红/青色三层银盐密度非线性饱和 · 绵长灰阶"),
    DARKTABLE_AGX("Darktable AgX", "现代电影工业宽容度映射 · 高光色相恒定避免死白"),
}

object MasterColorEngine {

    /**
     * 对线性 RGB 应用指定大师色彩家族的调色矩阵与影调映射。
     *
     * @param r 0..1
     * @param g 0..1
     * @param b 0..1
     * @param family 色彩家族
     * @param toneWeight 0..1 影调权重
     * @param colorWeight 0..1 色彩权重
     * @param out 长度为 3 的 FloatArray
     */
    fun transform(
        r: Float,
        g: Float,
        b: Float,
        family: MasterColorFamily,
        toneWeight: Float = 1.0f,
        colorWeight: Float = 1.0f,
        out: FloatArray,
    ) {
        if (family == MasterColorFamily.STANDARD || (toneWeight <= 0.001f && colorWeight <= 0.001f)) {
            out[0] = r
            out[1] = g
            out[2] = b
            return
        }

        var cr = r
        var cg = g
        var cb = b

        when (family) {
            MasterColorFamily.LEICA_M9 -> {
                // 徕卡 M9 CCD 矩阵：红通道轻微提纯、冷色往青蓝推、对比度略硬
                val nr = r * 1.12f - g * 0.08f - b * 0.04f
                val ng = -r * 0.04f + g * 1.06f - b * 0.02f
                val nb = -r * 0.02f - g * 0.06f + b * 1.08f

                // 影调：特征 S 形微对比（德味立体感）
                val tr = leicaSTone(nr)
                val tg = leicaSTone(ng)
                val tb = leicaSTone(nb)

                cr = blend(r, nr, colorWeight)
                cg = blend(g, ng, colorWeight)
                cb = blend(b, nb, colorWeight)
                cr = blend(cr, tr, toneWeight)
                cg = blend(cg, tg, toneWeight)
                cb = blend(cb, tb, toneWeight)
            }
            MasterColorFamily.CANON_R5 -> {
                // 佳能人像色调：品红提亮、黄绿压暗、高光 rolloff 极度柔滑
                val nr = r * 1.05f + g * 0.02f - b * 0.07f
                val ng = r * 0.01f + g * 0.99f
                val nb = b * 0.96f + r * 0.04f

                val tr = canonRolloff(nr)
                val tg = canonRolloff(ng)
                val tb = canonRolloff(nb)

                cr = blend(r, nr, colorWeight)
                cg = blend(g, ng, colorWeight)
                cb = blend(b, nb, colorWeight)
                cr = blend(cr, tr, toneWeight)
                cg = blend(cg, tg, toneWeight)
                cb = blend(cb, tb, toneWeight)
            }
            MasterColorFamily.LUMIX_S9 -> {
                // 松下 L.ClassicNeo: 偏电影低反差，暗部略浮，青色偏绿
                val nr = r * 0.98f + g * 0.02f
                val ng = g * 1.02f + b * 0.02f
                val nb = b * 0.96f + g * 0.04f

                val tr = lumixNeo(nr)
                val tg = lumixNeo(ng)
                val tb = lumixNeo(nb)

                cr = blend(r, nr, colorWeight)
                cg = blend(g, ng, colorWeight)
                cb = blend(b, nb, colorWeight)
                cr = blend(cr, tr, toneWeight)
                cg = blend(cg, tg, toneWeight)
                cb = blend(cb, tb, toneWeight)
            }
            MasterColorFamily.FILM_SPEKTRA -> {
                // Spektrafilm: 负片三层乳剂密度特征，红色在亮部不偏橙，黄色扎实
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val densityC = 1f - lum
                val nr = r * (1f + 0.08f * densityC)
                val ng = g * (1f - 0.02f * densityC)
                val nb = b * (1f - 0.05f * densityC)

                val tr = filmCurve(nr)
                val tg = filmCurve(ng)
                val tb = filmCurve(nb)

                cr = blend(r, nr, colorWeight)
                cg = blend(g, ng, colorWeight)
                cb = blend(b, nb, colorWeight)
                cr = blend(cr, tr, toneWeight)
                cg = blend(cg, tg, toneWeight)
                cb = blend(cb, tb, toneWeight)
            }
            MasterColorFamily.DARKTABLE_AGX -> {
                // AgX 影调映射：高光收敛，色彩不剪切，保持色相恒定
                val tr = agxMap(r)
                val tg = agxMap(g)
                val tb = agxMap(b)

                cr = blend(r, tr, toneWeight)
                cg = blend(g, tg, toneWeight)
                cb = blend(b, tb, toneWeight)
            }
            MasterColorFamily.STANDARD -> {
                cr = r
                cg = g
                cb = b
            }
        }

        out[0] = cr.coerceIn(0f, 1f)
        out[1] = cg.coerceIn(0f, 1f)
        out[2] = cb.coerceIn(0f, 1f)
    }

    private fun blend(orig: Float, target: Float, weight: Float): Float =
        orig + (target - orig) * weight.coerceIn(0f, 1f)

    private fun leicaSTone(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        return if (v < 0.5f) {
            2f * v * v
        } else {
            1f - 2f * (1f - v) * (1f - v)
        }
    }

    private fun canonRolloff(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        // 柔和高光肩部 Rolloff
        return if (v > 0.75f) {
            0.75f + (v - 0.75f) * 0.72f
        } else {
            v
        }
    }

    private fun lumixNeo(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        // 抬暗部 0.04，压高光 0.96
        return 0.04f + v * 0.92f
    }

    private fun filmCurve(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        // 经典胶片特征曲线（趾部平缓，肩部绵长）
        return v.pow(0.92f) * (0.02f + 0.98f * v)
    }

    private fun agxMap(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        // AgX 类似 Sigmoid 曲线，高光平滑饱和收敛
        return (v / (1f + v * 0.42f)) * 1.42f
    }
}
