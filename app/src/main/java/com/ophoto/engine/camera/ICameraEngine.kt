package com.ophoto.engine.camera

import android.graphics.Bitmap
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.ophoto.core.device.PhysicalLensSpec
import com.ophoto.engine.filter.FilterConfig

enum class CameraCaptureMode(val label: String) {
    PHOTO("拍照"),
    VIDEO("视频"),
    PORTRAIT("人像"),
    NIGHT("夜景"),
    PRO("专业"),
    LONG_EXPOSURE("长曝光"),
    DOCUMENT("文档"),
}

enum class FlashSetting(val label: String) {
    OFF("关闭"),
    AUTO("自动"),
    ON("常亮/补光"),
    TORCH("手电筒"),
}

enum class ViewfinderAspectRatio(val label: String, val ratioValue: Float) {
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_3_2("3:2", 3f / 2f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_1_1("1:1", 1f),
    RATIO_21_9("21:9", 21f / 9f),
}

enum class MeteringMode(val label: String) {
    MATRIX("全局测光"),
    CENTER_WEIGHTED("中央重点"),
    SPOT("点测光"),
    FACE_PRIORITY("人像优先"),
}

enum class WhiteBalanceMode(val label: String, val approximateKelvin: Int) {
    AUTO("AWB 自动", 5500),
    DAYLIGHT("日光", 5200),
    CLOUDY("阴天", 6500),
    INCANDESCENT("白炽灯", 2850),
    FLUORESCENT("荧光灯", 4000),
    SHADE("阴影", 7500),
    MANUAL("手动色温", 5500),
}

enum class CaptureFormatOption(val label: String) {
    JPEG("JPEG"),
    RAW_PLUS_JPEG("RAW + JPEG"),
}

data class ExposureConfig(
    val manualMode: Boolean = false,
    val iso: Int = 100,
    val shutterSeconds: Double = 1.0 / 125.0,
    val evCompensation: Float = 0f,
    val aeLocked: Boolean = false,
    val meteringMode: MeteringMode = MeteringMode.CENTER_WEIGHTED,
)

data class FocusConfig(
    val manualFocus: Boolean = false,
    val focusDistanceDiopters: Float = 0f,
    val afLocked: Boolean = false,
    val normalizedTapX: Float? = null,
    val normalizedTapY: Float? = null,
)

data class WhiteBalanceConfig(
    val mode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val manualKelvin: Int = 5500,
    val awbLocked: Boolean = false,
)

/**
 * CaptureConfig per Doc §5.
 */
data class CaptureConfig(
    val mode: CameraCaptureMode = CameraCaptureMode.PHOTO,
    val flashMode: FlashSetting = FlashSetting.OFF,
    val aspectRatio: ViewfinderAspectRatio = ViewfinderAspectRatio.RATIO_4_3,
    val enableRaw: Boolean = false,
    val enableHdr: Boolean = false,
    val exposure: ExposureConfig = ExposureConfig(),
    val focus: FocusConfig = FocusConfig(),
    val whiteBalance: WhiteBalanceConfig = WhiteBalanceConfig(),
    val filterId: String? = null,
    val filterStrength: Float = 0.85f,
    val timerSeconds: Int = 0,
    val burstCount: Int = 1,
    val bracketStops: Boolean = false,
)

data class VideoConfig(
    val qualityTag: String = "1080P",
    val fps: Int = 30,
    val enableAudio: Boolean = true,
    val stabilization: Boolean = true,
)

data class CameraCapabilities(
    val availableLenses: List<PhysicalLensSpec> = emptyList(),
    val activeCameraId: String = "0",
    val isoRange: IntRange = 50..12800,
    val shutterNsRange: LongRange = 125_000L..30_000_000_000L,
    val evRange: ClosedFloatingPointRange<Float> = -2.0f..2.0f,
    val maxFocusDiopters: Float = 10f,
    val zoomRange: ClosedFloatingPointRange<Float> = 0.6f..10f,
    val supportsRaw: Boolean = false,
)

data class PhotoResult(
    val bitmap: Bitmap?,
    val width: Int,
    val height: Int,
    val capturedCount: Int = 1,
    val errorMessage: String? = null,
)

data class VideoResult(
    val savedUriString: String?,
    val durationMillis: Long,
    val errorMessage: String? = null,
)

/**
 * Standard Camera Engine contract per Doc §5.
 */
interface ICameraEngine {
    suspend fun bindSession(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        cameraId: String,
    )
    suspend fun openCamera(cameraId: String)
    suspend fun closeCamera()
    suspend fun takePhoto(config: CaptureConfig): PhotoResult
    suspend fun startVideoRecord(config: VideoConfig)
    suspend fun stopVideoRecord(): VideoResult
    fun setExposure(exposure: ExposureConfig)
    fun setFocus(focus: FocusConfig)
    fun setWhiteBalance(wb: WhiteBalanceConfig)
    fun setZoomRatio(zoom: Float)
    fun setFilter(filter: FilterConfig)
    fun getCameraCapabilities(): CameraCapabilities
}
