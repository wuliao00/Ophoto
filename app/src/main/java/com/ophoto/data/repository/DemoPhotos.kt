package com.ophoto.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.ophoto.engine.filter.FilterCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * 示例样片库（小红书流行美学 · 丰富画报级样本 · 离线全量内置）
 *
 * 彻底消除「示例图太少、卡片复用线框图」的问题：
 * 1. 为每个题材与核心预设提供独立的高清真实摄影底片（1600×1200，全无水印无文字）；
 * 2. 覆盖发丝金边人像、柔雾窗光、雨夜霓虹、复古咖啡馆、海风青蓝、森系微光、
 *    银盐光影街拍、落日琥珀旷野、极简螺旋旋梯、法式甜点静物等流行调色流派；
 * 3. 现场助手 8 张灵感卡 + 5 大布光全数精准对齐独立底片。
 */
enum class DemoPhoto(
    val assetName: String,
    val label: String,
    val sceneHint: String,
    val scene: SceneCategory,
) {
    // ── 人像流派 ──────────────────────────────────────────────
    PORTRAIT_SUNLIGHT("demo/portrait_sunlight.jpg", "发丝金边", "落日逆光 · 暖阳发丝 · 肤色通透", SceneCategory.PORTRAIT),
    PORTRAIT_SOFTGLOW("demo/portrait_softglow.jpg", "柔雾窗光", "窗边薄纱 · 奶油漫射 · 清透空气感", SceneCategory.PORTRAIT),
    STUDIO_REMBRANDT("demo/studio.jpg", "棚拍光影", "经典伦勃朗 · 45° 主光 · 雕塑立体感", SceneCategory.PORTRAIT),
    PORTRAIT_WINDOW("demo/portrait.jpg", "日常窗光", "单侧自然光 · 眼神光清澈", SceneCategory.PORTRAIT),

    // ── 街头与夜景 ──────────────────────────────────────────
    NIGHT_NEON("demo/night_neon_street.jpg", "雨夜霓虹", "湿润路面 · 倒影光斑 · 电影冷暖对比", SceneCategory.NIGHT),
    STREET_RAIN("demo/street.jpg", "雨夜漫步", "暗部扎实 · 街头氛围", SceneCategory.NIGHT),
    DAYSTREET_BACKLIGHT("demo/daystreet.jpg", "街头逆光", "高动态强对比 · 人文剪影", SceneCategory.STREET),
    MONO_STREET_SLICE("demo/mono_street_slice.jpg", "银盐光影", "建筑斜切硬阴影 · 纯粹黑白影调", SceneCategory.ARCHITECTURE),

    // ── 胶片与日常风尚 ────────────────────────────────────────
    FILM_RETRO_CAFE("demo/film_retro_cafe.jpg", "复古午后", "木质角隅 · 暖棕调胶片呼吸感", SceneCategory.GOURMET),
    TRAVEL_COASTAL_CYAN("demo/travel_coastal_cyan.jpg", "海风青蓝", "低饱和青蓝海面 · 纯白墙面 · 极简留白", SceneCategory.TRAVEL),
    TRAVEL_ALLEY("demo/travel.jpg", "海岛小巷", "饱和色彩 · 异域阳光", SceneCategory.TRAVEL),

    // ── 自然与风光 ──────────────────────────────────────────
    FOREST_MOSS_MIST("demo/forest_moss_mist.jpg", "森系微光", "晨雾青苔 · 幽深绿阶 · 宁静呼吸", SceneCategory.LANDSCAPE),
    LANDSCAPE_AMBER("demo/landscape_amber_sunset.jpg", "落日琥珀", "金色草坡 · 旷野暖晖 · 电影级大景", SceneCategory.LANDSCAPE),
    LANDSCAPE_LAKE("demo/landscape.jpg", "晨雾湖泊", "柔和漫射 · 静谧倒影", SceneCategory.LANDSCAPE),

    // ── 美食与器物 ──────────────────────────────────────────
    GOURMET_ARTISAN("demo/gourmet_artisan_bistro.jpg", "餐桌暖调", "侧逆光焦糖甜点 · 细腻油润高光", SceneCategory.GOURMET),
    GOURMET_TABLE("demo/gourmet.jpg", "桌面静物", "自然散射 · 材质肌理", SceneCategory.GOURMET),

    // ── 建筑与几何 ──────────────────────────────────────────
    ARCHITECTURE_SPIRAL("demo/architecture_spiral.jpg", "斐波那契旋梯", "弧线几何 · 纯净灰调 · 秩序构图", SceneCategory.ARCHITECTURE),
    ARCHITECTURE_SLATE("demo/architecture.jpg", "硬光立面", "直线与斜影 · 极简质感", SceneCategory.ARCHITECTURE),

    // ── 动态与萌宠 ──────────────────────────────────────────
    ACTION_RUN("demo/action.jpg", "奔跑瞬间", "高速快门 · 毛发细节 · 眼神光捕捉", SceneCategory.ACTION_PET);

    companion object {
        /** 场景 → 最贴近的示范底片（小红书精选版）。 */
        fun forScene(scene: SceneCategory): DemoPhoto = when (scene) {
            SceneCategory.PORTRAIT -> PORTRAIT_SUNLIGHT
            SceneCategory.STREET -> DAYSTREET_BACKLIGHT
            SceneCategory.NIGHT -> NIGHT_NEON
            SceneCategory.LANDSCAPE -> LANDSCAPE_AMBER
            SceneCategory.GOURMET -> GOURMET_ARTISAN
            SceneCategory.TRAVEL -> TRAVEL_COASTAL_CYAN
            SceneCategory.ARCHITECTURE -> ARCHITECTURE_SPIRAL
            SceneCategory.ACTION_PET -> ACTION_RUN
            SceneCategory.ALL -> PORTRAIT_SUNLIGHT
        }

        /** 滤镜分类 → 示例底片（每类风格都展示在它最有说服力的场景上）。 */
        fun forFilterCategory(category: FilterCategory): DemoPhoto = when (category) {
            FilterCategory.RECOMMENDED -> PORTRAIT_SUNLIGHT
            FilterCategory.FILM -> FILM_RETRO_CAFE
            FilterCategory.MONOCHROME -> MONO_STREET_SLICE
            FilterCategory.PORTRAIT -> PORTRAIT_SOFTGLOW
            FilterCategory.NIGHT -> NIGHT_NEON
            FilterCategory.STREET_SCENERY -> TRAVEL_COASTAL_CYAN
            FilterCategory.IMPORTED -> GOURMET_ARTISAN
        }

        /** 具体预设 ID → 专属定制底片（让每个热门预设都拥有专属视觉！）。 */
        fun forPresetId(presetId: String): DemoPhoto = when (presetId) {
            "film_green_001" -> FILM_RETRO_CAFE
            "city_neon_night" -> NIGHT_NEON
            "amber_golden_hour" -> LANDSCAPE_AMBER
            "silver_documentary" -> MONO_STREET_SLICE
            "velvet_skin_portrait" -> PORTRAIT_SOFTGLOW
            "coastal_breeze_cyan" -> TRAVEL_COASTAL_CYAN
            "terracotta_humanist" -> PORTRAIT_SUNLIGHT
            "moss_rainforest" -> FOREST_MOSS_MIST
            "nostalgia_lightleak" -> FILM_RETRO_CAFE
            "architectural_slate" -> ARCHITECTURE_SPIRAL
            "bistro_gourmet" -> GOURMET_ARTISAN
            "mist_soft_mono" -> MONO_STREET_SLICE
            "black_mist_portrait" -> PORTRAIT_SUNLIGHT
            "white_mist_dream" -> PORTRAIT_SOFTGLOW
            "emulsion_scatter_film" -> FILM_RETRO_CAFE
            "neon_halation_night" -> NIGHT_NEON
            else -> PORTRAIT_SUNLIGHT
        }
    }
}

