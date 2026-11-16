package com.ophoto.ui.camera

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ophoto.OphotoContainer
import com.ophoto.core.permission.CameraPermissionHelper
import com.ophoto.core.storage.ExportImageFormat
import com.ophoto.core.storage.MediaStoreSaver
import com.ophoto.data.local.CompositionGuideType
import com.ophoto.data.local.UserSettings
import com.ophoto.data.local.ViewfinderBrightnessMode
import com.ophoto.data.local.VolumeKeyAction
import com.ophoto.data.local.entity.CaptureRecordEntity
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.engine.camera.CameraCaptureMode
import com.ophoto.engine.camera.CaptureConfig
import com.ophoto.engine.camera.ExposureConfig
import com.ophoto.engine.camera.FlashSetting
import com.ophoto.engine.camera.FocusConfig
import com.ophoto.engine.camera.MeteringMode
import com.ophoto.engine.camera.VideoConfig
import com.ophoto.engine.camera.ViewfinderAspectRatio
import com.ophoto.engine.camera.WhiteBalanceConfig
import com.ophoto.engine.camera.WhiteBalanceMode
import com.ophoto.engine.camera.formatZoomLabel
import com.ophoto.engine.filter.FilterPreset
import com.ophoto.engine.metadata.MetadataEngine
import com.ophoto.ui.theme.AlertCrimson
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.GlowBrass
import com.ophoto.ui.theme.GlowOrange
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.OphotoCard
import com.ophoto.ui.theme.OphotoChip
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.ShutterOrange
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class ProDialTab(val label: String) {
    EV("EV"),
    ISO("ISO"),
    SHUTTER("快门 S"),
    WB("色温 WB"),
    FOCUS("对焦 MF"),
    METERING("测光"),
}

private val ISO_STOPS = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400, 12800)
private val SHUTTER_STOPS = listOf(
    1.0 / 8000.0,
    1.0 / 4000.0,
    1.0 / 2000.0,
    1.0 / 1000.0,
    1.0 / 500.0,
    1.0 / 250.0,
    1.0 / 125.0,
    1.0 / 60.0,
    1.0 / 30.0,
    1.0 / 15.0,
    0.25,
    1.0,
    4.0,
    15.0,
    30.0,
)

/** 预设的变焦档位（等效主摄/超广/中焦/长焦）。 */
private val ZOOM_PRESETS = listOf(0.6f, 1.0f, 2.0f, 3.0f, 5.0f)

