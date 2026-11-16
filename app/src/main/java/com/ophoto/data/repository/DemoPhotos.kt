package com.ophoto.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.util.concurrent.ConcurrentHashMap

/**
 * 示例样片（Demo Photography）
 *
 * 之前取景/滤镜/暗房用的是程序化生成的「几何色块测试图」，观感极其简陋。
 * 现在改为随 APK 打包的三张真实摄影底片（人像 / 夜景街拍 / 风光），
 * 用来做滤镜预览、暗房初始画面与空状态占位——它们在任意滤镜下都成立，
 * 也能真实反映出各预设的肤色、暗部与高光倾向。
 *
 * 只在需要时解码一次并缓存（≤1440×1080，~0.6MB 内存），不参与用户成片管线。
 */
enum class DemoPhoto(val assetName: String, val label: String, val sceneHint: String) {
    PORTRAIT("demo/portrait.jpg", "窗光人像", "柔光顺光 · 肤色基准"),
    STREET("demo/street.jpg", "雨夜街拍", "暖灯 vs 冷夜 · 暗部基准"),
    LANDSCAPE("demo/landscape.jpg", "晨雾湖景", "大动态 · 高光基准"),
}

object DemoPhotos {

    private val cache = ConcurrentHashMap<String, Bitmap>()

    /** 按场景取一张示例底片（失败时回退到最小可用渐变，绝不让 UI 崩）。 */
    fun load(context: Context, photo: DemoPhoto = DemoPhoto.LANDSCAPE): Bitmap =
        cache[photo.assetName] ?: decode(context, photo) ?: fallbackGradient().also {
            cache[photo.assetName] = it
        }

    /** 供滤镜卡片使用的小尺寸底片（统一 4:3，避免卡片之间比例跳动）。 */
    fun loadForPreview(context: Context, targetWidth: Int = 640): Bitmap {
        val base = load(context, DemoPhoto.LANDSCAPE)
        if (base.width <= targetWidth) return base
        val ratio = targetWidth.toFloat() / base.width
        val h = (base.height * ratio).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(base, targetWidth, h, true) }.getOrDefault(base)
    }

    private fun decode(context: Context, photo: DemoPhoto): Bitmap? = runCatching {
        context.assets.open(photo.assetName).use { stream ->
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }.getOrNull()

    private fun fallbackGradient(): Bitmap {
        val bmp = Bitmap.createBitmap(720, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, 540f,
            intArrayOf(0xFF2A3340.toInt(), 0xFF141820.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 720f, 540f, paint)
        return bmp
    }
}