object DemoPhotos {

    private val cache = ConcurrentHashMap<String, Bitmap>()

    /** 暗房空状态每次进来换一张，画报轮播。 */
    private val darkroomRotation = listOf(
        DemoPhoto.PORTRAIT_SUNLIGHT,
        DemoPhoto.FILM_RETRO_CAFE,
        DemoPhoto.NIGHT_NEON,
        DemoPhoto.TRAVEL_COASTAL_CYAN,
        DemoPhoto.FOREST_MOSS_MIST,
        DemoPhoto.MONO_STREET_SLICE,
        DemoPhoto.GOURMET_ARTISAN,
        DemoPhoto.ARCHITECTURE_SPIRAL,
        DemoPhoto.LANDSCAPE_AMBER,
        DemoPhoto.PORTRAIT_SOFTGLOW,
    )
    private var darkroomIndex = 0

    @Synchronized
    fun nextDarkroomPhoto(): DemoPhoto {
        val photo = darkroomRotation[darkroomIndex % darkroomRotation.size]
        darkroomIndex += 1
        return photo
    }

    /** 取整幅示例底片（暗房画布用）。 */
    fun load(context: Context, photo: DemoPhoto = DemoPhoto.PORTRAIT_SUNLIGHT): Bitmap =
        cache[photo.assetName] ?: decode(context, photo) ?: fallbackGradient().also {
            cache[photo.assetName] = it
        }

    /** 按最大边长采样解码（卡片 / 缩略图 / 示例面板用）。 */
    fun loadScaled(context: Context, photo: DemoPhoto = DemoPhoto.PORTRAIT_SUNLIGHT, maxEdge: Int = 720): Bitmap {
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
        loadScaled(context, DemoPhoto.PORTRAIT_SUNLIGHT, targetWidth)

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
        val b = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 800f, 600f,
                intArrayOf(0xFF1B202B.toInt(), 0xFF0B0E14.toInt(), 0xFF141923.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, 800f, 600f, paint)
        return b
    }
}