@Composable
fun CameraScreen(
    container: OphotoContainer,
    onJumpToEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val host = container.viewfinderHost

    val settings by container.preferences.settingsFlow.collectAsState(initial = UserSettings())
    val presets by container.filterRepository.allPresetsFlow.collectAsState(initial = BuiltInPresets.ALL)

    var hasCameraPerm by remember { mutableStateOf(CameraPermissionHelper.hasCameraPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantedMap ->
        hasCameraPerm = grantedMap[android.Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) {
            permLauncher.launch(CameraPermissionHelper.requiredCameraPermissions(includeAudio = false).toTypedArray())
        }
    }

    var captureMode by remember { mutableStateOf(CameraCaptureMode.PHOTO) }
    var flashSetting by remember { mutableStateOf(FlashSetting.OFF) }
    val aspectRatio = ViewfinderAspectRatio.RATIO_4_3
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var burstCount by remember { mutableIntStateOf(1) }
    var bracketMode by remember { mutableStateOf(false) }
    var showFilterTray by remember { mutableStateOf(false) }
    var showBrightnessPanel by remember { mutableStateOf(false) }
    var activeProDial by remember { mutableStateOf(ProDialTab.EV) }

    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    var filterStrength by remember { mutableFloatStateOf(settings.defaultFilterStrength) }

    /**
     * 曝光解耦：userExposure 决定成片，previewExposure 决定取景观感。
     * 之前两者共用同一 EV，导致「为了看亮而提亮预览」会污染成片曝光；
     * 现在起 previewExposure 只是显示层的 EV，拍完会自动复位。
     */
    var userExposure by remember {
        mutableStateOf(ExposureConfig(manualMode = false, iso = 100, shutterSeconds = 1.0 / 125.0, evCompensation = 0f))
    }
    var brightnessMode by remember { mutableStateOf(settings.viewfinderBrightness) }
    var displayLift by remember { mutableFloatStateOf(0f) }
    var manualBoost by remember { mutableFloatStateOf(0f) }
    // 用户在亮度面板里点过「手动增益」或方案后，自适应不再和手动值打架。
    var autoLiftEnabled by remember { mutableStateOf(true) }
    var manualBoostTouched by remember { mutableStateOf(false) }
    var focusConfig by remember { mutableStateOf(FocusConfig()) }
    var wbConfig by remember { mutableStateOf(WhiteBalanceConfig()) }
    var activeCameraId by remember {
        mutableStateOf(container.hardwareProfile.lenses.firstOrNull { !it.isFrontFacing }?.cameraId ?: "0")
    }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var activeFieldBanner by remember { mutableStateOf<String?>(null) }

    // 取景 View 走共享宿主：切页返回时复用同一 PreviewView，不再重建会话。
    val previewView = remember(context, host) {
        host.previewView ?: PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }.also { host.previewView = it }
    }
    var previewStreaming by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var bindingLabel by remember { mutableStateOf("") }
    var previewResolution by remember { mutableStateOf("") }

    // 真实帧采样：亮度、直方图、斑马纹都只用真实预览帧，不再靠「假设画面正常」。
    val sampler = remember { PreviewSampler() }
    var frameLuma by remember { mutableStateOf<FrameLuma?>(null) }
    var liveHistogram by remember { mutableStateOf<com.ophoto.engine.filter.HistogramData?>(null) }
    var capturedFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val previewExposure = remember(userExposure, brightnessMode) {
        userExposure.copy(evCompensation = (brightnessMode.evOffset + userExposure.evCompensation).coerceIn(-3f, 3f))
    }

    DisposableEffect(previewView, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { state ->
            previewStreaming = state == PreviewView.StreamState.STREAMING
            host.setStreaming(previewStreaming)
            if (previewStreaming) {
                previewError = null
                host.sessionReady = true
            }
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }

    LaunchedEffect(hasCameraPerm, activeCameraId, retryCount) {
        if (!hasCameraPerm) return@LaunchedEffect
        previewStreaming = false
        previewError = null
        try {
            container.cameraEngine.bindSession(lifecycleOwner, previewView.surfaceProvider, activeCameraId)
            container.cameraEngine.setExposure(previewExposure)
            container.cameraEngine.setZoomRatio(zoomRatio)
            container.cameraEngine.setWhiteBalance(wbConfig)
            val engine = container.cameraEngine as? com.ophoto.engine.camera.CameraX2Engine
            bindingLabel = engine?.lastBinding?.sessionLabel.orEmpty()
            previewResolution = engine?.previewFormat?.display.orEmpty()
            delay(9000)
            if (!host.streaming.value) {
                previewError = "相机未输出画面，请点重试；若系统相机也黑屏，请检查镜头是否被遮挡"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            previewError = e.message?.takeIf { it.isNotBlank() } ?: "无法启动相机，请检查权限或关闭其他相机应用"
            com.ophoto.core.log.SafeLogger.error("CameraScreen", "bind failed", e)
        }
    }

    // 切换亮度方案 / 深度手动提亮时，把预览 EV 同步给引擎（成片不受影响）。
    LaunchedEffect(previewExposure) {
        if (hasCameraPerm) container.cameraEngine.setExposure(previewExposure)
    }

    // ~1Hz 真实帧分析：亮度自适应提亮 + 直方图刷新（斑马纹只在开启时才取帧）。
    LaunchedEffect(hasCameraPerm, previewStreaming, settings.showHistogram, brightnessMode) {
        while (hasCameraPerm && previewStreaming) {
            delay(650)
            val frame = withContext(Dispatchers.Default) {
                runCatching { previewView.bitmap }.getOrNull()
            }
            if (frame != null) {
                frameLuma = withContext(Dispatchers.Default) { sampler.sample(frame) }
                if (settings.showHistogram) {
                    liveHistogram = withContext(Dispatchers.Default) { sampler.histogram(frame) }
                }
                if (!settings.showZebraStripes) {
                    // 斑马纹关闭时立即释放引用，避免持有整帧位图。
                    capturedFrame = null
                } else {
                    capturedFrame = frame
                }
            }
        }
    }

    // 自适应提亮：画面暗就抬亮，恢复后自动回落；手动增益优先，不再叠乘避免反复跳动。
    LaunchedEffect(frameLuma, brightnessMode, manualBoost, manualBoostTouched, autoLiftEnabled) {
        val autoLift = if (autoLiftEnabled && !manualBoostTouched) {
            PreviewSampler.requiredLift(frameLuma) * brightnessMode.autoLiftScale
        } else {
            0f
        }
        val target = if (manualBoostTouched) manualBoost else (autoLift + manualBoost).coerceIn(0f, 0.40f)
        // 平滑过渡，避免忽明忽暗
        displayLift = displayLift * 0.4f + target.coerceIn(0f, 0.45f) * 0.6f
    }

    val activePreset: FilterPreset? = remember(presets, selectedPresetId) {
        presets.firstOrNull { it.id == selectedPresetId }
    }
    val activeLut = remember(activePreset?.id, activePreset?.lutPath) {
        activePreset?.let { preset ->
            container.filterEngine.loadFilter(preset)
            container.filterEngine.currentLut() ?: BuiltInPresets.buildCubeLutForPreset(preset, 17)
        }
    }

    // Receive 1-click camera preset/composition setup from On-Site Photography Assistant
    LaunchedEffect(container) {
        container.fieldSetupCommands.collect { cmd ->
            selectedPresetId = cmd.presetId
            userExposure = userExposure.copy(manualMode = false, evCompensation = cmd.evOffset)
            wbConfig = WhiteBalanceConfig(mode = WhiteBalanceMode.MANUAL, manualKelvin = cmd.kelvin)
            zoomRatio = when {
                cmd.focalMmHint <= 18 -> 0.6f
                cmd.focalMmHint in 35..58 -> 2.0f
                cmd.focalMmHint >= 59 -> 3.0f
                else -> 1.0f
            }
            container.preferences.update { it.copy(compositionGuide = cmd.compositionGuide) }
            activeFieldBanner = cmd.tipBanner
        }
    }

    var lastCapturedThumb by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val rollDegrees = rememberDeviceRollDegrees(enabled = settings.showLeveler)
    val animatedRoll by animateFloatAsState(targetValue = rollDegrees, animationSpec = tween(220, easing = FastOutSlowInEasing), label = "rollSmooth")
    var shutterFlash by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(targetValue = if (shutterFlash) 0.92f else 0f, animationSpec = tween(if (shutterFlash) 80 else 420), label = "flash")
    val shutterScale by animateFloatAsState(targetValue = if (shutterFlash) 0.90f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f), label = "shutterScale")

    /** 一键修复黑屏：把 AE 拉回自动、清空手动 ISO/快门/EV、清空 WB 锁定。 */
    fun rescueExposure() {
        scope.launch {
            container.cameraEngine.resetExposureBaseline()
            userExposure = ExposureConfig()
            wbConfig = WhiteBalanceConfig()
            focusConfig = FocusConfig()
            manualBoost = 0f
            brightnessMode = ViewfinderBrightnessMode.BRIGHT
            container.cameraEngine.setWhiteBalance(wbConfig)
            container.cameraEngine.setFocus(focusConfig)
            delay(350)
            retryCount++
            Toast.makeText(context, "已恢复自动 AE / AWB 并提亮取景", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerCapture() {
        scope.launch {
            if (!isRecordingVideo && !hasCameraPerm) {
                permLauncher.launch(CameraPermissionHelper.requiredCameraPermissions(includeAudio = false).toTypedArray())
                return@launch
            }
            if (!isRecordingVideo && !previewStreaming) {
                Toast.makeText(context, "相机尚未就绪，请等待或点「重试」", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (captureMode == CameraCaptureMode.VIDEO) {
                if (isRecordingVideo) {
                    val videoRes = container.cameraEngine.stopVideoRecord()
                    isRecordingVideo = false
                    host.recordingVideo = false
                    Toast.makeText(
                        context,
                        videoRes.errorMessage ?: "视频已保存 (${videoRes.durationMillis / 1000}s)",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    try {
                        container.cameraEngine.startVideoRecord(
                            VideoConfig(
                                qualityTag = settings.videoResolutionTag,
                                enableAudio = CameraPermissionHelper.hasAudioPermission(context),
                                stabilization = settings.videoStabilization,
                            ),
                        )
                        isRecordingVideo = true
                        host.recordingVideo = true
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "无法开始录像", Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            if (timerSeconds > 0) {
                for (sec in timerSeconds downTo 1) {
                    countdownRemaining = sec
                    delay(1000L)
                }
                countdownRemaining = 0
            }

            val config = CaptureConfig(
                mode = captureMode,
                flashMode = flashSetting,
                aspectRatio = aspectRatio,
                enableRaw = settings.enableRawWhenSupported,
                exposure = userExposure,
                focus = focusConfig,
                whiteBalance = wbConfig,
                filterId = selectedPresetId,
                filterStrength = filterStrength,
                timerSeconds = timerSeconds,
                burstCount = burstCount,
                bracketStops = bracketMode,
                previewExposure = previewExposure,
            )

            val photoResult = container.cameraEngine.takePhoto(config)
            val rawCapture = photoResult.bitmap ?: run {
                Toast.makeText(context, photoResult.errorMessage ?: "拍摄失败，请重试", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val finalBitmap = withContext(Dispatchers.Default) {
                val preset = activePreset
                if (preset != null && hasCameraPerm) {
                    container.filterEngine.loadFilterWithExplicitLut(preset, activeLut)
                    container.filterEngine.setStrength(filterStrength)
                    container.filterEngine.applyToImage(rawCapture)
                } else {
                    rawCapture.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                }
            }

            lastCapturedThumb = finalBitmap
            val eqFocal = (24f * zoomRatio).roundToInt()
            val fileBase = MediaStoreSaver.formatFileName(
                template = settings.fileNameTemplate,
                modeTag = captureMode.name,
                filterName = activePreset?.name,
            )
            val savedUri = MediaStoreSaver.saveBitmapToGallery(
                context = context,
                bitmap = finalBitmap,
                fileBaseName = fileBase,
                format = ExportImageFormat.JPEG,
                exifMetadata = null,
                stripLocation = !settings.saveGpsLocation,
            )
            val uriStr = savedUri?.toString() ?: run {
                Toast.makeText(context, "照片保存失败，请检查存储权限和空间", Toast.LENGTH_LONG).show()
                return@launch
            }
            container.mediaRepository.recordCapture(
                CaptureRecordEntity(
                    filePath = uriStr,
                    rawPath = null,
                    thumbnailPath = uriStr,
                    captureMode = captureMode.label,
                    cameraId = activeCameraId,
                    iso = userExposure.iso,
                    shutter = MetadataEngine.formatShutterSpeed(userExposure.shutterSeconds),
                    ev = userExposure.evCompensation,
                    wb = wbConfig.manualKelvin,
                    focalLength = eqFocal,
                    gpsEnabled = settings.saveGpsLocation,
                    filterId = selectedPresetId,
                    filterStrength = filterStrength,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            // Pro shutter flash: 80ms burst + auto-fade
            shutterFlash = true
            delay(90)
            shutterFlash = false
            Toast.makeText(
                context,
                "已保存 ${activePreset?.name ?: "原片"}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Hardware Volume Key mapping per Doc §6
    LaunchedEffect(container, settings.volumeKeyAction) {
        container.hardwareKeyEvents.collect { keyCode ->
            when (settings.volumeKeyAction) {
                VolumeKeyAction.SHUTTER -> triggerCapture()
                VolumeKeyAction.ZOOM -> {
                    val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.5f else -0.5f
                    zoomRatio = (zoomRatio + delta).coerceIn(0.6f, 6.0f)
                    container.cameraEngine.setZoomRatio(zoomRatio)
                }
                VolumeKeyAction.EXPOSURE -> {
                    val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.3f else -0.3f
                    userExposure = userExposure.copy(
                        evCompensation = (userExposure.evCompensation + delta).coerceIn(-2f, 2f),
                    )
                }
            }
        }
    }

    val effectiveEv = previewExposure.evCompensation
    val isDarkFrame = frameLuma?.isBlack == true && settings.autoBrightnessRescue
    val isDimFrame = frameLuma?.isDim == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07090C))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── 1. 顶部仪表条（曝光 / 闪光 / 构图 / 定时 / 连拍）────────────────────────
        CameraTopDeck(
            effectiveEv = effectiveEv,
            brightnessLabel = brightnessMode.label,
            flashLabel = flashSetting.label,
            flashActive = flashSetting != FlashSetting.OFF,
            guideLabel = settings.compositionGuide.label,
            guideActive = settings.compositionGuide != CompositionGuideType.NONE,
            timerSeconds = timerSeconds,
            burstLabel = when {
                bracketMode -> "包围 ±2EV"
                burstCount > 1 -> "连拍 ${burstCount}张"
                else -> "单张"
            },
            burstActive = bracketMode || burstCount > 1,
            brightnessActive = brightnessMode != ViewfinderBrightnessMode.NATURAL,
            onExposureClick = {
                val steps = listOf(-1.3f, -0.7f, 0f, 0.7f, 1.3f, 2f)
                val next = steps[(steps.indexOfFirst { kotlin.math.abs(it - userExposure.evCompensation) < 0.2f } + 1)
                    .coerceIn(0, steps.lastIndex)]
                userExposure = userExposure.copy(manualMode = false, aeLocked = false, evCompensation = next)
            },
            onBrightnessClick = { showBrightnessPanel = !showBrightnessPanel },
            onFlashClick = {
                val all = FlashSetting.entries
                flashSetting = all[(all.indexOf(flashSetting) + 1) % all.size]
            },
            onGuideClick = {
                val all = CompositionGuideType.entries
                val next = all[(all.indexOf(settings.compositionGuide) + 1) % all.size]
                scope.launch { container.preferences.update { it.copy(compositionGuide = next) } }
            },
            onTimerClick = {
                timerSeconds = when (timerSeconds) {
                    0 -> 3
                    3 -> 10
                    else -> 0
                }
            },
            onBurstClick = {
                when {
                    !bracketMode && burstCount == 1 -> burstCount = 5
                    burstCount > 1 -> {
                        burstCount = 1
                        bracketMode = true
                    }
                    else -> bracketMode = false
                }
            },
        )

        AnimatedVisibility(
            visible = showBrightnessPanel,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            BrightnessControlPanel(
                mode = brightnessMode,
                onSelectMode = {
                    brightnessMode = it
                    scope.launch { container.preferences.update { s -> s.copy(viewfinderBrightness = it) } }
                },
                manualBoost = manualBoost,
                onManualBoost = {
                    manualBoost = it
                    manualBoostTouched = it > 0.001f
                },
                autoLiftEnabled = autoLiftEnabled,
                onToggleAutoLift = { autoLiftEnabled = it },
                frameLuma = frameLuma,
                onRescue = { rescueExposure() },
            )
        }

        activeFieldBanner?.let { tip ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(Color(0xFF13241D), RoundedCornerShape(10.dp))
                    .border(0.8.dp, OpticalEmerald.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .clickable { activeFieldBanner = null }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "✦ 现场指导已载入：$tip",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpticalEmerald,
                    modifier = Modifier.weight(1f),
                )
                Text("✕", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }

        // ── 2. 全幅取景框 ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF05070A))
                .border(1.dp, Color(0xFF39424F), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings.touchToCapture, settings.keepAeOnTap) {
                        detectTapGestures { offset ->
                            val (nx, ny) = normalizedTap(offset.x, offset.y, size.width, size.height)
                            val nextFocus = focusConfig.copy(
                                manualFocus = false,
                                normalizedTapX = nx,
                                normalizedTapY = ny,
                            )
                            focusConfig = nextFocus
                            container.cameraEngine.setFocus(nextFocus)
                            if (!settings.keepAeOnTap) {
                                // 点哪里对焦/测光，但不把 AE 长期锁在暗部（否则构图一半发黑）
                                container.cameraEngine.setExposure(previewExposure)
                            }
                            if (settings.touchToCapture) triggerCapture()
                        }
                    },
            ) {
                if (hasCameraPerm) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // 观感提亮：只影响屏幕，不影响成片；Screen 混合避免把黑色抬成灰雾。
                    ViewfinderLiftOverlay(lift = displayLift, modifier = Modifier.fillMaxSize())
                    ViewfinderBloomOverlay(
                        strength = if (frameLuma?.isBlack == true) 0.16f else 0f,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (settings.showZebraStripes && capturedFrame != null) {
                        ZebraOverlay(
                            bitmap = capturedFrame,
                            modifier = Modifier.fillMaxSize(),
                            threshold = 0.94f,
                        )
                    }

                    if (!previewStreaming) {
                        Column(
                            modifier = Modifier.align(Alignment.Center)
                                .background(Color(0xE6141A22), RoundedCornerShape(18.dp))
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                previewError ?: "正在启动相机…",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { retryCount++ }) { Text("重试相机") }
                                if (frameLuma?.isBlack == true) {
                                    OutlinedButton(onClick = { rescueExposure() }) { Text("修复曝光") }
                                }
                            }
                        }
                    }
                } else {
                    CameraPermissionGate(onRequest = {
                        permLauncher.launch(
                            CameraPermissionHelper.requiredCameraPermissions(includeAudio = false).toTypedArray(),
                        )
                    })
                }

                // 取景边角刻度 + 构图辅助线 + 电子水平仪 + 对焦指示
                FinderCornerTicks(accent = Color.White.copy(alpha = 0.42f), modifier = Modifier.fillMaxSize())
                CompositionOverlayCanvas(
                    guideType = settings.compositionGuide,
                    showLeveler = settings.showLeveler,
                    rollDegrees = animatedRoll,
                    focusTapNormalized = focusConfig.normalizedTapX?.let { x ->
                        focusConfig.normalizedTapY?.let { y -> x to y }
                    },
                )

                // 快门闪光
                if (flashAlpha > 0.01f) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha * 0.92f)))
                }

                // 左下：真实测光/画质遥测
                if (hasCameraPerm) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (settings.showHistogram && liveHistogram != null) {
                            LiveHistogramHud(histogram = liveHistogram!!)
                        }
                        if (frameLuma != null) {
                            LumaMeterBar(
                                luma = frameLuma!!,
                                effectiveEv = effectiveEv,
                                zoomLabel = formatZoomLabel(zoomRatio),
                                bindingLabel = bindingLabel,
                            )
                        }
                    }
                }

                // 右上：暗场/偏暗提示（可一键修复）
                AnimatedVisibility(
                    visible = (isDarkFrame || isDimFrame) && previewStreaming,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xE6311208), RoundedCornerShape(10.dp))
                            .border(1.dp, AlertCrimson.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable { rescueExposure() }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Exposure,
                            contentDescription = null,
                            tint = BrassGold,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isDarkFrame) "画面过暗 · 点此修复" else "偏暗 · 提亮",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }

                // 变焦档位（底部居中，系统相机同款手势）
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .background(Color(0xC40B0D12), CircleShape)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZOOM_PRESETS.forEach { z ->
                        val active = kotlin.math.abs(zoomRatio - z) < 0.12f
                        Text(
                            text = formatZoomLabel(z),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) ObsidianBg else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (active) BrassGold else Color.Transparent)
                                .clickable {
                                    zoomRatio = z
                                    container.cameraEngine.setZoomRatio(z)
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }

                if (previewStreaming && previewResolution.isNotEmpty()) {
                    Text(
                        text = previewResolution,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(Color(0xA60B0D12), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                }

                if (countdownRemaining > 0) {
                    Text(
                        text = countdownRemaining.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = BrassGold,
                        modifier = Modifier
                            .background(Color(0xAA000000), CircleShape)
                            .padding(horizontal = 26.dp, vertical = 14.dp),
                    )
                }

                if (isRecordingVideo) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(Color(0xCC2A0B0B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = AlertCrimson,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("REC", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }

        // ── 3. 专业手动参数面板 ─────────────────────────────────────────────────
        if (captureMode == CameraCaptureMode.PRO || captureMode == CameraCaptureMode.LONG_EXPOSURE) {
            ProParameterPanel(
                activeTab = activeProDial,
                onSelectTab = { activeProDial = it },
                exposure = userExposure,
                focus = focusConfig,
                wb = wbConfig,
                onUpdateExposure = {
                    userExposure = it
                    container.cameraEngine.setExposure(
                        it.copy(evCompensation = if (it.manualMode) it.evCompensation else previewExposure.evCompensation),
                    )
                },
                onUpdateFocus = {
                    focusConfig = it
                    container.cameraEngine.setFocus(it)
                },
                onUpdateWb = {
                    wbConfig = it
                    container.cameraEngine.setWhiteBalance(it)
                },
            )
        }

        // ── 4. 滤镜抽屉 ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showFilterTray,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            OphotoCard(
                modifier = Modifier.fillMaxWidth(),
                borderDp = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "拍照滤镜 · ${activePreset?.name ?: "原片直出"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrassGold,
                    )
                    if (activePreset != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(170.dp),
                        ) {
                            Text(
                                text = "${(filterStrength * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Slider(
                                value = filterStrength,
                                onValueChange = { filterStrength = it },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = BrassGold,
                                    activeTrackColor = BrassGold,
                                ),
                                modifier = Modifier.height(22.dp),
                            )
                        }
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 10.dp),
                ) {
                    item {
                        FilterQuickChip(
                            name = "原片",
                            selected = selectedPresetId == null,
                            accent = TextSecondary,
                            onClick = { selectedPresetId = null },
                        )
                    }
                    items(presets, key = { it.id }) { preset ->
                        val parsedAccent = remember(preset.accentHex) {
                            runCatching { Color(android.graphics.Color.parseColor(preset.accentHex)) }
                                .getOrDefault(BrassGold)
                        }
                        FilterQuickChip(
                            name = preset.name,
                            selected = selectedPresetId == preset.id,
                            accent = parsedAccent,
                            onClick = { selectedPresetId = preset.id },
                        )
                    }
                }
            }
        }

        // ── 5. 模式选择 + 快门甲板 ─────────────────────────────────────────────
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            items(CameraCaptureMode.entries) { mode ->
                val selected = mode == captureMode
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) ObsidianBg else TextMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) BrassGold else Color.Transparent)
                        .clickable { captureMode = mode }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：暗房入口 / 最近拍摄缩略图
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ControlPillBg)
                    .border(1.2.dp, BrassGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable { onJumpToEditor() },
                contentAlignment = Alignment.Center,
            ) {
                val thumb = lastCapturedThumb
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "最近拍摄",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = "进入暗房编辑",
                        tint = BrassGold,
                    )
                }
            }

            // 中：快门
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(shutterScale)
                    .clip(CircleShape)
                    .background(
                        when {
                            isRecordingVideo -> GlowOrange.copy(alpha = 0.45f)
                            captureMode == CameraCaptureMode.VIDEO -> ShutterOrange.copy(alpha = 0.25f)
                            else -> GlowBrass.copy(alpha = 0.30f)
                        },
                        CircleShape,
                    )
                    .border(3.dp, BrassGold, CircleShape)
                    .clickable { triggerCapture() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(if (isRecordingVideo) RoundedCornerShape(14.dp) else CircleShape)
                        .background(
                            when {
                                isRecordingVideo -> AlertCrimson
                                captureMode == CameraCaptureMode.VIDEO -> ShutterOrange
                                else -> Color(0xFFF3F5F8)
                            },
                        ),
                )
            }

            // 右：滤镜 / 亮度 / 切镜头
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { showFilterTray = !showFilterTray },
                    modifier = Modifier
                        .size(42.dp)
                        .background(if (showFilterTray) BrassGold.copy(alpha = 0.22f) else ControlPillBg, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "滤镜面板开关",
                        tint = if (showFilterTray) BrassGold else TextSecondary,
                    )
                }
                IconButton(
                    onClick = { showBrightnessPanel = !showBrightnessPanel },
                    modifier = Modifier
                        .size(42.dp)
                        .background(if (showBrightnessPanel) BrassGold.copy(alpha = 0.22f) else ControlPillBg, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Exposure,
                        contentDescription = "取景亮度",
                        tint = if (showBrightnessPanel) BrassGold else TextSecondary,
                    )
                }
                IconButton(
                    onClick = {
                        val lenses = container.hardwareProfile.lenses
                        val nextId = if (lenses.size >= 2) {
                            val curIdx = lenses.indexOfFirst { it.cameraId == activeCameraId }
                            lenses[(curIdx + 1) % lenses.size].cameraId
                        } else {
                            if (activeCameraId == "0") "1" else "0"
                        }
                        activeCameraId = nextId
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(ControlPillBg, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = "切换摄像头",
                        tint = TextPrimary,
                    )
                }
            }
        }
    }
}

