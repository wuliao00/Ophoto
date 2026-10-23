package com.ophoto.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.ophoto.engine.filter.CubeLut3D
import com.ophoto.engine.filter.CubeLutParser
import com.ophoto.engine.filter.FilterCategory
import com.ophoto.engine.filter.FilterEngineImpl
import com.ophoto.engine.filter.FilterParams
import com.ophoto.engine.filter.FilterPreset
import com.ophoto.engine.filter.FilterType
import com.ophoto.engine.filter.MasterCameraReference

/**
 * 12 100%-original color grading & Master Camera parameter presets (Doc §3.3, §4, §14, §21:
 * strictly original naming and recipes — zero third-party film brand trademarks).
 */
object BuiltInPresets {

    val ALL: List<FilterPreset> = listOf(
        FilterPreset(
            id = "film_green_001",
            name = "青绿胶片",
            subtitle = "低对比 · 青绿暗部 · 褪色呼吸感",
            category = FilterCategory.FILM,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "低对比、偏青绿色调的日系街景胶片风格，高光温和奶油白，暗部带深青绿层次。",
            accentHex = "#34D399",
            params = FilterParams(
                exposureEv = 0.05f,
                contrast = -10,
                saturation = -12,
                vibrance = 6,
                temperature = -6,
                tint = 8,
                highlights = -14,
                shadows = 16,
                highlightTintHex = "#D8F0E6",
                shadowTintHex = "#12332B",
                colorCastStrength = 48,
                grainStrength = 18,
                vignette = 20,
                fade = 12,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "复古 85%",
                softLightLabel = "轻柔",
                tone = -18,
                saturation = -12,
                warmth = -6,
                cyanMagenta = -10,
                sharpness = -6,
                vignetteEnabled = true,
                recommendedIso = "ISO 100–400",
                recommendedShutter = "1/250s",
                recommendedEv = "+0.3 EV",
                recommendedKelvin = 5100,
                sceneAdvice = "适合阴天街巷、绿植窗景、海边巴士与树影斑驳午后",
            ),
            defaultStrength = 0.88f,
            isFavorite = true,
            isBuiltIn = true,
            sortOrder = 1,
        ),
        FilterPreset(
            id = "city_neon_night",
            name = "城市夜景",
            subtitle = "冷蓝高光 · 深靛暗部 · 霓虹通透",
            category = FilterCategory.NIGHT,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "压制杂乱钠灯黄浊色偏，赋予高光清透冰蓝与暗部深邃午夜靛蓝。",
            accentHex = "#60A5FA",
            params = FilterParams(
                exposureEv = -0.15f,
                contrast = 14,
                saturation = -6,
                vibrance = 18,
                temperature = -14,
                tint = 6,
                highlights = -22,
                shadows = 10,
                highlightTintHex = "#86C5FF",
                shadowTintHex = "#0E1E3A",
                colorCastStrength = 52,
                grainStrength = 10,
                vignette = 22,
                fade = 4,
                sharpness = 14,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "冷调 90%",
                softLightLabel = "无",
                tone = -24,
                saturation = 8,
                warmth = -16,
                cyanMagenta = 6,
                sharpness = 12,
                vignetteEnabled = true,
                recommendedIso = "ISO 100–800",
                recommendedShutter = "1/50s",
                recommendedEv = "-0.7 EV",
                recommendedKelvin = 4300,
                sceneAdvice = "雨夜反光路面、天桥车流、便利店灯箱，务必压低曝光保住霓虹灯牌字迹",
            ),
            defaultStrength = 0.85f,
            isFavorite = true,
            isBuiltIn = true,
            sortOrder = 2,
        ),
        FilterPreset(
            id = "amber_golden_hour",
            name = "琥珀暖阳",
            subtitle = "逆光金边 · 柔光滚降 · 暖调叙事",
            category = FilterCategory.RECOMMENDED,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "专为落日前一小时设计，柔化逆光高光边缘，渲染温暖琥珀金与自然红润肤色。",
            accentHex = "#F59E0B",
            params = FilterParams(
                exposureEv = 0.10f,
                contrast = -4,
                saturation = 8,
                vibrance = 12,
                temperature = 18,
                tint = 4,
                highlights = -18,
                shadows = 18,
                highlightTintHex = "#FFE3B3",
                shadowTintHex = "#2E1C14",
                colorCastStrength = 44,
                grainStrength = 14,
                vignette = 16,
                fade = 8,
                softGlow = 26,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "暖阳 80%",
                softLightLabel = "朦胧",
                tone = -10,
                saturation = 10,
                warmth = 18,
                cyanMagenta = 4,
                sharpness = -8,
                vignetteEnabled = false,
                recommendedIso = "ISO 50–200",
                recommendedShutter = "1/500s",
                recommendedEv = "+0.3 EV",
                recommendedKelvin = 6200,
                sceneAdvice = "日落前 45 分钟侧逆光拍摄发丝金边，对人物脸部点测光",
            ),
            defaultStrength = 0.85f,
            isFavorite = true,
            isBuiltIn = true,
            sortOrder = 3,
        ),
        FilterPreset(
            id = "silver_documentary",
            name = "银盐纪实",
            subtitle = "高反差灰阶 · 真实颗粒 · 人文街头",
            category = FilterCategory.MONOCHROME,
            type = FilterType.BASIC_PARAMS,
            author = "Ophoto Lab",
            description = "抽离色彩干扰，强化明暗几何切割与中灰质感，赋予街头抓拍厚重银盐张力。",
            accentHex = "#E5E7EB",
            params = FilterParams(
                exposureEv = -0.10f,
                contrast = 26,
                highlights = -12,
                shadows = -8,
                whites = 10,
                blacks = -6,
                grainStrength = 28,
                vignette = 26,
                fade = 5,
                sharpness = 18,
                monochrome = true,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "黑白 100%",
                softLightLabel = "无",
                tone = -32,
                saturation = -50,
                warmth = 0,
                cyanMagenta = 0,
                sharpness = 18,
                vignetteEnabled = true,
                recommendedIso = "ISO 100–800",
                recommendedShutter = "1/500s",
                recommendedEv = "-0.7 EV",
                recommendedKelvin = 5500,
                sceneAdvice = "正午硬光投射狭长阴影、建筑走廊明暗交界处按高光测光",
            ),
            defaultStrength = 1.0f,
            isFavorite = false,
            isBuiltIn = true,
            sortOrder = 4,
        ),
        FilterPreset(
            id = "velvet_skin_portrait",
            name = "柔雾清透人像",
            subtitle = "肤色提亮 · 低锐度 · 通透空气感",
            category = FilterCategory.PORTRAIT,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "适度压低环境杂色饱和度，抬升阴影明度并柔化皮肤微瑕，保留清澈眼神光。",
            accentHex = "#F472B6",
            params = FilterParams(
                exposureEv = 0.18f,
                contrast = -12,
                saturation = -8,
                vibrance = 10,
                temperature = 4,
                tint = 7,
                highlights = -16,
                shadows = 24,
                highlightTintHex = "#FFF2F0",
                shadowTintHex = "#242834",
                colorCastStrength = 30,
                grainStrength = 8,
                vignette = 10,
                fade = 8,
                softGlow = 32,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "清透 75%",
                softLightLabel = "柔光",
                tone = 12,
                saturation = -6,
                warmth = 4,
                cyanMagenta = 8,
                sharpness = -14,
                vignetteEnabled = false,
                recommendedIso = "ISO 50–320",
                recommendedShutter = "1/250s",
                recommendedEv = "+0.3 ~ +0.7 EV",
                recommendedKelvin = 5300,
                sceneAdvice = "使用 2x(46mm) 或 3x(70mm) 镜头站在窗边 45° 侧顺光位置",
            ),
            defaultStrength = 0.85f,
            isBuiltIn = true,
            sortOrder = 5,
        ),
        FilterPreset(
            id = "coastal_breeze_cyan",
            name = "海风青蓝",
            subtitle = "高明度 · 清新晴空 · 夏日海岸",
            category = FilterCategory.STREET_SCENERY,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "提亮整体明度，令天空与海水呈现纯净青钴色，白色衣物与浪花干净透亮。",
            accentHex = "#38BDF8",
            params = FilterParams(
                exposureEv = 0.16f,
                contrast = -6,
                saturation = 6,
                vibrance = 16,
                temperature = -10,
                tint = -4,
                highlights = -12,
                shadows = 20,
                whites = 8,
                highlightTintHex = "#E6F8FF",
                shadowTintHex = "#153243",
                colorCastStrength = 38,
                grainStrength = 9,
                vignette = 8,
                fade = 6,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "清新 85%",
                softLightLabel = "轻柔",
                tone = 14,
                saturation = 10,
                warmth = -12,
                cyanMagenta = -6,
                sharpness = 4,
                vignetteEnabled = false,
                recommendedIso = "ISO 50–100",
                recommendedShutter = "1/1000s",
                recommendedEv = "+0.5 EV",
                recommendedKelvin = 4900,
                sceneAdvice = "晴天海堤、白色灯塔、晴空草坡，适当留白三分之二天空",
            ),
            defaultStrength = 0.85f,
            isBuiltIn = true,
            sortOrder = 6,
        ),
        FilterPreset(
            id = "terracotta_humanist",
            name = "赤陶人文",
            subtitle = "沉稳低影调 · 浓郁色阶 · 经典纪实",
            category = FilterCategory.RECOMMENDED,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "收敛浮躁亮色，强化砖红、木色与深蓝织物的油润层次，画面沉稳耐看。",
            accentHex = "#FB7185",
            params = FilterParams(
                exposureEv = -0.12f,
                contrast = 18,
                saturation = 4,
                vibrance = -6,
                temperature = 6,
                tint = 2,
                highlights = -16,
                shadows = -10,
                highlightTintHex = "#F5E6D3",
                shadowTintHex = "#1A1816",
                colorCastStrength = 36,
                grainStrength = 16,
                vignette = 28,
                fade = 4,
                sharpness = 12,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "大师浓郁 90%",
                softLightLabel = "无",
                tone = -30,
                saturation = 6,
                warmth = 6,
                cyanMagenta = 2,
                sharpness = 10,
                vignetteEnabled = true,
                recommendedIso = "ISO 100–400",
                recommendedShutter = "1/320s",
                recommendedEv = "-0.5 EV",
                recommendedKelvin = 5600,
                sceneAdvice = "老城区骑楼、咖啡馆深木色吧台、黄昏市集抓拍",
            ),
            defaultStrength = 0.86f,
            isBuiltIn = true,
            sortOrder = 7,
        ),
        FilterPreset(
            id = "moss_rainforest",
            name = "森系微光",
            subtitle = "静谧翠绿 · 柔和暗调 · 雨后山林",
            category = FilterCategory.STREET_SCENERY,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "消除刺眼嫩黄绿，将树叶修饰为沉静墨绿与苔藓色，凸显林间雾气与水珠。",
            accentHex = "#10B981",
            params = FilterParams(
                exposureEv = -0.08f,
                contrast = 8,
                saturation = -10,
                vibrance = 8,
                temperature = -8,
                tint = -8,
                highlights = -20,
                shadows = 12,
                highlightTintHex = "#DCEEE4",
                shadowTintHex = "#0D211A",
                colorCastStrength = 45,
                grainStrength = 12,
                vignette = 24,
                fade = 9,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "森系 85%",
                softLightLabel = "无",
                tone = -20,
                saturation = -8,
                warmth = -8,
                cyanMagenta = -8,
                sharpness = 8,
                vignetteEnabled = true,
                recommendedIso = "ISO 100–400",
                recommendedShutter = "1/160s",
                recommendedEv = "-0.3 EV",
                recommendedKelvin = 5000,
                sceneAdvice = "雨后公园、山间溪谷、植物园玻璃温室",
            ),
            defaultStrength = 0.85f,
            isBuiltIn = true,
            sortOrder = 8,
        ),
        FilterPreset(
            id = "nostalgia_lightleak",
            name = "旧日暖笺",
            subtitle = "胶卷漏光 · 柔和褪色 · 记忆颗粒",
            category = FilterCategory.FILM,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "抬升暗部黑位并注入细腻边角暖橙漏光，营造翻开旧相册般的温柔年代感。",
            accentHex = "#FB923C",
            params = FilterParams(
                exposureEv = 0.06f,
                contrast = -8,
                saturation = -6,
                temperature = 16,
                tint = 6,
                highlights = -10,
                shadows = 16,
                highlightTintHex = "#FFE6C7",
                shadowTintHex = "#2D2024",
                colorCastStrength = 40,
                grainStrength = 24,
                vignette = 18,
                fade = 18,
                lightLeak = 28,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "怀旧 90%",
                softLightLabel = "朦胧",
                tone = -8,
                saturation = -4,
                warmth = 16,
                cyanMagenta = 6,
                sharpness = -10,
                vignetteEnabled = true,
                recommendedIso = "ISO 200–800",
                recommendedShutter = "1/125s",
                recommendedEv = "0.0 EV",
                recommendedKelvin = 6000,
                sceneAdvice = "朋友聚会抓拍、老书店、绿皮火车窗边随拍",
            ),
            defaultStrength = 0.85f,
            isBuiltIn = true,
            sortOrder = 9,
        ),
        FilterPreset(
            id = "architectural_slate",
            name = "极简几何",
            subtitle = "冷灰中性 · 利落边缘 · 空间秩序",
            category = FilterCategory.STREET_SCENERY,
            type = FilterType.BASIC_PARAMS,
            author = "Ophoto Lab",
            description = "纯净低饱和冷灰基调配合精细微反差锐化，凸显现代建筑幕墙与几何线条。",
            accentHex = "#94A3B8",
            params = FilterParams(
                exposureEv = 0.0f,
                contrast = 16,
                saturation = -26,
                vibrance = -10,
                temperature = -8,
                tint = 0,
                highlights = -14,
                shadows = 6,
                whites = 12,
                sharpness = 24,
                vignette = 12,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "冷灰 80%",
                softLightLabel = "无",
                tone = -14,
                saturation = -24,
                warmth = -8,
                cyanMagenta = 0,
                sharpness = 22,
                vignetteEnabled = false,
                recommendedIso = "ISO 50–100",
                recommendedShutter = "1/500s",
                recommendedEv = "0.0 EV",
                recommendedKelvin = 5000,
                sceneAdvice = "开启电子水平仪与对角线构图，仰拍美术馆穹顶或旋转楼梯",
            ),
            defaultStrength = 0.90f,
            isBuiltIn = true,
            sortOrder = 10,
        ),
        FilterPreset(
            id = "bistro_gourmet",
            name = "食光暖味",
            subtitle = "诱人暖橙 · 清亮高光 · 餐桌质感",
            category = FilterCategory.RECOMMENDED,
            type = FilterType.LUT_PLUS_PARAMS,
            author = "Ophoto Lab",
            description = "针对咖啡馆与餐厅混杂灯光校准，校正桌面发灰发绿，令烘焙与料理色泽饱满。",
            accentHex = "#FBBF24",
            params = FilterParams(
                exposureEv = 0.12f,
                contrast = 10,
                saturation = 12,
                vibrance = 18,
                temperature = 12,
                tint = 5,
                highlights = -8,
                shadows = 14,
                highlightTintHex = "#FFF5E4",
                shadowTintHex = "#241911",
                colorCastStrength = 28,
                sharpness = 16,
                vignette = 14,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "美食 85%",
                softLightLabel = "无",
                tone = 6,
                saturation = 16,
                warmth = 12,
                cyanMagenta = 4,
                sharpness = 14,
                vignetteEnabled = false,
                recommendedIso = "ISO 100–640",
                recommendedShutter = "1/100s",
                recommendedEv = "+0.3 EV",
                recommendedKelvin = 5400,
                sceneAdvice = "坐在靠窗座位利用侧逆光拍出食物高光油润感，切勿使用机顶直闪",
            ),
            defaultStrength = 0.85f,
            isBuiltIn = true,
            sortOrder = 11,
        ),
        FilterPreset(
            id = "mist_soft_mono",
            name = "雾银低反差",
            subtitle = "宽容灰阶 · 柔和黑位 · 静谧肖像",
            category = FilterCategory.MONOCHROME,
            type = FilterType.BASIC_PARAMS,
            author = "Ophoto Lab",
            description = "相比银盐纪实拥有更绵长的中间调过渡与呼吸感黑位，适合黑白人像与阴雨静物。",
            accentHex = "#CBD5E1",
            params = FilterParams(
                exposureEv = 0.08f,
                contrast = 6,
                highlights = -18,
                shadows = 18,
                grainStrength = 16,
                vignette = 14,
                fade = 15,
                softGlow = 18,
                monochrome = true,
            ),
            masterReference = MasterCameraReference(
                baseFilterLabel = "单色柔调 85%",
                softLightLabel = "轻柔",
                tone = 8,
                saturation = -50,
                warmth = 0,
                cyanMagenta = 0,
                sharpness = -4,
                vignetteEnabled = false,
                recommendedIso = "ISO 100–400",
                recommendedShutter = "1/200s",
                recommendedEv = "+0.2 EV",
                recommendedKelvin = 5500,
                sceneAdvice = "适合窗边侧光人像特写、雨雾湖面与极简器物静物",
            ),
            defaultStrength = 1.0f,
            isBuiltIn = true,
            sortOrder = 12,
        ),
    )

