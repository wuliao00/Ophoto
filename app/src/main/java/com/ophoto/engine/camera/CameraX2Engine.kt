package com.ophoto.engine.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
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
import com.ophoto.core.device.OppoDeviceInspector
import com.ophoto.core.log.SafeLogger
import com.ophoto.engine.filter.FilterConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.ln

/**
 * Production CameraX + Camera2Interop Engine (Doc §4.1, §5, §8):
 * - Uses CameraX lifecycle stability with full Camera2 CaptureRequest overrides for Pro ISO,
 *   Shutter Speed (ns), Manual Focus diopters, Kelvin RGB channel gains, AE/AWB locks, and multi-lens selection.
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
    private var currentExposure = ExposureConfig()
    private var currentFocus = FocusConfig()
    private var currentWb = WhiteBalanceConfig()
    private var currentFilter = FilterConfig(null, 1f)

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
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = surfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.UHD, Quality.FHD, Quality.HD),
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)

        provider.unbindAll()
        activeCamera = null
        imageCaptureUseCase = null
        videoCaptureUseCase = null
        // Some devices cannot support preview + still + video simultaneously. Keep
        // photography usable on those devices instead of silently showing a black preview.
        var videoAvailable = true
        val camera = try {
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
        } catch (first: Exception) {
            SafeLogger.warn("CameraX2Engine", "Video combination unsupported; retrying photo session")
            provider.unbindAll()
            videoAvailable = false
            try {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (second: Exception) {
                SafeLogger.error("CameraX2Engine", "Failed to bind camera session", second)
                throw second
            }
        }
        activeCamera = camera
        previewUseCase = preview
        imageCaptureUseCase = imageCapture
        videoCaptureUseCase = if (videoAvailable) videoCapture else null
        // Apply the requested compensation after binding; setting it before a camera
        // exists only stores the value. Do not apply it again in a preview shader.
        setExposure(currentExposure)
        Unit
    }

    override suspend fun openCamera(cameraId: String) {
        activeCameraId = cameraId
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

        capture.flashMode = when (config.flashMode) {
            FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
            FlashSetting.TORCH -> ImageCapture.FLASH_MODE_OFF
        }
        activeCamera?.cameraControl?.enableTorch(config.flashMode == FlashSetting.TORCH)

        setExposure(config.exposure)
        setFocus(config.focus)
        setWhiteBalance(config.whiteBalance)

        val framesToCapture = when {
            config.bracketStops -> 3
            config.burstCount > 1 -> config.burstCount.coerceIn(2, 10)
            else -> 1
        }

        var primaryBitmap: Bitmap? = null
        var successFrames = 0
        val bracketOffsets = listOf(-2f, 0f, 2f)

        for (index in 0 until framesToCapture) {
            if (config.bracketStops) {
                setExposure(config.exposure.copy(evCompensation = bracketOffsets[index]))
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
            setExposure(config.exposure)
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
        val videoCapture = videoCaptureUseCase ?: throw IllegalStateException("当前设备不支持此录像组合")
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
        val cam = activeCamera ?: return
        val state = cam.cameraInfo.exposureState
        if (state.isExposureCompensationSupported && !exposure.manualMode) {
            val index = exposureCompensationIndex(
                exposure.evCompensation, state.exposureCompensationStep.toFloat(),
                state.exposureCompensationRange.lower, state.exposureCompensationRange.upper,
            )
            cam.cameraControl.setExposureCompensationIndex(index)
        }
        applyCamera2CaptureOptions()
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
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
        } else {
            applyCamera2CaptureOptions()
        }
    }

    override fun setWhiteBalance(wb: WhiteBalanceConfig) {
        currentWb = wb
        applyCamera2CaptureOptions()
    }

    override fun setZoomRatio(zoom: Float) {
        val cam = activeCamera ?: return
        val zoomState = cam.cameraInfo.zoomState.value
        val clamped = zoom.coerceIn(
            zoomState?.minZoomRatio ?: 1f,
            zoomState?.maxZoomRatio ?: 8f,
        )
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
            isoRange = activeSpec?.isoRange ?: (100..6400),
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

    private fun applyCamera2CaptureOptions() {
        val cam = activeCamera ?: return
        val cam2Control = runCatching { Camera2CameraControl.from(cam.cameraControl) }.getOrNull() ?: return
        val caps = getCameraCapabilities()

        val optionsBuilder = CaptureRequestOptions.Builder()

        // 1. Exposure (AE vs Manual ISO + Shutter ns)
        if (currentExposure.manualMode) {
            val iso = currentExposure.iso.coerceIn(caps.isoRange.first, caps.isoRange.last)
            val ns = (currentExposure.shutterSeconds * 1_000_000_000.0).toLong()
                .coerceIn(caps.shutterNsRange.first, caps.shutterNsRange.last)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, ns)
        } else if (currentExposure.aeLocked) {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
        }

        // 2. Focus (AF Continuous Picture vs Manual Focus Diopter)
        if (currentFocus.manualFocus) {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                currentFocus.focusDistanceDiopters.coerceIn(0f, caps.maxFocusDiopters),
            )
        }

        // 3. White Balance (Auto, Scene Presets, or Manual Kelvin RGGB vector)
        when (currentWb.mode) {
            WhiteBalanceMode.AUTO -> {
                if (currentWb.awbLocked) optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
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
            WhiteBalanceMode.MANUAL -> {
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_GAINS,
                    kelvinToRggbVector(currentWb.manualKelvin),
                )
            }
        }

        cam2Control.setCaptureRequestOptions(optionsBuilder.build())
    }

    companion object {
        /**
         * Planckian locus approximation mapping color temperature (2500K..10000K) to Camera2 RGGB gains.
         */
        fun kelvinToRggbVector(kelvin: Int): RggbChannelVector {
            val temp = (kelvin.coerceIn(2500, 10000)) / 100.0
            val red = if (temp <= 66.0) 1.0 else (1.292936 * Math.pow(temp - 60.0, -0.13320476)).coerceIn(0.4, 2.5)
            val green = if (temp <= 66.0) {
                (0.39008158 * ln(temp) - 0.63184144).coerceIn(0.4, 2.5)
            } else {
                (1.1298909 * Math.pow(temp - 60.0, -0.07551485)).coerceIn(0.4, 2.5)
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