/**
 * 顶部仪表条：把曝光、亮度方案、闪光、构图、定时、连拍压缩进一行可横向滑动的胶囊，
 * 并让「曝光」始终显示真实生效的 EV（受设备步长限幅）。
 */
@Composable
private fun CameraTopDeck(
    effectiveEv: Float,
    brightnessLabel: String,
    flashLabel: String,
    flashActive: Boolean,
    guideLabel: String,
    guideActive: Boolean,
    timerSeconds: Int,
    burstLabel: String,
    burstActive: Boolean,
    brightnessActive: Boolean,
    onExposureClick: () -> Unit,
    onBrightnessClick: () -> Unit,
    onFlashClick: () -> Unit,
    onGuideClick: () -> Unit,
    onTimerClick: () -> Unit,
    onBurstClick: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(InstrumentDeck, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            TopHudChip(
                label = "曝光 %+.1f".format(effectiveEv),
                active = kotlin.math.abs(effectiveEv) > 0.05f,
                onClick = onExposureClick,
            )
        }
        item {
            TopHudChip(
                label = brightnessLabel,
                active = brightnessActive,
                onClick = onBrightnessClick,
            )
        }
        item {
            TopHudChip(
                label = "闪光 $flashLabel",
                active = flashActive,
                onClick = onFlashClick,
            )
        }
        item {
            TopHudChip(
                label = guideLabel,
                active = guideActive,
                onClick = onGuideClick,
            )
        }
        item {
            TopHudChip(
                label = if (timerSeconds == 0) "定时 关" else "${timerSeconds}s",
                active = timerSeconds > 0,
                onClick = onTimerClick,
            )
        }
        item {
            TopHudChip(
                label = burstLabel,
                active = burstActive,
                onClick = onBurstClick,
            )
        }
    }
}