    /**
     * Synthesizes a genuine 3D `.cube` Lookup Table from any [FilterPreset] color pipeline so
     * users can export presets directly to external desktop/camera workflows or upload to GL Texture3D.
     */
    fun buildCubeLutForPreset(preset: FilterPreset, lutSize: Int = 17): CubeLut3D {
        val samplePixels = Bitmap.createBitmap(lutSize * lutSize, lutSize, Bitmap.Config.ARGB_8888)
        val raw = IntArray(lutSize * lutSize * lutSize)
        val denom = (lutSize - 1).toFloat()
        var idx = 0
        for (bi in 0 until lutSize) {
            val b = ((bi / denom) * 255f + 0.5f).toInt().coerceIn(0, 255)
            for (gi in 0 until lutSize) {
                val g = ((gi / denom) * 255f + 0.5f).toInt().coerceIn(0, 255)
                for (ri in 0 until lutSize) {
                    val r = ((ri / denom) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    raw[idx++] = Color.argb(255, r, g, b)
                }
            }
        }
        samplePixels.setPixels(raw, 0, lutSize * lutSize, 0, 0, lutSize * lutSize, lutSize)

        // Exclude spatial-only effects (vignette/grain/lightLeak) from color cube table
        val colorOnlyPreset = preset.copy(
            params = preset.params.copy(
                grainStrength = 0,
                vignette = 0,
                lightLeak = 0,
                sharpness = 0,
            ),
            defaultStrength = 1.0f,
        )
        val engine = FilterEngineImpl()
        engine.loadFilter(colorOnlyPreset)
        engine.setStrength(1.0f)
        val mapped = engine.applyToImage(samplePixels)
        val outPixels = IntArray(lutSize * lutSize * lutSize)
        mapped.getPixels(outPixels, 0, lutSize * lutSize, 0, 0, lutSize * lutSize, lutSize)
        samplePixels.recycle()
        mapped.recycle()

        val floats = FloatArray(lutSize * lutSize * lutSize * 3)
        for (v in outPixels.indices) {
            val c = outPixels[v]
            floats[v * 3] = Color.red(c) / 255f
            floats[v * 3 + 1] = Color.green(c) / 255f
            floats[v * 3 + 2] = Color.blue(c) / 255f
        }
        return CubeLut3D(
            title = preset.name,
            size = lutSize,
            table = floats,
        )
    }

    /**
     * Generates a rich synthetic test canvas featuring sky gradient, warm skin-tone sphere,
     * foliage green, neon cyan/magenta accents, and greyscale step wedge for instant offline preview.
     */
    fun createSamplePreviewBitmap(width: Int = 640, height: Int = 480): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sky & dusk horizon gradient
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height * 0.65f,
            intArrayOf(0xFF1E3A5F.toInt(), 0xFF4B79A1.toInt(), 0xFFE6A56C.toInt()),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.65f, paint)

