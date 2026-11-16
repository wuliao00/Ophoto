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
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
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

    /** 最近一次绑定结果（会话能力 + 降级原因），供设置页与 HUD 诊断展示。 */
    var lastBinding: CameraBindingResult? = null
        private set

    /** 最近一次失败的绑定原因（用于抛给 UI 人类可读文本）。 */
    private var lastBindError: String? = null

    /** 真实预览分辨率。 */
    var previewFormat: PreviewFormatInfo = PreviewFormatInfo()
        private set

    /** 最近一次成功下发的 EV（索引, 步长），用于在 HUD 中还原「实际生效」的补偿值。 */
    var lastEvApplied: Pair<Int, Float>? = null
        private set

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

        // 预览用例：优先 4:3 的取景比率，与拍照输出一致，避免观感剪刀差。
        fun buildPreview(): Preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { it.surfaceProvider = surfaceProvider }

        // 注意：CameraX 不允许同时设置 target aspect ratio 与 target resolution。
        fun buildImageCapture(highRes: Boolean): ImageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .apply {
                if (highRes) {
                    setTargetResolution(Size(4000, 3000))
                } else {
                    setTargetAspectRatio(AspectRatio.RATIO_4_3)
                }
            }
            .build()

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
        )
        val videoCapture = VideoCapture.withOutput(
            Recorder.Builder().setQualitySelector(qualitySelector).build(),
        )

        // 逐级降级绑定：预览必须是第一优先级，宁可放弃录像/高分辨率也不能黑屏。
        var preview = buildPreview()
        var imageCapture = buildImageCapture(highRes = true)
        var bound: Camera? = null
        var videoAvailable = false
        var highResPhoto = true
        var degradedReason: String? = null

        val attempts: List<Pair<String, suspend () -> Camera>> = listOf(
            "UHD_PHOTO_AND_VIDEO" to {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
            },
            "PHOTO_AND_VIDEO" to {
                provider.unbindAll()
                preview = buildPreview()
                imageCapture = buildImageCapture(highRes = false)
                highResPhoto = false
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
            },
            "PHOTO_ONLY" to {
                provider.unbindAll()
                preview = buildPreview()
                imageCapture = buildImageCapture(highRes = false)
                highResPhoto = false
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            },
            "PREVIEW_ONLY" to {
                provider.unbindAll()
                preview = buildPreview()
                imageCapture = buildImageCapture(highRes = false)
                highResPhoto = false
                provider.bindToLifecycle(lifecycleOwner, selector, preview)
            },
        )

        for ((label, attempt) in attempts) {
            try {
                bound = attempt()
                videoAvailable = label != "PHOTO_ONLY" && label != "PREVIEW_ONLY"
                if (label != "UHD_PHOTO_AND_VIDEO" && degradedReason == null) {
                    degradedReason = "已降级为 $label 模式"
                }
                SafeLogger.info("CameraX2Engine", "bind ok: $label lens=$cameraId")
                break
            } catch (e: Exception) {
                lastBindError = describeCameraBindingFailure(e)
                if (degradedReason == null) {
                    degradedReason = lastBindError
                }
                SafeLogger.warn("CameraX2Engine", "bind attempt $label failed: ${e.javaClass.simpleName}")
            }
        }

        val camera = bound
        if (camera == null) {
            SafeLogger.error("CameraX2Engine", "all bind attempts failed for lens $cameraId")
            throw IllegalStateException(lastBindError ?: "无法启动相机预览")
        }

        activeCamera = camera
        previewUseCase = preview
        imageCaptureUseCase = imageCapture
        videoCaptureUseCase = if (videoAvailable) videoCapture else null
        lastBinding = CameraBindingResult(
            sessionLabel = if (videoAvailable) "照片 + 录像" else "仅照片",
            photoAvailable = true,
            videoAvailable = videoAvailable,
            highResolutionPhoto = highResPhoto,
            degradedReason = degradedReason,
        )

        // 预览格式信息：让 HUD 显示真实的取景分辨率，而不是猜。
        runCatching {
            val resolution = preview.resolutionInfo?.resolution
            previewFormat = PreviewFormatInfo(
                width = resolution?.width ?: 0,
                height = resolution?.height ?: 0,
                sessionLabel = lastBinding?.sessionLabel.orEmpty(),
                highResolutionPhoto = highResPhoto,
            )
        }

        // 绑定时让 AE 处于正常状态：清掉上一次可能遗留的 AE 锁定/手动曝光。
        runCatching { camera.cameraControl.enableTorch(false) }
        applyPreviewExposureGuard(camera)
        setExposure(currentExposure)
        Unit
    }

    /**
     * 预览亮度护栏：ColorOS 家族在预览流上更容易被自定义 Camera2 参数带偏
     * （典型是进入专业模式后 AE 被锁、或残留 AWB/AE 手动值导致整屏石灰黑）。
     * 这里显式把预览会话的 AE/AWB 恢复为自动，只有在用户主动锁定或手动模式时才例外。
     */
    private fun applyPreviewExposureGuard(camera: Camera) {
        val control = runCatching { Camera2CameraControl.from(camera.cameraControl) }.getOrNull() ?: return
        val builder = CaptureRequestOptions.Builder()
        if (currentExposure.manualMode) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, currentExposure.aeLocked)
        }
        if (currentWb.mode == WhiteBalanceMode.MANUAL) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
        } else if (!currentWb.awbLocked) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
        }
        runCatching { control.setCaptureRequestOptions(builder.build()) }
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

        // 拍照结束后恢复取景曝光：预览提亮 EV 与成片 EV 解耦，保证成片按用户设定曝光。
        setExposure(config.previewExposure ?: config.exposure)
        imageCaptureUseCase?.flashMode = ImageCapture.FLASH_MODE_OFF
        if (config.flashMode != FlashSetting.TORCH) {
            activeCamera?.cameraControl?.enableTorch(false)
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
            lastEvApplied = index to state.exposureCompensationStep.toFloat()
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
        val clamped = clampZoomRatio(zoom, zoomState?.minZoomRatio, zoomState?.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    /** 当前生效的实际 EV（考虑设备步长与限幅），用于 HUD 显示真实补偿值。 */
    override fun effectiveEv(): Float? {
        val cam = activeCamera ?: return null
        val state = cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return null
        val step = state.exposureCompensationStep.toFloat()
        val current = lastEvApplied?.first
            ?: currentExposure.evCompensation.let { ev ->
                exposureCompensationIndex(ev, step, state.exposureCompensationRange.lower, state.exposureCompensationRange.upper)
            }
        val clamped = current.coerceIn(state.exposureCompensationRange.lower, state.exposureCompensationRange.upper)
        return clamped * step
    }

    override fun resetExposureBaseline() {
        currentExposure = ExposureConfig()
        val cam = activeCamera ?: return
        runCatching { cam.cameraControl.setExposureCompensationIndex(0) }
        applyPreviewExposureGuard(cam)
        setExposure(currentExposure)
    }

    /** 取景亮度遥测：单帧 32×32 YUV 统计，不改动当前预览会话。 */
    override suspend fun probeViewfinderBrightness(): MeteringSample? = withContext(Dispatchers.IO) {
        if (!ViewfinderMeterProbe.canProbe(context)) return@withContext null
        // 探针需要独占相机，因此先释放 CameraX 会话、采样后再重新绑定预览。
        val owner = lastLifecycleOwner
        val surface = lastSurfaceProvider
        withContext(Dispatchers.Main) { runCatching { cameraProvider?.unbindAll() } }
        val sample = ViewfinderMeterProbe.probe(context, activeCameraId)
        if (owner != null && surface != null) {
            withContext(Dispatchers.Main) {
                runCatching { bindSession(owner, surface, activeCameraId) }
            }
        }
        sample
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

    /** 当前绑定会话能力（供设置页体检卡显示）。 */
    fun currentBinding(): CameraBindingResult? = lastBinding

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
        // 注意：Camera2Interop 下发的 CONTROL_AE_MODE=OFF 会在会话存续期间一直生效。
        // 从专业手动切回自动时必须显式写回 AE_MODE_ON，否则取景会停在旧的 ISO/快门上
        // ——这正是「退出专业模式后取景框还是黑」的典型根因。
        if (currentExposure.manualMode) {
            val iso = currentExposure.iso.coerceIn(caps.isoRange.first, caps.isoRange.last)
            val ns = (currentExposure.shutterSeconds * 1_000_000_000.0).toLong()
                .coerceIn(caps.shutterNsRange.first, caps.shutterNsRange.last)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, ns)
        } else {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, currentExposure.aeLocked)
        }

        // 2. Focus (AF Continuous Picture vs Manual Focus Diopter)
        if (currentFocus.manualFocus) {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                currentFocus.focusDistanceDiopters.coerceIn(0f, caps.maxFocusDiopters),
            )
        } else {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
        }

        // 3. White Balance (Auto, Scene Presets, or Manual Kelvin RGGB vector)
        when (currentWb.mode) {
            WhiteBalanceMode.AUTO -> {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_AUTO,
                )
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, currentWb.awbLocked)
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
            // Tanner Helland 黑体轨迹近似：RGB → 反向增益，输出归一化 RGGB。
            val temp = (kelvin.coerceIn(2500, 10000)) / 100.0
            val red = if (temp <= 66.0) {
                255.0
            } else {
                (329.698727446 * Math.pow(temp - 60.0, -0.1332047592)).coerceIn(0.0, 255.0)
            }
            val green = if (temp <= 66.0) {
                (99.4708025861 * ln(temp) - 161.1195681661).coerceIn(0.0, 255.0)
            } else {
                (288.1221695283 * Math.pow(temp - 60.0, -0.0755148492)).coerceIn(0.0, 255.0)
            }
            val blue = when {
                temp >= 66.0 -> 255.0
                temp <= 19.0 -> 0.0
                else -> (138.5177312231 * ln(temp - 10.0) - 305.0447927307).coerceIn(0.0, 255.0)
            }
            // 反向增益：色温越低画面越暖，需要更强的蓝通道增益来中和。
            fun inverse(channel: Double): Float =
                (255.0 / channel.coerceAtLeast(1.0)).toFloat().coerceIn(0.25f, 4.0f)
            val r = inverse(red)
            val g = 1.0f
            val b = inverse(blue)
            // 保证整体不改变画面平均亮度：绿通道不动，红/蓝围绕 1.0 平衡。
            return RggbChannelVector(r, g, g, b)
        }
    }
}