/** 亮度方案面板：方案档位 + 手动增益 + 实时亮度读数 + 一键修复。 */
@Composable
private fun BrightnessControlPanel(
    mode: ViewfinderBrightnessMode,
    onSelectMode: (ViewfinderBrightnessMode) -> Unit,
    manualBoost: Float,
    onManualBoost: (Float) -> Unit,
    autoLiftEnabled: Boolean,
    onToggleAutoLift: (Boolean) -> Unit,
    frameLuma: FrameLuma?,
    onRescue: () -> Unit,
) {
    OphotoCard(
        modifier = Modifier.padding(top = 6.dp),
        borderDp = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "取景亮度 · 只影响观感，成片仍按你的曝光",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrassGold,
                )
                Text(
                    text = frameLuma?.let { "实测 ${it.meanPercent}%" } ?: "测量中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        frameLuma == null -> TextMuted
                        frameLuma.isBlack -> AlertCrimson
                        frameLuma.isDim -> ShutterOrange
                        else -> OpticalEmerald
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ViewfinderBrightnessMode.entries) { m ->
                    TopHudChip(label = m.label, active = m == mode, onClick = { onSelectMode(m) })
                }
            }
            Text(
                text = mode.hint,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TopHudChip(
                    label = if (autoLiftEnabled) "自适应提亮 开" else "自适应提亮 关",
                    active = autoLiftEnabled,
                    onClick = { onToggleAutoLift(!autoLiftEnabled) },
                )
                TopHudChip(
                    label = if (manualBoost <= 0.001f) "手动增益 0%" else "手动增益 +${(manualBoost * 100).roundToInt()}%",
                    active = manualBoost > 0.001f,
                    onClick = { onManualBoost(0f) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "增益强度",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.width(110.dp),
                )
                Slider(
                    value = manualBoost,
                    onValueChange = onManualBoost,
                    valueRange = 0f..0.35f,
                    colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRescue,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, null, tint = BrassGold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("一键修复黑屏", color = BrassGold, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** 取景底部测光条：把实测亮度、EV、焦段、会话能力放一行，仪器感 + 可诊断。 */
@Composable
private fun LumaMeterBar(
    luma: FrameLuma,
    effectiveEv: Float,
    zoomLabel: String,
    bindingLabel: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xB00B0D12), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.width(46.dp).height(6.dp)) {
            drawRoundRect(
                color = Color(0xFF2B3242),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                color = when {
                    luma.isBlack -> AlertCrimson
                    luma.isDim -> ShutterOrange
                    else -> OpticalEmerald
                },
                size = androidx.compose.ui.geometry.Size(
                    width = size.width * luma.mean.coerceIn(0.02f, 1f),
                    height = size.height,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
        }
        Text(
            text = luma.meanPercent.toString().padStart(3) + "%",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
        )
        Text(
            text = "$zoomLabel · ${"%+.1f".format(effectiveEv)}EV",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        if (bindingLabel.isNotEmpty()) {
            Text(
                text = bindingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun CameraPermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1B2432), Color(0xFF0B0F16))),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(ControlPillBg, CircleShape)
                .border(1.2.dp, BrassGold.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "等待相机授权",
                tint = BrassGold,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("需要相机权限", style = MaterialTheme.typography.titleMedium, color = BrassGold)
        Spacer(Modifier.height(4.dp))
        Text(
            "授权后才会显示实时取景画面",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(onClick = onRequest) { Text("授权相机") }
    }
}

@Composable
private fun ProParameterPanel(
    activeTab: ProDialTab,
    onSelectTab: (ProDialTab) -> Unit,
    exposure: ExposureConfig,
    focus: FocusConfig,
    wb: WhiteBalanceConfig,
    onUpdateExposure: (ExposureConfig) -> Unit,
    onUpdateFocus: (FocusConfig) -> Unit,
    onUpdateWb: (WhiteBalanceConfig) -> Unit,
) {
    OphotoCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        borderDp = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProDialTab.entries) { tab ->
                    val selected = tab == activeTab
                    OphotoChip(
                        label = tab.label,
                        selected = selected,
                        accent = BrassGold,
                        onClick = { onSelectTab(tab) },
                    )
                }
            }

            // AE-L Lock Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (exposure.aeLocked) ShutterOrange.copy(alpha = 0.25f) else ControlPillBg)
                    .clickable {
                        onUpdateExposure(exposure.copy(aeLocked = !exposure.aeLocked))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (exposure.aeLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = "曝光锁定",
                    tint = if (exposure.aeLocked) ShutterOrange else TextSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "AE-L",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exposure.aeLocked) ShutterOrange else TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        when (activeTab) {
            ProDialTab.EV -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "%+.1f EV".format(exposure.evCompensation),
                        style = MaterialTheme.typography.labelMedium,
                        color = BrassGold,
                        modifier = Modifier.width(68.dp),
                    )
                    Slider(
                        value = exposure.evCompensation,
                        onValueChange = { onUpdateExposure(exposure.copy(manualMode = false, evCompensation = it)) },
                        valueRange = -2.0f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            ProDialTab.ISO -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        TopHudChip(
                            label = "AUTO",
                            active = !exposure.manualMode,
                            onClick = { onUpdateExposure(exposure.copy(manualMode = false)) },
                        )
                    }
                    items(ISO_STOPS) { isoVal ->
                        TopHudChip(
                            label = isoVal.toString(),
                            active = exposure.manualMode && exposure.iso == isoVal,
                            onClick = { onUpdateExposure(exposure.copy(manualMode = true, iso = isoVal)) },
                        )
                    }
                }
            }

            ProDialTab.SHUTTER -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SHUTTER_STOPS) { sec ->
                        val text = MetadataEngine.formatShutterSpeed(sec)
                        TopHudChip(
                            label = text,
                            active = exposure.manualMode && kotlin.math.abs(exposure.shutterSeconds - sec) < 1e-5,
                            onClick = { onUpdateExposure(exposure.copy(manualMode = true, shutterSeconds = sec)) },
                        )
                    }
                }
            }

            ProDialTab.WB -> {
                Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(WhiteBalanceMode.entries) { mode ->
                            TopHudChip(
                                label = mode.label,
                                active = wb.mode == mode,
                                onClick = {
                                    onUpdateWb(
                                        wb.copy(
                                            mode = mode,
                                            manualKelvin = mode.approximateKelvin,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    if (wb.mode == WhiteBalanceMode.MANUAL) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${wb.manualKelvin}K",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrassGold,
                                modifier = Modifier.width(62.dp),
                            )
                            Slider(
                                value = wb.manualKelvin.toFloat(),
                                onValueChange = { onUpdateWb(wb.copy(manualKelvin = it.roundToInt())) },
                                valueRange = 2500f..10000f,
                                colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            ProDialTab.FOCUS -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TopHudChip(
                        label = if (focus.manualFocus) "MF 手动" else "AF 自动",
                        active = focus.manualFocus,
                        onClick = { onUpdateFocus(focus.copy(manualFocus = !focus.manualFocus)) },
                    )
                    Slider(
                        value = focus.focusDistanceDiopters,
                        onValueChange = { onUpdateFocus(focus.copy(manualFocus = true, focusDistanceDiopters = it)) },
                        valueRange = 0f..10f,
                        colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (focus.focusDistanceDiopters < 0.2f) "∞ 无穷远" else "%.1fdpt".format(focus.focusDistanceDiopters),
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassGold,
                    )
                }
            }

            ProDialTab.METERING -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MeteringMode.entries) { m ->
                        TopHudChip(
                            label = m.label,
                            active = exposure.meteringMode == m,
                            onClick = { onUpdateExposure(exposure.copy(meteringMode = m)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopHudChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (active) ObsidianBg else TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) BrassGold else ControlPillBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun FilterQuickChip(
    name: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) accent.copy(alpha = 0.28f) else ControlPillBg,
            contentColor = if (selected) accent else TextPrimary,
        ),
        modifier = Modifier.height(30.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