        // Foreground urban street & foliage plane
        paint.shader = LinearGradient(
            0f,
            height * 0.62f,
            0f,
            height.toFloat(),
            intArrayOf(0xFF284236.toInt(), 0xFF161A22.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, height * 0.62f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Sun / warm key orb
        paint.color = 0xFFFFDDA6.toInt()
        canvas.drawCircle(width * 0.72f, height * 0.34f, width * 0.085f, paint)

        // Portrait warm skin-tone reference disc & garment silhouette
        paint.color = 0xFFE2AE8C.toInt()
        canvas.drawCircle(width * 0.36f, height * 0.45f, width * 0.095f, paint)
        paint.color = 0xFF2E5E4E.toInt()
        canvas.drawRoundRect(
            width * 0.22f,
            height * 0.56f,
            width * 0.50f,
            height * 0.88f,
            28f,
            28f,
            paint,
        )

        // Color checker patches at bottom right
        val swatches = intArrayOf(
            0xFFD94E4E.toInt(),
            0xFFE5A93B.toInt(),
            0xFF3E9C66.toInt(),
            0xFF3A82D6.toInt(),
            0xFFF2F4F7.toInt(),
            0xFF22252A.toInt(),
        )
        val barTop = height * 0.89f
        val patchW = width * 0.065f
        swatches.forEachIndexed { idx, col ->
            paint.color = col
            val left = width * 0.56f + idx * (patchW + 4f)
            canvas.drawRoundRect(left, barTop, left + patchW, height * 0.96f, 6f, 6f, paint)
        }
        return bmp
    }

    fun exportPresetCubeText(preset: FilterPreset, lutSize: Int = 17): String {
        return CubeLutParser.serialize(buildCubeLutForPreset(preset, lutSize))
    }
}
