package com.ophoto.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.ophoto.data.local.CompositionGuideType
import com.ophoto.engine.filter.FilterCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * 示例样片（Demo Photography）
 *
 * 早先界面用的是程序化生成的「几何色块测试图」，观感极其简陋；
 * 上一版补到 3 张真实底片，但 8 个场景分类里有一半在复用同一张图，仍然「不够看」。
 * 现在按**场景一一对应**打包 9 张实拍底片，任何灵感卡、布光指南、滤镜分类
 * 都能找到真正贴合它的一张，不再出现「所有卡片都是同一张照片」。
 *
 * 只在需要时按目标尺寸采样解码并缓存，不参与用户成片的处理管线。
 */
enum class DemoPhoto(
    val assetName: String,
    val label: String,
    val sceneHint: String,
    /** 这张底片最适合演示的题材。 */
    val scene: SceneCategory,
) {
    PORTRAIT("demo/portrait.jpg", "窗光人像", "柔光顺光 · 肤色基准", SceneCategory.PORTRAIT),
    STUDIO("demo/studio.jpg", "棚拍伦勃朗", "单灯侧位 · 立体感基准", SceneCategory.PORTRAIT),
    STREET("demo/street.jpg", "雨夜街拍", "暖灯 vs 冷夜 · 暗部基准", SceneCategory.NIGHT),
    DAYSTREET("demo/daystreet.jpg", "街头逆光", "强对比剪影 · 高光基准", SceneCategory.STREET),
    LANDSCAPE("demo/landscape.jpg", "晨雾湖景", "大动态 · 风光基准", SceneCategory.LANDSCAPE),
    TRAVEL("demo/travel.jpg", "海岛小巷", "高饱和 · 旅行基准", SceneCategory.TRAVEL),
    GOURMET("demo/gourmet.jpg", "桌面静物", "侧窗光 · 质感基准", SceneCategory.GOURMET),
    ARCHITECTURE("demo/architecture.jpg", "硬光几何", "直线与硬边 · 结构基准", SceneCategory.ARCHITECTURE),
    ACTION("demo/action.jpg", "奔跑瞬间", "高速快门 · 动态基准", SceneCategory.ACTION_PET);

    companion object {
        /** 场景 → 底片：一一对应，没有「其他都回退到同一张」的兜底。 */
        fun forScene(scene: SceneCategory): DemoPhoto = when (scene) {
            SceneCategory.PORTRAIT -> PORTRAIT
            SceneCategory.STREET -> DAYSTREET
            SceneCategory.NIGHT -> STREET
            SceneCategory.LANDSCAPE -> LANDSCAPE
            SceneCategory.GOURMET -> GOURMET
            SceneCategory.TRAVEL -> TRAVEL
            SceneCategory.ARCHITECTURE -> ARCHITECTURE
            SceneCategory.ACTION_PET -> ACTION
            SceneCategory.ALL -> LANDSCAPE
        }

        /** 调色库分类 → 底片：每类预设都展示在它真正擅长的题材上。 */
        fun forFilterCategory(category: FilterCategory): DemoPhoto = when (category) {
            FilterCategory.RECOMMENDED -> LANDSCAPE
            FilterCategory.FILM -> DAYSTREET
            FilterCategory.MONOCHROME -> ARCHITECTURE
            FilterCategory.PORTRAIT -> PORTRAIT
            FilterCategory.NIGHT -> STREET
            FilterCategory.STREET_SCENERY -> TRAVEL
            FilterCategory.IMPORTED -> GOURMET
        }
    }
}

object DemoPhotos {

    private val cache = ConcurrentHashMap<String, Bitmap>()

    /** 暗房空状态每次进来换一张，避免「这张图怎么永远一样」。 */
    private val darkroomRotation = listOf(
        DemoPhoto.LANDSCAPE,
        DemoPhoto.PORTRAIT,
        DemoPhoto.STREET,
        DemoPhoto.TRAVEL,
        DemoPhoto.GOURMET,
    )
    private var darkroomIndex = 0

    @Synchronized
    fun nextDarkroomPhoto(): DemoPhoto {
        val photo = darkroomRotation[darkroomIndex % darkroomRotation.size]
        darkroomIndex += 1
        return photo
    }

    /** 取整幅示例底片（暗房画布用）。 */
    fun load(context: Context, photo: DemoPhoto = DemoPhoto.LANDSCAPE): Bitmap =
        cache[photo.assetName] ?: decode(context, photo) ?: fallbackGradient().also {
            cache[photo.assetName] = it
        }

    /** 按最大边长采样解码（卡片 / 缩略图 / 示例面板用）。 */
    fun loadScaled(context: Context, photo: DemoPhoto = DemoPhoto.LANDSCAPE, maxEdge: Int = 720): Bitmap {
        val key = "${photo.assetName}@$maxEdge"
        cache[key]?.let { return it }
        val decoded = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(photo.assetName).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            context.assets.open(photo.assetName).use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            }
        }.getOrNull()
        val result = decoded ?: load(context, photo)
        if (result.width > maxEdge) {
            val ratio = maxEdge.toFloat() / result.width
            val scaled = runCatching {
                Bitmap.createScaledBitmap(result, maxEdge, (result.height * ratio).toInt().coerceAtLeast(1), true)
            }.getOrDefault(result)
            cache[key] = scaled
            return scaled
        }
        cache[key] = result
        return result
    }

    /** 供滤镜卡片使用的统一比例底片（4:3，避免卡片之间比例跳动）。 */
    fun loadForPreview(context: Context, targetWidth: Int = 640): Bitmap =
        loadScaled(context, DemoPhoto.LANDSCAPE, targetWidth)

    private fun decode(context: Context, photo: DemoPhoto): Bitmap? = runCatching {
        context.assets.open(photo.assetName).use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
            )
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

/** 布光/构图示范：底片 + 辅助线参数，成对出现。 */
data class DemoSceneCue(
    val photo: DemoPhoto,
    val guide: CompositionGuideType,
    val subjectX: Float,
    val subjectY: Float,
    val lightDirectionDeg: Double,
)
