package com.ophoto.engine.filter

import android.graphics.Bitmap

enum class FilterCategory(val label: String) {
    RECOMMENDED("推荐"),
    FILM("胶片"),
    MONOCHROME("黑白"),
    PORTRAIT("人像"),
    NIGHT("夜景"),
    STREET_SCENERY("风光/人文"),
    IMPORTED("导入"),
}

enum class FilterType {
    BASIC_PARAMS,
    CUBE_LUT,
    LUT_PLUS_PARAMS,
}

/**
 * Dual-mode Master Camera Tuning Reference (inspired by OPPO Master Mode & OMaster parameter workflow,
 * 100% clean-room original model so users can both render directly inside Ophoto AND display parameters
 * over the native system camera via [com.ophoto.service.MasterOverlayService]).
 */
data class MasterCameraReference(
    val baseFilterLabel: String = "自然 85%",
    val softLightLabel: String = "无",
    val tone: Int = 0,          // -50 .. +50 (影调)
    val saturation: Int = 0,    // -50 .. +50 (饱和度)
    val warmth: Int = 0,        // -50 .. +50 (冷暖)
    val cyanMagenta: Int = 0,   // -50 .. +50 (青品)
    val sharpness: Int = 0,     // -50 .. +50 (锐度)
    val vignetteEnabled: Boolean = false,
    val recommendedIso: String = "Auto (≤800)",
    val recommendedShutter: String = "1/125s",
    val recommendedEv: String = "-0.3 EV",
    val recommendedKelvin: Int = 5400,
    val sceneAdvice: String = "适合自然光日常街拍与人文纪实",
)

data class FilterParams(
    val exposureEv: Float = 0f,           // -2.0 .. +2.0
    val contrast: Int = 0,                // -100 .. +100
    val saturation: Int = 0,              // -100 .. +100
    val vibrance: Int = 0,                // -100 .. +100
    val temperature: Int = 0,             // -100 .. +100
    val tint: Int = 0,                    // -100 .. +100
    val highlights: Int = 0,              // -100 .. +100
    val shadows: Int = 0,                 // -100 .. +100
    val whites: Int = 0,                  // -100 .. +100
    val blacks: Int = 0,                  // -100 .. +100
    val highlightTintHex: String = "#FFFFFF",
    val shadowTintHex: String = "#000000",
    val colorCastStrength: Int = 0,       // 0 .. 100
    val grainStrength: Int = 0,           // 0 .. 100
    val vignette: Int = 0,                // 0 .. 100
    val fade: Int = 0,                    // 0 .. 100
    val softGlow: Int = 0,                // 0 .. 100
    val lightLeak: Int = 0,               // 0 .. 100
    val sharpness: Int = 0,               // 0 .. 100
    val monochrome: Boolean = false,
)

data class FilterPreset(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val category: FilterCategory = FilterCategory.RECOMMENDED,
    val type: FilterType = FilterType.LUT_PLUS_PARAMS,
    val author: String = "Ophoto Lab",
    val version: String = "1.0.0",
    val description: String = "",
    val accentHex: String = "#E5B869",
    val lutPath: String? = null,
    val previewPath: String? = null,
    val params: FilterParams = FilterParams(),
    val masterReference: MasterCameraReference = MasterCameraReference(),
    val defaultStrength: Float = 0.85f,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class FilterConfig(
    val presetId: String?,
    val strength: Float = 1.0f,
    val paramsOverride: FilterParams? = null,
)

data class HistogramData(
    val redBins: IntArray = IntArray(64),
    val greenBins: IntArray = IntArray(64),
    val blueBins: IntArray = IntArray(64),
    val lumaBins: IntArray = IntArray(64),
    val highlightClipPercent: Float = 0f,
    val shadowClipPercent: Float = 0f,
    val averageEvOffset: Float = 0f,
)

/**
 * Standard Filter Engine contract per Doc §6.
 */
interface IFilterEngine {
    fun loadFilter(filter: FilterPreset): Boolean
    fun loadFilterWithExplicitLut(filter: FilterPreset, lut: CubeLut3D?)
    fun applyToPreviewTexture(textureId: Int): Int
    fun applyToImage(input: Bitmap): Bitmap
    fun setStrength(strength: Float)
    fun reset()
    fun exportConfig(): FilterConfig
    fun currentLut(): CubeLut3D?
    fun computeHistogram(bitmap: Bitmap): HistogramData
}
