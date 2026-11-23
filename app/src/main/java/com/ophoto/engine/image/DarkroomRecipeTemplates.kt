package com.ophoto.engine.image

import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.engine.filter.MasterColorFamily

/**
 * 暗房一键套用模板（One-Tap Pro Recipe Templates）
 *
 * 针对用户反馈的「相册没有一键套用模板、调色别扭」问题，
 * 精选 8 大流行画报级一键调色配方，将滤镜、实体柔光、光晕、自适应锐度、
 * 理光 GR 对比度曲线、大师色彩家族与高级展厅画框一并打包。
 * 点击单张模板即可瞬间完成完整调色！
 */
data class DarkroomTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val tagColorHex: Long,
    val recipeBuilder: () -> PhotoEditRecipe,
)

object DarkroomRecipeTemplates {

    val ALL: List<DarkroomTemplate> = listOf(
        DarkroomTemplate(
            id = "tmpl_leica_street",
            title = "德味黑白街头",
            subtitle = "银盐纪实 + 理光GR高对比 + 纯黑展框",
            badge = "LEICA MONO",
            tagColorHex = 0xFFCBD5E1,
            recipeBuilder = {
                PhotoEditRecipe(
                    preset = BuiltInPresets.ALL.firstOrNull { it.id == "silver_documentary" },
                    presetStrength = 0.92f,
                    grContrastAmount = 24,
                    masterColorFamily = MasterColorFamily.LEICA_M9,
                    masterToneWeight = 0.85f,
                    masterColorWeight = 0.60f,
                    filmAcutanceStrength = 0.45f,
                    frameStyle = FrameStyle.OBSIDIAN_DARK,
                )
            },
        ),
        DarkroomTemplate(
            id = "tmpl_french_softglow",
            title = "法式奶油柔光",
            subtitle = "柔雾人像 + 1/4黑柔 + 佳能高光Rolloff",
            badge = "CREAM PORTRAIT",
            tagColorHex = 0xFFF472B6,
            recipeBuilder = {
                PhotoEditRecipe(
                    preset = BuiltInPresets.ALL.firstOrNull { it.id == "velvet_skin_portrait" },
                    presetStrength = 0.88f,
                    opticalSoftFilterId = "mist_1_4",
                    opticalSoftAmount = 0.80f,
                    masterColorFamily = MasterColorFamily.CANON_R5,
                    masterToneWeight = 0.75f,
                    masterColorWeight = 0.80f,
                    frameStyle = FrameStyle.CLASSIC_WHITE,
                )
            },
        ),
        DarkroomTemplate(
            id = "tmpl_retro_cafe_film",
            title = "复古午后胶片",
            subtitle = "青绿负片 + 乳剂散射 + 暖棕片基",
            badge = "FILM CAFE",
            tagColorHex = 0xFFE5B869,
            recipeBuilder = {
                PhotoEditRecipe(
                    preset = BuiltInPresets.ALL.firstOrNull { it.id == "emulsion_scatter_film" }
                        ?: BuiltInPresets.ALL.firstOrNull { it.id == "film_green_001" },
                    presetStrength = 0.85f,
                    opticalSoftFilterId = "diffusion",
                    opticalSoftAmount = 0.55f,
                    halationStrength = 0.40f,
                    masterColorFamily = MasterColorFamily.FILM_SPEKTRA,
                    masterToneWeight = 0.80f,
                    masterColorWeight = 0.85f,
                    frameStyle = FrameStyle.FILM_CONTACT,
                )
            },
        ),
        DarkroomTemplate(
            id = "tmpl_night_cyber_neon",
            title = "雨夜霓虹溢光",
            subtitle = "夜景人像 + CineStill红光晕 + AgX色调",
            badge = "NEON NIGHT",
            tagColorHex = 0xFFFF6B5B,
            recipeBuilder = {
                PhotoEditRecipe(
                    preset = BuiltInPresets.ALL.firstOrNull { it.id == "neon_halation_night" }
                        ?: BuiltInPresets.ALL.firstOrNull { it.id == "city_neon_night" },
                    presetStrength = 0.90f,
                    halationStrength = 0.70f,
                    grContrastAmount = 15,
                    masterColorFamily = MasterColorFamily.DARKTABLE_AGX,
                    masterToneWeight = 0.90f,
                    masterColorWeight = 0.70f,
                    frameStyle = FrameStyle.OBSIDIAN_DARK,
                )
            },
        ),
        DarkroomTemplate(
            id = "tmpl_coastal_breeze",
            title = "海风夏日青蓝",
            subtitle = "低饱和青蓝 + 白柔雾面 + 宽幅三分割",
            badge = "CYAN BREEZE",
            tagColorHex = 0xFF38BDF8,
            recipeBuilder = {
                PhotoEditRecipe(
                    preset = BuiltInPresets.ALL.firstOrNull { it.id == "coastal_breeze_cyan" },
                    presetStrength = 0.90f,
                    opticalSoftFilterId = "white_mist",
                    opticalSoftAmount = 0.60f,
                    masterColorFamily = MasterColorFamily.LUMIX_S9,
                    masterToneWeight = 0.70f,
                    masterColorWeight = 0.75f,
                    cropPreset = CropAspectPreset.R_16_9,
                    frameStyle = FrameStyle.CLASSIC_WHITE,
                )
            },
        ),
        DarkroomTemplate(
            id = "tmpl_amber_sunset",
            title = "落日琥珀大景",
            subtitle = "高光衍射HDF + 暖金发丝 + 胶片锐度",
            badge = "GOLDEN HOUR",
            tagColorHex = 0xFFF59E0B,
            recipeBuilder = {
                PhotoEditRecipe(
                    preset = BuiltInPresets.ALL.firstOrNull { it.id == "amber_golden_hour" },
                    presetStrength = 0.88f,
                    opticalSoftFilterId = "hdf",
                    opticalSoftAmount = 0.85f,
                    filmAcutanceStrength = 0.60f,
                    masterColorFamily = MasterColorFamily.CANON_R5,
                    masterToneWeight = 0.80f,
                    masterColorWeight = 0.70f,
                    frameStyle = FrameStyle.CLASSIC_WHITE,
                )
            },
        ),
        DarkroomTemplate(
            id = "tmpl_gr_high_contrast",
            title = "理光 GR 街拍速写",
            subtitle = "S型高反差 + 粗银盐颗粒 + 4:3 极简框",
            badge = "RICOH GR",
            tagColorHex = 0xFFE5B869,
            recipeBuilder = {
                PhotoEditRecipe(
                    preset = BuiltInPresets.ALL.firstOrNull { it.id == "terracotta_humanist" },
                    presetStrength = 0.85f,
                    grContrastAmount = 35,
                    filmAcutanceStrength = 0.50f,
                    frameStyle = FrameStyle.OBSIDIAN_DARK,
                )
            },
        ),
        DarkroomTemplate(
            id = "tmpl_nordic_misty_slate",
            title = "冷峻几何微光",
            subtitle = "极简冷调 + 暗部抬升 + 斐波那契秩序",
            badge = "SLATE MINIMAL",
            tagColorHex = 0xFF94A3B8,
            recipeBuilder = {
                PhotoEditRecipe(
                    preset = BuiltInPresets.ALL.firstOrNull { it.id == "architectural_slate" },
                    presetStrength = 0.90f,
                    grContrastAmount = -10,
                    masterColorFamily = MasterColorFamily.LUMIX_S9,
                    masterToneWeight = 0.85f,
                    masterColorWeight = 0.65f,
                    frameStyle = FrameStyle.CLASSIC_WHITE,
                )
            },
        ),
    )
}
