package com.ophoto.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ophotoDataStore: DataStore<Preferences> by preferencesDataStore(name = "ophoto_settings")

enum class CompositionGuideType(val label: String) {
    RULE_OF_THIRDS("九宫格"),
    GOLDEN_SPIRAL("黄金螺旋"),
    DIAGONAL("对角线"),
    CENTER_CROSS("中心十字"),
    CINEMA_SAFE("电影安全框"),
    NONE("关闭网格"),
}

enum class VolumeKeyAction(val label: String) {
    SHUTTER("拍照/录像快门"),
    ZOOM("光学变焦推拉"),
    EXPOSURE("EV 曝光微调"),
}

enum class VideoLutMode(val label: String) {
    MONITOR_ONLY("仅监看 LUT（录原片省电）"),
    LOW_POWER_PREVIEW("低功耗实时滤镜（推荐）"),
    FULL_QUALITY("全精度 GPU 渲染"),
}

enum class FloatingHudStyle(val label: String) {
    STANDARD_CARD("标准大师参数卡"),
    COMPACT_PILL("紧凑顶部胶囊"),
    MINI_ORB("迷你悬浮球"),
}

data class UserSettings(
    val shutterSound: Boolean = true,
    val compositionGuide: CompositionGuideType = CompositionGuideType.RULE_OF_THIRDS,
    val showLeveler: Boolean = true,
    val touchToCapture: Boolean = false,
    val showHistogram: Boolean = true,
    val showZebraStripes: Boolean = false,
    val showParameterHud: Boolean = true,
    val saveGpsLocation: Boolean = false, // Privacy-by-default per Doc §15
    val mirrorFrontCamera: Boolean = true,
    val volumeKeyAction: VolumeKeyAction = VolumeKeyAction.SHUTTER,
    val videoStabilization: Boolean = true,
    val fileNameTemplate: String = "OPHOTO_{yyyyMMdd}_{HHmmss}",
    val enableRawWhenSupported: Boolean = false,
    val videoResolutionTag: String = "1080P 60fps",
    val videoLutMode: VideoLutMode = VideoLutMode.LOW_POWER_PREVIEW,
    val defaultFilterStrength: Float = 0.85f,
    val realtimeFilterPreview: Boolean = true,
    val floatingHudStyle: FloatingHudStyle = FloatingHudStyle.STANDARD_CARD,
    val floatingHudOpacity: Float = 0.86f,
    val selectedCityId: String = "hk",
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val SHUTTER_SOUND = booleanPreferencesKey("shutter_sound")
        val COMPOSITION_GUIDE = stringPreferencesKey("composition_guide")
        val SHOW_LEVELER = booleanPreferencesKey("show_leveler")
        val TOUCH_CAPTURE = booleanPreferencesKey("touch_capture")
        val SHOW_HISTOGRAM = booleanPreferencesKey("show_histogram")
        val SHOW_ZEBRA = booleanPreferencesKey("show_zebra")
        val SHOW_HUD = booleanPreferencesKey("show_hud")
        val SAVE_GPS = booleanPreferencesKey("save_gps")
        val MIRROR_FRONT = booleanPreferencesKey("mirror_front")
        val VOLUME_ACTION = stringPreferencesKey("volume_action")
        val VIDEO_STAB = booleanPreferencesKey("video_stabilization")
        val FILE_TEMPLATE = stringPreferencesKey("file_name_template")
        val ENABLE_RAW = booleanPreferencesKey("enable_raw")
        val VIDEO_RES = stringPreferencesKey("video_res")
        val VIDEO_LUT_MODE = stringPreferencesKey("video_lut_mode")
        val DEFAULT_STRENGTH = floatPreferencesKey("default_filter_strength")
        val REALTIME_PREVIEW = booleanPreferencesKey("realtime_filter_preview")
        val FLOATING_STYLE = stringPreferencesKey("floating_hud_style")
        val FLOATING_OPACITY = floatPreferencesKey("floating_hud_opacity")
        val SELECTED_CITY = stringPreferencesKey("selected_city")
        val OVERLAY_ACTIVE_PRESET_ID = stringPreferencesKey("overlay_active_preset_id")
    }

    val settingsFlow: Flow<UserSettings> = context.ophotoDataStore.data.map { pref ->
        UserSettings(
            shutterSound = pref[Keys.SHUTTER_SOUND] ?: true,
            compositionGuide = pref[Keys.COMPOSITION_GUIDE]
                ?.let { runCatching { CompositionGuideType.valueOf(it) }.getOrNull() }
                ?: CompositionGuideType.RULE_OF_THIRDS,
            showLeveler = pref[Keys.SHOW_LEVELER] ?: true,
            touchToCapture = pref[Keys.TOUCH_CAPTURE] ?: false,
            showHistogram = pref[Keys.SHOW_HISTOGRAM] ?: true,
            showZebraStripes = pref[Keys.SHOW_ZEBRA] ?: false,
            showParameterHud = pref[Keys.SHOW_HUD] ?: true,
            saveGpsLocation = pref[Keys.SAVE_GPS] ?: false,
            mirrorFrontCamera = pref[Keys.MIRROR_FRONT] ?: true,
            volumeKeyAction = pref[Keys.VOLUME_ACTION]
                ?.let { runCatching { VolumeKeyAction.valueOf(it) }.getOrNull() }
                ?: VolumeKeyAction.SHUTTER,
            videoStabilization = pref[Keys.VIDEO_STAB] ?: true,
            fileNameTemplate = pref[Keys.FILE_TEMPLATE] ?: "OPHOTO_{yyyyMMdd}_{HHmmss}",
            enableRawWhenSupported = pref[Keys.ENABLE_RAW] ?: false,
            videoResolutionTag = pref[Keys.VIDEO_RES] ?: "1080P 60fps",
            videoLutMode = pref[Keys.VIDEO_LUT_MODE]
                ?.let { runCatching { VideoLutMode.valueOf(it) }.getOrNull() }
                ?: VideoLutMode.LOW_POWER_PREVIEW,
            defaultFilterStrength = pref[Keys.DEFAULT_STRENGTH] ?: 0.85f,
            realtimeFilterPreview = pref[Keys.REALTIME_PREVIEW] ?: true,
            floatingHudStyle = pref[Keys.FLOATING_STYLE]
                ?.let { runCatching { FloatingHudStyle.valueOf(it) }.getOrNull() }
                ?: FloatingHudStyle.STANDARD_CARD,
            floatingHudOpacity = pref[Keys.FLOATING_OPACITY] ?: 0.86f,
            selectedCityId = pref[Keys.SELECTED_CITY] ?: "hk",
        )
    }

    val overlayPresetIdFlow: Flow<String?> = context.ophotoDataStore.data.map {
        it[Keys.OVERLAY_ACTIVE_PRESET_ID]
    }

    suspend fun update(block: (UserSettings) -> UserSettings) {
        context.ophotoDataStore.edit { pref ->
            val current = UserSettings(
                shutterSound = pref[Keys.SHUTTER_SOUND] ?: true,
                compositionGuide = pref[Keys.COMPOSITION_GUIDE]
                    ?.let { runCatching { CompositionGuideType.valueOf(it) }.getOrNull() }
                    ?: CompositionGuideType.RULE_OF_THIRDS,
                showLeveler = pref[Keys.SHOW_LEVELER] ?: true,
                touchToCapture = pref[Keys.TOUCH_CAPTURE] ?: false,
                showHistogram = pref[Keys.SHOW_HISTOGRAM] ?: true,
                showZebraStripes = pref[Keys.SHOW_ZEBRA] ?: false,
                showParameterHud = pref[Keys.SHOW_HUD] ?: true,
                saveGpsLocation = pref[Keys.SAVE_GPS] ?: false,
                mirrorFrontCamera = pref[Keys.MIRROR_FRONT] ?: true,
                volumeKeyAction = pref[Keys.VOLUME_ACTION]
                    ?.let { runCatching { VolumeKeyAction.valueOf(it) }.getOrNull() }
                    ?: VolumeKeyAction.SHUTTER,
                videoStabilization = pref[Keys.VIDEO_STAB] ?: true,
                fileNameTemplate = pref[Keys.FILE_TEMPLATE] ?: "OPHOTO_{yyyyMMdd}_{HHmmss}",
                enableRawWhenSupported = pref[Keys.ENABLE_RAW] ?: false,
                videoResolutionTag = pref[Keys.VIDEO_RES] ?: "1080P 60fps",
                videoLutMode = pref[Keys.VIDEO_LUT_MODE]
                    ?.let { runCatching { VideoLutMode.valueOf(it) }.getOrNull() }
                    ?: VideoLutMode.LOW_POWER_PREVIEW,
                defaultFilterStrength = pref[Keys.DEFAULT_STRENGTH] ?: 0.85f,
                realtimeFilterPreview = pref[Keys.REALTIME_PREVIEW] ?: true,
                floatingHudStyle = pref[Keys.FLOATING_STYLE]
                    ?.let { runCatching { FloatingHudStyle.valueOf(it) }.getOrNull() }
                    ?: FloatingHudStyle.STANDARD_CARD,
                floatingHudOpacity = pref[Keys.FLOATING_OPACITY] ?: 0.86f,
                selectedCityId = pref[Keys.SELECTED_CITY] ?: "hk",
            )
            val next = block(current)
            pref[Keys.SHUTTER_SOUND] = next.shutterSound
            pref[Keys.COMPOSITION_GUIDE] = next.compositionGuide.name
            pref[Keys.SHOW_LEVELER] = next.showLeveler
            pref[Keys.TOUCH_CAPTURE] = next.touchToCapture
            pref[Keys.SHOW_HISTOGRAM] = next.showHistogram
            pref[Keys.SHOW_ZEBRA] = next.showZebraStripes
            pref[Keys.SHOW_HUD] = next.showParameterHud
            pref[Keys.SAVE_GPS] = next.saveGpsLocation
            pref[Keys.MIRROR_FRONT] = next.mirrorFrontCamera
            pref[Keys.VOLUME_ACTION] = next.volumeKeyAction.name
            pref[Keys.VIDEO_STAB] = next.videoStabilization
            pref[Keys.FILE_TEMPLATE] = next.fileNameTemplate
            pref[Keys.ENABLE_RAW] = next.enableRawWhenSupported
            pref[Keys.VIDEO_RES] = next.videoResolutionTag
            pref[Keys.VIDEO_LUT_MODE] = next.videoLutMode.name
            pref[Keys.DEFAULT_STRENGTH] = next.defaultFilterStrength
            pref[Keys.REALTIME_PREVIEW] = next.realtimeFilterPreview
            pref[Keys.FLOATING_STYLE] = next.floatingHudStyle.name
            pref[Keys.FLOATING_OPACITY] = next.floatingHudOpacity
            pref[Keys.SELECTED_CITY] = next.selectedCityId
        }
    }

    suspend fun setOverlayPresetId(presetId: String) {
        context.ophotoDataStore.edit { pref ->
            pref[Keys.OVERLAY_ACTIVE_PRESET_ID] = presetId
        }
    }
}
