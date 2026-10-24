package com.ophoto.engine.camera

import android.graphics.Bitmap
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.ophoto.core.device.PhysicalLensSpec
import com.ophoto.engine.filter.FilterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

enum class CameraCaptureMode(val label: String) {
    PHOTO("拍照"),
    PORTRAIT("人像"),
    NIGHT("夜景"),
    PRO("专业"),
    VIDEO("视频"),
    LONG_EXPOSURE("长曝光"),
    DOCUMENT("文档"),
}

enum class FlashSetting(val label: String) {
    OFF("关闭"),
    AUTO("自动"),
    ON("补光"),
    TORCH("常亮"),
}

/**
 * Viewfinder aspect ratios with accurate [portraitRatio] (width / height in handheld portrait orientation)
 * plus [ratioValue] preserved for compatibility.
 */
enum class ViewfinderAspectRatio(
    val label: String,
    val portraitRatio: Float,
    val ratioValue: Float,
) {
    RATIO_4_3("4:3", 3f / 4f, 4f / 3f),
    RATIO_3_2("3:2", 2f / 3f, 3f / 2f),
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_16_9("16:9", 9f / 16f, 16f / 9f),
    RATIO_21_9("XPan", 65f / 24f, 65f / 24f),
}

enum class MeteringMode(val label: String) {
    MATRIX("矩阵测光"),
    CENTER_WEIGHTED("中央重点"),
    SPOT("点测光"),
    FACE_PRIORITY("人像测光"),
}

enum class WhiteBalanceMode(val label: String, val approximateKelvin: Int) {
    AUTO("AWB", 5500),
    DAYLIGHT("日光", 5200),
    CLOUDY("阴天", 6500),
    INCANDESCENT("钨丝灯", 3000),
    FLUORESCENT("荧光灯", 4000),
    SHADE("阴影", 7200),
    MANUAL("手动 K", 5500),
}

enum class CaptureFormatOption(val label: String) {
    JPEG("JPEG"),
    RAW_PLUS_JPEG("RAW + JPEG"),
}

/**
 * Pro exposure configuration supporting:
 * - Full Auto AE (default in Pro & Photo modes so viewfinder is always properly exposed)
 * - Semi-Auto ISO Priority ([manualIso] = true, [manualShutter] = false)
 * - Semi-Auto Shutter Priority ([manualIso] = false, [manualShutter] = true)
 * - Full Manual M ([manualIso] = true && [manualShutter] = true, or legacy [manualMode] = true)
 */
data class ExposureConfig(
    val manualMode: Boolean = false,
    val manualIso: Boolean = false,
    val manualShutter: Boolean = false,
    val iso: Int = 200,
    val shutterSeconds: Double = 1.0 / 60.0,
    val evCompensation: Float = 0f,
    val aeLocked: Boolean = false,
    val meteringMode: MeteringMode = MeteringMode.CENTER_WEIGHTED,
) {
    val isIsoManualEffective: Boolean
        get() = manualIso || (manualMode && !manualShutter)

    val isShutterManualEffective: Boolean
        get() = manualShutter || (manualMode && !manualIso)

    val isAnyManual: Boolean
        get() = manualMode || manualIso || manualShutter

    /**
     * Resolves hardware (ISO, ShutterNs) using real ambient AE metering so changing ISO alone
     * or Shutter alone automatically compensates the paired axis via exposure reciprocity.
     */
    fun resolveManualParameters(
        meteredIso: Int,
        meteredExposureNs: Long,
        isoRange: IntRange = 50..12800,
        shutterNsRange: LongRange = 125_000L..30_000_000_000L,
    ): Pair<Int, Long> {
        val safeAmbIso = meteredIso.coerceIn(50, 25600)
        val safeAmbNs = meteredExposureNs.coerceIn(100_000L, 1_000_000_000L)
        val evFactor = 2.0.pow(evCompensation.toDouble())
        val targetProductNs = safeAmbIso.toDouble() * safeAmbNs.toDouble() * evFactor

        val fullManual = (manualIso && manualShutter) || (manualMode && !manualIso && !manualShutter)
        return when {
            fullManual -> {
                val outIso = iso.coerceIn(isoRange.first, isoRange.last)
                val outNs = (shutterSeconds * 1_000_000_000.0).toLong()
                    .coerceIn(shutterNsRange.first, shutterNsRange.last)
                outIso to outNs
            }
            manualIso -> {
                val outIso = iso.coerceIn(isoRange.first, isoRange.last)
                val maxAutoHandheldNs = minOf(shutterNsRange.last, 125_000_000L) // 1/8s ceiling
                val solvedNs = (targetProductNs / outIso.coerceAtLeast(1)).toLong()
                    .coerceIn(shutterNsRange.first, maxAutoHandheldNs)
                outIso to solvedNs
            }
            manualShutter -> {
                val outNs = (shutterSeconds * 1_000_000_000.0).toLong()
                    .coerceIn(shutterNsRange.first, shutterNsRange.last)
                val solvedIso = (targetProductNs / outNs.coerceAtLeast(1_000L))
                    .roundToInt()
                    .coerceIn(isoRange.first, isoRange.last)
                solvedIso to outNs
            }
            else -> {
                safeAmbIso.coerceIn(isoRange.first, isoRange.last) to
                    safeAmbNs.coerceIn(shutterNsRange.first, shutterNsRange.last)
            }
        }
    }

    /**
     * Estimates EV stop difference relative to metered ambient light (negative = underexposed).
     */
    fun estimateExposureDeltaStops(meteredIso: Int, meteredExposureNs: Long): Float {
        if (!isAnyManual) return evCompensation
        val ambProduct = meteredIso.coerceAtLeast(50).toDouble() *
            meteredExposureNs.coerceAtLeast(100_000L).toDouble()
        val (effIso, effNs) = resolveManualParameters(meteredIso, meteredExposureNs)
        val effProduct = effIso.toDouble() * effNs.toDouble()
        if (ambProduct <= 0.0 || effProduct <= 0.0) return 0f
        return (ln(effProduct / ambProduct) / ln(2.0)).toFloat().coerceIn(-8f, 8f)
    }
}

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

data class CameraLiveTelemetry(
    val iso: Int = 200,
    val shutterSeconds: Double = 1.0 / 60.0,
    val aperture: Float = 1.8f,
    val focusDiopters: Float = 0f,
    val ambientIso: Int = 200,
    val ambientExposureNs: Long = 20_000_000L,
    val exposureDeltaEv: Float = 0f,
)

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

interface ICameraEngine {
    val liveTelemetryFlow: StateFlow<CameraLiveTelemetry>
        get() = MutableStateFlow(CameraLiveTelemetry())

    suspend fun bindSession(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        cameraId: String,
    )
    suspend fun setVideoModeEnabled(enabled: Boolean) {}
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
