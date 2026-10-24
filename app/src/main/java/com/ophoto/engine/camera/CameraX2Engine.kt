package com.ophoto.engine.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.ophoto.core.device.OppoDeviceInspector
import com.ophoto.core.log.SafeLogger
import com.ophoto.engine.filter.FilterConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Production CameraX + Camera2Interop Engine:
 * - Isolates Photo/Pro 2-stream binding (`Preview + ImageCapture`) from Video binding so sensor AE
 *   can expose freely below 1/30s in indoor/low-light scenes without 3-surface HAL failures.
 * - Streams real-time Camera2 `TotalCaptureResult` telemetry (ISO, Shutter, Aperture, Focus) and
 *   powers Semi-Auto Reciprocity (ISO-priority / Shutter-priority) so Pro mode is never dark.
 */
@OptIn(ExperimentalCamera2Interop::class)
class CameraX2Engine(
    private val context: Context,
) : ICameraEngine {

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null
    private var videoCaptureUseCase: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var videoStartMillis: Long = 0L
    private var videoFinishDeferred: CompletableDeferred<VideoResult>? = null

    private var lastLifecycleOwner: LifecycleOwner? = null
    private var lastSurfaceProvider: Preview.SurfaceProvider? = null
    private var activeCameraId: String = "0"
    private var videoModeEnabled: Boolean = false
    private var pendingZoomRatio: Float = 1.0f

    private var currentExposure = ExposureConfig()
    private var currentFocus = FocusConfig()
    private var currentWb = WhiteBalanceConfig()
    private var currentFilter = FilterConfig(null, 1f)

    @Volatile
    private var ambientMeteredIso: Int = 250

    @Volatile
    private var ambientMeteredExposureNs: Long = 20_000_000L // 1/50s indoor-safe baseline

    @Volatile
    private var lastTelemetryEmitMs: Long = 0L

    private val _liveTelemetryFlow = MutableStateFlow(CameraLiveTelemetry())
    override val liveTelemetryFlow: StateFlow<CameraLiveTelemetry> = _liveTelemetryFlow

    override suspend fun bindSession(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        cameraId: String,
    ) = withContext(Dispatchers.Main) {
        lastLifecycleOwner = lifecycleOwner
        lastSurfaceProvider = surfaceProvider
        activeCameraId = cameraId

        val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).await().also {
            cameraProvider = it
        }

        val selector = buildSelectorForCameraId(cameraId)

        val previewBuilder = Preview.Builder()
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    val reportedIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                    val reportedNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                    val reportedAperture = result.get(CaptureResult.LENS_APERTURE)
                    val reportedFocus = result.get(CaptureResult.LENS_FOCUS_DISTANCE)

                    if (!currentExposure.isAnyManual) {
                        if (reportedIso != null && reportedIso in 25..51200) {
                            ambientMeteredIso = reportedIso
                        }
                        if (reportedNs != null && reportedNs in 50_000L..2_000_000_000L) {
                            ambientMeteredExposureNs = reportedNs
                        }
                    }

                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastTelemetryEmitMs >= 160L) {
                        lastTelemetryEmitMs = now
                        val curIso = reportedIso ?: ambientMeteredIso
                        val curNs = reportedNs ?: ambientMeteredExposureNs
                        val deltaEv = currentExposure.estimateExposureDeltaStops(
                            ambientMeteredIso,
                            ambientMeteredExposureNs,
                        )
                        _liveTelemetryFlow.value = CameraLiveTelemetry(
                            iso = curIso,
                            shutterSeconds = (curNs.toDouble() / 1_000_000_000.0).coerceAtLeast(1e-5),
                            aperture = reportedAperture ?: 1.8f,
                            focusDiopters = reportedFocus ?: 0f,
                            ambientIso = ambientMeteredIso,
                            ambientExposureNs = ambientMeteredExposureNs,
                            exposureDeltaEv = deltaEv,
                        )
                    }
                }
            },
        )

        val preview = previewBuilder.build().also {
            it.surfaceProvider = surfaceProvider
        }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(95)
            .build()

        runCatching {
            provider.unbindAll()
            if (videoModeEnabled) {
                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.UHD, Quality.HD),
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                activeCamera = runCatching {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture,
                        videoCapture,
                    )
                }.getOrElse {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        videoCapture,
                    )
                }
                videoCaptureUseCase = videoCapture
            } else {
                // Bind strictly Preview + ImageCapture in Photo/Pro modes so low-light exposure
                // can lengthen past 1/30s and all Camera2 HAL levels bind reliably!
                activeCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture,
                )
                videoCaptureUseCase = null
            }

            previewUseCase = preview
            imageCaptureUseCase = imageCapture
            applyExposureCompensationIndex(currentExposure)
            applyCamera2CaptureOptionsAsync()
            if (pendingZoomRatio != 1.0f) {
                setZoomRatio(pendingZoomRatio)
            }
        }.onFailure { err ->
            SafeLogger.error("CameraX2Engine", "Failed to bind camera session", err)
        }
        Unit
    }

    override suspend fun setVideoModeEnabled(enabled: Boolean) {
        if (videoModeEnabled == enabled) return
        videoModeEnabled = enabled
        val owner = lastLifecycleOwner
        val surface = lastSurfaceProvider
        if (owner != null && surface != null) {
            bindSession(owner, surface, activeCameraId)
        }
    }

    override suspend fun openCamera(cameraId: String) {
        activeCameraId = cameraId
        pendingZoomRatio = 1.0f
        val owner = lastLifecycleOwner
        val surface = lastSurfaceProvider
        if (owner != null && surface != null) {
            bindSession(owner, surface, cameraId)
        }
    }

    override suspend fun closeCamera() = withContext(Dispatchers.Main) {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        activeCamera = null
    }

    override suspend fun takePhoto(config: CaptureConfig): PhotoResult {
        val capture = imageCaptureUseCase
            ?: return PhotoResult(null, 0, 0, 0, "相机尚未就绪")

        withContext(Dispatchers.Main) {
            capture.flashMode = when (config.flashMode) {
                FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
                FlashSetting.TORCH -> ImageCapture.FLASH_MODE_OFF
            }
            activeCamera?.cameraControl?.enableTorch(config.flashMode == FlashSetting.TORCH)
        }

        val exposureChanged = currentExposure != config.exposure ||
            currentFocus != config.focus ||
            currentWb != config.whiteBalance
        currentExposure = config.exposure
        currentFocus = config.focus
        currentWb = config.whiteBalance

        if (exposureChanged) {
            withContext(Dispatchers.Main) {
                runCatching { applyExposureCompensationIndex(config.exposure)?.await() }
                runCatching { applyCamera2CaptureOptionsAsync()?.await() }
            }
            if (config.exposure.isAnyManual) {
                delay(55L)
            }
        }

        val framesToCapture = when {
            config.bracketStops -> 3
            config.burstCount > 1 -> config.burstCount.coerceIn(2, 10)
            else -> 1
        }

        var primaryBitmap: Bitmap? = null
        var successFrames = 0
        val bracketOffsets = listOf(-1.7f, 0f, 1.7f)

        for (index in 0 until framesToCapture) {
            if (config.bracketStops) {
                val bracketExp = config.exposure.copy(evCompensation = bracketOffsets[index])
                currentExposure = bracketExp
                withContext(Dispatchers.Main) {
                    runCatching { applyExposureCompensationIndex(bracketExp)?.await() }
                    runCatching { applyCamera2CaptureOptionsAsync()?.await() }
                }
                delay(75L)
            }
            val single = captureSingleBitmap(capture)
            if (single != null) {
                successFrames++
                if (primaryBitmap == null || (config.bracketStops && index == 1)) {
                    primaryBitmap = single
                }
            }
        }

        if (config.bracketStops) {
            currentExposure = config.exposure
            withContext(Dispatchers.Main) {
                runCatching { applyExposureCompensationIndex(config.exposure)?.await() }
                runCatching { applyCamera2CaptureOptionsAsync()?.await() }
            }
        }

        return if (primaryBitmap != null) {
            PhotoResult(
                bitmap = primaryBitmap,
                width = primaryBitmap.width,
                height = primaryBitmap.height,
                capturedCount = successFrames,
            )
        } else {
            PhotoResult(null, 0, 0, 0, "快门捕获失败")
        }
    }

    private suspend fun captureSingleBitmap(capture: ImageCapture): Bitmap? {
        val deferred = CompletableDeferred<Bitmap?>()
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = runCatching { decodeImageProxy(image) }.getOrNull()
                    image.close()
                    deferred.complete(bmp)
                }

                override fun onError(exception: ImageCaptureException) {
                    SafeLogger.warn("CameraX2Engine", "ImageCapture error: ${exception.imageCaptureError}")
                    deferred.complete(null)
                }
            },
        )
        return deferred.await()
    }

    private fun decodeImageProxy(image: ImageProxy): Bitmap? {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        val isFront = getCameraCapabilities().availableLenses
            .firstOrNull { it.cameraId == activeCameraId }?.isFrontFacing == true

        if (rotation == 0 && !isFront) return raw
        val matrix = Matrix().apply {
            if (rotation != 0) postRotate(rotation.toFloat())
            if (isFront) postScale(-1f, 1f)
        }
        val transformed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (transformed !== raw) raw.recycle()
        return transformed
    }

    @SuppressLint("MissingPermission")
    override suspend fun startVideoRecord(config: VideoConfig) {
        if (!videoModeEnabled || videoCaptureUseCase == null) {
            setVideoModeEnabled(true)
        }
        val videoCapture = videoCaptureUseCase ?: return
        if (activeRecording != null) return

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "OPHOTO_VID_${System.currentTimeMillis()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Ophoto")
            }
        }
        val mediaOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()

        val finishDeferred = CompletableDeferred<VideoResult>()
        videoFinishDeferred = finishDeferred
        videoStartMillis = System.currentTimeMillis()

        val pending = videoCapture.output.prepareRecording(context, mediaOptions).let {
            if (config.enableAudio) it.withAudioEnabled() else it
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                val dur = (System.currentTimeMillis() - videoStartMillis).coerceAtLeast(0L)
                val uriStr = event.outputResults.outputUri.takeIf { it != android.net.Uri.EMPTY }?.toString()
                finishDeferred.complete(
                    VideoResult(
                        savedUriString = uriStr,
                        durationMillis = dur,
                        errorMessage = if (event.hasError()) "录像结束异常 (${event.error})" else null,
                    ),
                )
            }
        }
    }

    override suspend fun stopVideoRecord(): VideoResult {
        val rec = activeRecording ?: return VideoResult(null, 0L, null)
        val deferred = videoFinishDeferred
        activeRecording = null
        rec.stop()
        return deferred?.await() ?: VideoResult(null, 0L, null)
    }

    override fun setExposure(exposure: ExposureConfig) {
        currentExposure = exposure
        applyExposureCompensationIndex(exposure)
        applyCamera2CaptureOptionsAsync()
        val cur = _liveTelemetryFlow.value
        val (resolvedIso, resolvedNs) = if (exposure.isAnyManual) {
            exposure.resolveManualParameters(ambientMeteredIso, ambientMeteredExposureNs)
        } else {
            ambientMeteredIso to ambientMeteredExposureNs
        }
        _liveTelemetryFlow.value = cur.copy(
            iso = resolvedIso,
            shutterSeconds = resolvedNs / 1_000_000_000.0,
            exposureDeltaEv = exposure.estimateExposureDeltaStops(ambientMeteredIso, ambientMeteredExposureNs),
        )
    }

    override fun setFocus(focus: FocusConfig) {
        currentFocus = focus
        val cam = activeCamera ?: return
        if (!focus.manualFocus && focus.normalizedTapX != null && focus.normalizedTapY != null) {
            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
            val point = factory.createPoint(
                focus.normalizedTapX.coerceIn(0f, 1f),
                focus.normalizedTapY.coerceIn(0f, 1f),
            )
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
            ).build()
            cam.cameraControl.startFocusAndMetering(action)
        }
        applyCamera2CaptureOptionsAsync()
    }

    override fun setWhiteBalance(wb: WhiteBalanceConfig) {
        currentWb = wb
        applyCamera2CaptureOptionsAsync()
    }

    override fun setZoomRatio(zoom: Float) {
        pendingZoomRatio = zoom
        val cam = activeCamera ?: return
        val zoomState = cam.cameraInfo.zoomState.value
        val minZ = zoomState?.minZoomRatio ?: 1f
        val maxZ = zoomState?.maxZoomRatio ?: 10f
        val clamped = zoom.coerceIn(minZ, maxZ)
        cam.cameraControl.setZoomRatio(clamped)
    }

    override fun setFilter(filter: FilterConfig) {
        currentFilter = filter
    }

    override fun getCameraCapabilities(): CameraCapabilities {
        val lenses = OppoDeviceInspector.enumerateAvailableLenses(context)
        val activeSpec = lenses.firstOrNull { it.cameraId == activeCameraId } ?: lenses.firstOrNull()
        val zoomState = activeCamera?.cameraInfo?.zoomState?.value
        val minZoom = zoomState?.minZoomRatio ?: 0.6f
        val maxZoom = zoomState?.maxZoomRatio ?: 10f
        return CameraCapabilities(
            availableLenses = lenses,
            activeCameraId = activeSpec?.cameraId ?: activeCameraId,
            isoRange = activeSpec?.isoRange ?: (50..12800),
            shutterNsRange = activeSpec?.exposureNsRange ?: (125_000L..30_000_000_000L),
            evRange = -2.0f..2.0f,
            maxFocusDiopters = (activeSpec?.minFocusDistanceDiopters ?: 10f).coerceAtLeast(1f),
            zoomRange = minZoom..maxZoom,
            supportsRaw = activeSpec?.supportsRaw == true,
        )
    }

    private fun buildSelectorForCameraId(cameraId: String): CameraSelector {
        return CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                val matched = cameraInfos.filter { info ->
                    runCatching { Camera2CameraInfo.from(info).cameraId == cameraId }.getOrDefault(false)
                }
                matched.ifEmpty { cameraInfos }
            }
            .build()
    }

    private fun applyExposureCompensationIndex(exposure: ExposureConfig): ListenableFuture<Int>? {
        val cam = activeCamera ?: return null
        val state = cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return null
        val targetEv = if (exposure.manualIso && exposure.manualShutter) 0f else exposure.evCompensation
        val step = state.exposureCompensationStep.toFloat().coerceAtLeast(0.1f)
        val index = (targetEv / step).roundToInt()
            .coerceIn(state.exposureCompensationRange.lower, state.exposureCompensationRange.upper)
        return cam.cameraControl.setExposureCompensationIndex(index)
    }

    private fun applyCamera2CaptureOptionsAsync(): ListenableFuture<Void>? {
        val cam = activeCamera ?: return null
        val cam2Control = runCatching { Camera2CameraControl.from(cam.cameraControl) }.getOrNull() ?: return null
        val caps = getCameraCapabilities()

        val usesHardwareWbPreset = currentWb.mode != WhiteBalanceMode.AUTO &&
            currentWb.mode != WhiteBalanceMode.MANUAL
        val needsCustomRequest = currentExposure.isAnyManual ||
            currentExposure.aeLocked ||
            currentFocus.manualFocus ||
            usesHardwareWbPreset ||
            currentWb.awbLocked

        // When in standard Auto AE + AF + AWB (or GPU-graded Manual Kelvin), clear all Camera2
        // overrides so OEM CameraX 3A / Flash / HDR exposure runs at full native sensor brightness!
        if (!needsCustomRequest) {
            return cam2Control.clearCaptureRequestOptions()
        }

        val optionsBuilder = CaptureRequestOptions.Builder()

        // 1. Exposure: Manual / Semi-Auto Reciprocity vs AE Lock
        if (currentExposure.isAnyManual) {
            val (resolvedIso, resolvedNs) = currentExposure.resolveManualParameters(
                meteredIso = ambientMeteredIso,
                meteredExposureNs = ambientMeteredExposureNs,
                isoRange = caps.isoRange,
                shutterNsRange = caps.shutterNsRange,
            )
            val frameDurationNs = maxOf(resolvedNs, 33_333_333L)
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_OFF,
            )
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, resolvedIso)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, resolvedNs)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, frameDurationNs)
        } else if (currentExposure.aeLocked) {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
        }

        // 2. Focus: Manual Diopters vs Native Continuous/Tap AF
        if (currentFocus.manualFocus) {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF,
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                currentFocus.focusDistanceDiopters.coerceIn(0f, caps.maxFocusDiopters),
            )
        }

        // 3. Hardware Scene AWB Presets (Factory-calibrated ISP CCM; Manual Kelvin uses GPU/CPU WB shift
        //    so sensor channels are never starved without a custom ColorSpaceTransform)
        when (currentWb.mode) {
            WhiteBalanceMode.AUTO, WhiteBalanceMode.MANUAL -> {
                if (currentWb.awbLocked) {
                    optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                }
            }
            WhiteBalanceMode.DAYLIGHT -> optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
            )
            WhiteBalanceMode.CLOUDY -> optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
            )
            WhiteBalanceMode.INCANDESCENT -> optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT,
            )
            WhiteBalanceMode.FLUORESCENT -> optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT,
            )
            WhiteBalanceMode.SHADE -> optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_SHADE,
            )
        }

        return cam2Control.setCaptureRequestOptions(optionsBuilder.build())
    }

    companion object {
        fun kelvinToRggbVector(kelvin: Int): RggbChannelVector {
            val temp = (kelvin.coerceIn(2500, 10000)) / 100.0
            val red = if (temp <= 66.0) 1.0 else (1.292936 * (temp - 60.0).pow(-0.13320476)).coerceIn(0.4, 2.5)
            val green = if (temp <= 66.0) {
                (0.39008158 * ln(temp) - 0.63184144).coerceIn(0.4, 2.5)
            } else {
                (1.1298909 * (temp - 60.0).pow(-0.07551485)).coerceIn(0.4, 2.5)
            }
            val blue = when {
                temp >= 66.0 -> 1.0
                temp <= 19.0 -> 0.4
                else -> (0.54320679 * ln(temp - 10.0) - 1.1962541).coerceIn(0.4, 2.5)
            }
            val normG = green.coerceAtLeast(0.01)
            return RggbChannelVector(
                (1.0 / (red / normG)).toFloat().coerceIn(0.5f, 3.2f),
                1.0f,
                1.0f,
                (1.0 / (blue / normG)).toFloat().coerceIn(0.5f, 3.2f),
            )
        }
    }
}
