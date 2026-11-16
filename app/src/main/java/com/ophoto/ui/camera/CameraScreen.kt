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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.GridOn
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
import androidx.compose.ui.unit.Dp
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
import com.ophoto.ui.theme.MaterialHairline
import com.ophoto.ui.theme.MaterialRegular
import com.ophoto.ui.theme.MaterialThick
import com.ophoto.ui.theme.MaterialThin
import com.ophoto.ui.theme.OphotoShape
import com.ophoto.ui.theme.pressScale
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ophoto.data.repository.DemoPhotos
import com.ophoto.ui.theme.BrassGoldSoft
import com.ophoto.ui.theme.OphotoBrassButton
import com.ophoto.ui.theme.OphotoOutlinedButton
import com.ophoto.ui.theme.TextOnAccent

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

/** 相机页的三种界面密度（Deference：让内容决定控件的去留）。 */
private enum class ChromeDensity { FULL, COMPACT, HIDDEN }

@Composable
fun CameraScreen(
    container: OphotoContainer,
    onJumpToEditor: () -> Unit,
    modifier: Modifier = Modifier,
    onChromeInteraction: () -> Unit = {},
    onChromeIdle: () -> Unit = {},
    /** 底部全局标签栏高度：让快门与工具行浮在标签栏之上而不是被遮住。 */
    bottomInset: Dp = 0.dp,
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
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var burstCount by remember { mutableIntStateOf(1) }
    var bracketMode by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<CameraSheet?>(null) }
    var activeProDial by remember { mutableStateOf(ProDialTab.EV) }

    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    var filterStrength by remember { mutableFloatStateOf(settings.defaultFilterStrength) }

    var userExposure by remember {
        mutableStateOf(ExposureConfig(manualMode = false, iso = 100, shutterSeconds = 1.0 / 125.0, evCompensation = 0f))
    }
    var brightnessMode by remember { mutableStateOf(settings.viewfinderBrightness) }
    var displayLift by remember { mutableFloatStateOf(0f) }
    var manualBoost by remember { mutableFloatStateOf(0f) }
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

    // 沉浸控制：交互 → FULL；静止 4s → COMPACT；长按照片 → HIDDEN
    var chromeDensity by remember { mutableStateOf(ChromeDensity.FULL) }
    fun wake() {
        chromeDensity = ChromeDensity.FULL
        onChromeInteraction()
    }

    LaunchedEffect(settings.autoHideChrome, chromeDensity, sheet, isRecordingVideo) {
        if (!settings.autoHideChrome || sheet != null || isRecordingVideo) return@LaunchedEffect
        if (chromeDensity == ChromeDensity.FULL) {
            delay(4200)
            chromeDensity = ChromeDensity.COMPACT
            onChromeIdle()
        }
    }

    // 再次进入相机页时，把被其他页面隐去的控制条唤回
    LaunchedEffect(Unit) { onChromeInteraction() }

    val previewView = remember(context, host) {
        host.ensurePreviewView {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        }
    }
    var previewStreaming by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var previewResolution by remember { mutableStateOf("") }

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

    LaunchedEffect(previewExposure) {
        if (hasCameraPerm) container.cameraEngine.setExposure(previewExposure)
    }

    LaunchedEffect(hasCameraPerm, previewStreaming, settings.showHistogram, settings.showZebraStripes) {
        while (hasCameraPerm && previewStreaming) {
            delay(650)
            val frame = withContext(Dispatchers.Default) { runCatching { previewView.bitmap }.getOrNull() }
            if (frame != null) {
                frameLuma = withContext(Dispatchers.Default) { sampler.sample(frame) }
                if (settings.showHistogram) {
                    liveHistogram = withContext(Dispatchers.Default) { sampler.histogram(frame) }
                }
                capturedFrame = if (settings.showZebraStripes) frame else null
            }
        }
    }

    LaunchedEffect(frameLuma, brightnessMode, manualBoost, manualBoostTouched, autoLiftEnabled) {
        val autoLift = if (autoLiftEnabled && !manualBoostTouched) {
            PreviewSampler.requiredLift(frameLuma) * brightnessMode.autoLiftScale
        } else {
            0f
        }
        val target = if (manualBoostTouched) manualBoost else (autoLift + manualBoost).coerceIn(0f, 0.40f)
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
    val animatedRoll by animateFloatAsState(
        targetValue = rollDegrees,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "rollSmooth",
    )
    var shutterFlash by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (shutterFlash) 0.9f else 0f,
        animationSpec = tween(if (shutterFlash) 70 else 380),
        label = "flash",
    )
    val shutterScale by animateFloatAsState(
        targetValue = if (shutterFlash) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "shutterScale",
    )

    fun setZoom(z: Float) {
        zoomRatio = z
        container.cameraEngine.setZoomRatio(z)
        wake()
    }

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
        wake()
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
                aspectRatio = ViewfinderAspectRatio.RATIO_4_3,
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
            // 刚拍的照片直接进暗房：点缩略图即可继续精修
            container.openMediaInEditor(uriStr)
            shutterFlash = true
            delay(80)
            shutterFlash = false
            Toast.makeText(context, "已保存 ${activePreset?.name ?: "原片"}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(container, settings.volumeKeyAction) {
        container.hardwareKeyEvents.collect { keyCode ->
            when (settings.volumeKeyAction) {
                VolumeKeyAction.SHUTTER -> triggerCapture()
                VolumeKeyAction.ZOOM -> {
                    val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.5f else -0.5f
                    setZoom((zoomRatio + delta).coerceIn(0.6f, 6.0f))
                }
                VolumeKeyAction.EXPOSURE -> {
                    val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.3f else -0.3f
                    userExposure = userExposure.copy(
                        evCompensation = (userExposure.evCompensation + delta).coerceIn(-2f, 2f),
                    )
                    wake()
                }
            }
        }
    }

    val effectiveEv = previewExposure.evCompensation
    val isDarkFrame = frameLuma?.isBlack == true && settings.autoBrightnessRescue
    val isDimFrame = frameLuma?.isDim == true
    val showHud = chromeDensity != ChromeDensity.HIDDEN

    // ── 全屏取景画布（内容即界面） ────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(settings.touchToCapture, settings.keepAeOnTap, settings.longPressCleanView) {
                    detectTapGestures(
                        onTap = { offset ->
                            val (nx, ny) = normalizedTap(offset.x, offset.y, size.width, size.height)
                            val nextFocus = focusConfig.copy(
                                manualFocus = false,
                                normalizedTapX = nx,
                                normalizedTapY = ny,
                            )
                            focusConfig = nextFocus
                            container.cameraEngine.setFocus(nextFocus)
                            if (!settings.keepAeOnTap) container.cameraEngine.setExposure(previewExposure)
                            wake()
                            if (settings.touchToCapture) triggerCapture()
                        },
                        onLongPress = {
                            if (settings.longPressCleanView) {
                                chromeDensity = if (chromeDensity == ChromeDensity.HIDDEN) ChromeDensity.FULL else ChromeDensity.HIDDEN
                                onChromeIdle()
                            }
                        },
                    )
                },
        ) {
            if (hasCameraPerm) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                // 观感提亮（只作用于屏幕，不影响成片）
                ViewfinderLiftOverlay(lift = displayLift, modifier = Modifier.fillMaxSize())
                ViewfinderBloomOverlay(
                    strength = if (frameLuma?.isBlack == true) 0.14f else 0f,
                    modifier = Modifier.fillMaxSize(),
                )

                if (settings.showZebraStripes && capturedFrame != null) {
                    ZebraOverlay(bitmap = capturedFrame, modifier = Modifier.fillMaxSize(), threshold = 0.94f)
                }

                if (!previewStreaming) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(OphotoShape.Card)
                            .background(MaterialRegular)
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            previewError ?: "正在启动相机…",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OphotoBrassButton(onClick = { retryCount++ }, text = "重试")
                            if (frameLuma?.isBlack == true) {
                                OphotoOutlinedButton(onClick = { rescueExposure() }) {
                                    Text("修复曝光", style = MaterialTheme.typography.labelLarge)
                                }
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

            // 监看叠层：只在 FULL 密度下显示，避免遮挡构图
            if (showHud) {
                CompositionOverlayCanvas(
                    guideType = settings.compositionGuide,
                    showLeveler = settings.showLeveler,
                    rollDegrees = animatedRoll,
                    focusTapNormalized = focusConfig.normalizedTapX?.let { x ->
                        focusConfig.normalizedTapY?.let { y -> x to y }
                    },
                )
            }

            if (flashAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha * 0.9f)))
            }

            if (countdownRemaining > 0) {
                Text(
                    text = countdownRemaining.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                )
            }
        }

        // ── 顶部：状态胶囊（FULL 显示完整，COMPACT 只留模式与关闭） ─────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = chromeDensity == ChromeDensity.FULL,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左：曝光读数（唯一强调色用在可操作处）
                    HudCapsule(
                        onClick = {
                            // 轻点循环 EV：0 → +0.7 → +1.3 → -0.7 → -1.3 → 0
                            val steps = listOf(0f, 0.7f, 1.3f, -0.7f, -1.3f)
                            val idx = steps.indexOfFirst { kotlin.math.abs(it - userExposure.evCompensation) < 0.2f }
                            val next = steps[if (idx < 0) 1 else (idx + 1) % steps.size]
                            userExposure = userExposure.copy(
                                manualMode = false, aeLocked = false, evCompensation = next,
                            )
                            wake()
                        },
                        highlighted = kotlin.math.abs(userExposure.evCompensation) > 0.05f,
                    ) {
                        Text(
                            text = "EV ${"%+.1f".format(effectiveEv)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (kotlin.math.abs(userExposure.evCompensation) > 0.05f) TextOnAccent else TextPrimary,
                        )
                    }

                    // 中：模式切换（唯一的横向主控；窄屏自动省略「专业」，可在「更多」里开启）
                    val compactModes = listOf(
                        CameraCaptureMode.PHOTO,
                        CameraCaptureMode.PORTRAIT,
                        CameraCaptureMode.NIGHT,
                        CameraCaptureMode.VIDEO,
                    )
                    val modesToShow = if (captureMode in compactModes) {
                        compactModes
                    } else {
                        compactModes + captureMode
                    }
                    Row(
                        modifier = Modifier
                            .clip(OphotoShape.Chip)
                            .background(Color(0x66000000))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        modesToShow.forEach { mode ->
                            val active = captureMode == mode
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) TextOnAccent else TextSecondary,
                                modifier = Modifier
                                    .clip(OphotoShape.Chip)
                                    .background(if (active) BrassGold else Color.Transparent)
                                    .clickable {
                                        captureMode = mode
                                        wake()
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                            )
                        }
                    }

                    // 右：闪光 + 更多
                    HudCapsule(onClick = {
                        val all = FlashSetting.entries
                        flashSetting = all[(all.indexOf(flashSetting) + 1) % all.size]
                        wake()
                    }, highlighted = flashSetting != FlashSetting.OFF) {
                        Icon(
                            Icons.Rounded.WbSunny,
                            contentDescription = "闪光",
                            tint = if (flashSetting != FlashSetting.OFF) TextOnAccent else TextPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // 第二行：真实读数 + 现场指导提示
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildHealthLine(
                            streaming = previewStreaming,
                            resolution = previewResolution,
                            frameLuma = frameLuma,
                            zoomLabel = formatZoomLabel(zoomRatio),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    activeFieldBanner?.let { tip ->
                        Text(
                            text = "✦ $tip",
                            style = MaterialTheme.typography.labelSmall,
                            color = OpticalEmerald,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(OphotoShape.Chip)
                                .background(Color(0xCC0A1A12))
                                .clickable { activeFieldBanner = null }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        // ── 暗场诊断（Deference：只在异常时出现） ──────────────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = (isDarkFrame || isDimFrame) && previewStreaming && showHud,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 96.dp)
                    .clip(OphotoShape.Chip)
                    .background(Color(0xE0241207))
                    .border(0.7.dp, AlertCrimson.copy(alpha = 0.5f), OphotoShape.Chip)
                    .clickable { rescueExposure() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Exposure, null, tint = BrassGold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isDarkFrame) "画面过暗 · 点此修复" else "偏暗 · 点此提亮",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                )
            }
        }

        // ── REC 指示 ──────────────────────────────────────────────────────────
        if (isRecordingVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 14.dp, top = 96.dp)
                    .clip(OphotoShape.Chip)
                    .background(Color(0xCC2A0B0B))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(AlertCrimson))
                Spacer(Modifier.width(6.dp))
                Text("REC", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }

        // ── 底部：主控甲板 ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF2000000))))
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = bottomInset + 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 测光/直方图：只在 FULL 且需要时出现
            androidx.compose.animation.AnimatedVisibility(
                visible = chromeDensity == ChromeDensity.FULL && hasCameraPerm && frameLuma != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (settings.showHistogram && liveHistogram != null) {
                        LiveHistogramHud(histogram = liveHistogram!!)
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    frameLuma?.let { luma ->
                        LumaMeterRow(luma = luma, effectiveEv = effectiveEv)
                    }
                }
            }

            // 变焦：单行胶囊，恒常可见（拍摄核心操作）
            if (hasCameraPerm) {
                ZoomPillRow(zoomRatio = zoomRatio, onZoom = { setZoom(it) })
                Spacer(Modifier.height(10.dp))
            }

            // 主控行：暗房入口 · 快门 · 镜头/滤镜
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左：暗房（最近一张）
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .pressScale()
                        .clickable { onJumpToEditor() }
                        .padding(3.dp),
                ) {
                    val thumb = lastCapturedThumb
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "进入暗房",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .border(0.7.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x33FFFFFF))
                                .border(0.7.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, "进入暗房", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // 中：快门（唯一实心白，视觉焦点）
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(if (isRecordingVideo) RoundedCornerShape(12.dp) else CircleShape)
                            .background(if (isRecordingVideo) AlertCrimson else Color.White)
                            .clickable { triggerCapture() },
                    )
                }

                // 右：镜头切换
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(0.7.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .pressScale()
                        .clickable {
                            val lenses = container.hardwareProfile.lenses
                            activeCameraId = if (lenses.size >= 2) {
                                val curIdx = lenses.indexOfFirst { it.cameraId == activeCameraId }
                                lenses[(curIdx + 1) % lenses.size].cameraId
                            } else {
                                if (activeCameraId == "0") "1" else "0"
                            }
                            wake()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Cameraswitch, "切换镜头", tint = Color.White, modifier = Modifier.size(21.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // 次要工具行：滤镜 / 亮度 / 定时 / 网格 —— 半透明胶囊，FULL 时出现
            androidx.compose.animation.AnimatedVisibility(
                visible = chromeDensity != ChromeDensity.HIDDEN,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolPill(
                        icon = Icons.Rounded.Tune,
                        label = activePreset?.name ?: "滤镜",
                        active = activePreset != null,
                        onClick = { sheet = CameraSheet.FILTERS; wake() },
                    )
                    ToolPill(
                        icon = Icons.Rounded.Exposure,
                        label = "亮度",
                        active = brightnessMode != ViewfinderBrightnessMode.NATURAL,
                        onClick = { sheet = CameraSheet.BRIGHTNESS; wake() },
                    )
                    ToolPill(
                        icon = Icons.Rounded.Timer,
                        label = if (timerSeconds == 0) "定时" else "${timerSeconds}s",
                        active = timerSeconds > 0,
                        onClick = {
                            timerSeconds = when (timerSeconds) {
                                0 -> 3
                                3 -> 10
                                else -> 0
                            }
                            wake()
                        },
                    )
                    ToolPill(
                        icon = Icons.Rounded.GridOn,
                        label = settings.compositionGuide.label,
                        active = settings.compositionGuide != CompositionGuideType.NONE,
                        onClick = {
                            val all = CompositionGuideType.entries
                            val next = all[(all.indexOf(settings.compositionGuide) + 1) % all.size]
                            scope.launch { container.preferences.update { it.copy(compositionGuide = next) } }
                            wake()
                        },
                    )
                    ToolPill(
                        icon = Icons.Rounded.MoreHoriz,
                        label = if (bracketMode) "包围" else if (burstCount > 1) "连拍" else "更多",
                        active = bracketMode || burstCount > 1,
                        onClick = { sheet = CameraSheet.MORE; wake() },
                    )
                }
            }
        }

        // ── 底部抽屉（Depth：从底部延伸出来的材质面板） ─────────────────────────
        CameraSheetHost(
            sheet = sheet,
            onDismiss = { sheet = null },
            content = { current ->
                when (current) {
                    CameraSheet.FILTERS -> FiltersSheetContent(
                        presets = presets,
                        selectedPresetId = selectedPresetId,
                        strength = filterStrength,
                        onSelect = { selectedPresetId = it },
                        onStrength = { filterStrength = it },
                    )
                    CameraSheet.BRIGHTNESS -> BrightnessSheetContent(
                        brightnessMode = brightnessMode,
                        onBrightnessMode = {
                            brightnessMode = it
                            scope.launch { container.preferences.update { s -> s.copy(viewfinderBrightness = it) } }
                        },
                        manualBoost = manualBoost,
                        onManualBoost = {
                            manualBoost = it
                            manualBoostTouched = it > 0.001f
                        },
                        autoLift = autoLiftEnabled,
                        onToggleAutoLift = { autoLiftEnabled = it },
                        frameLuma = frameLuma,
                        onRescue = { rescueExposure() },
                    )
                    CameraSheet.MORE -> MoreSheetContent(
                        captureMode = captureMode,
                        onCaptureMode = { captureMode = it },
                        bracketMode = bracketMode,
                        onBracket = { bracketMode = !bracketMode },
                        burstCount = burstCount,
                        onBurst = { burstCount = if (it) 5 else 1 },
                        onProDial = { sheet = CameraSheet.PRO },
                        exposure = userExposure,
                        focus = focusConfig,
                        wb = wbConfig,
                        onExposure = {
                            userExposure = it
                            container.cameraEngine.setExposure(it)
                        },
                        onFocus = {
                            focusConfig = it
                            container.cameraEngine.setFocus(it)
                        },
                        onWb = {
                            wbConfig = it
                            container.cameraEngine.setWhiteBalance(it)
                        },
                    )
                    CameraSheet.PRO -> ProSheetContent(
                        exposure = userExposure,
                        focus = focusConfig,
                        wb = wbConfig,
                        onExposure = {
                            userExposure = it
                            container.cameraEngine.setExposure(it)
                        },
                        onFocus = {
                            focusConfig = it
                            container.cameraEngine.setFocus(it)
                        },
                        onWb = {
                            wbConfig = it
                            container.cameraEngine.setWhiteBalance(it)
                        },
                    )
                    null -> Unit
                }
            },
        )
    }
}

private enum class CameraSheet { FILTERS, BRIGHTNESS, MORE, PRO }

/* ── 小组件 ─────────────────────────────────────────────────────────────── */

@Composable
private fun HudCapsule(
    onClick: () -> Unit,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(OphotoShape.Chip)
            .background(if (highlighted) BrassGold else Color(0x66000000))
            .pressScale()
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ToolPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .pressScale()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) BrassGold else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) BrassGold else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

/** 顶部读数：把「有没有画面 / 分辨率 / 亮度 / 焦段」压成一行，替代原来的多枚 HUD 芯片。 */
private fun buildHealthLine(
    streaming: Boolean,
    resolution: String,
    frameLuma: FrameLuma?,
    zoomLabel: String,
): String {
    if (!streaming) return "正在连接相机…"
    val parts = mutableListOf<String>()
    parts += zoomLabel
    frameLuma?.let { parts += "亮度 ${it.meanPercent}%" }
    if (resolution.isNotBlank()) parts += resolution.substringBefore(" · ")
    return parts.joinToString("  ·  ")
}

@Composable
private fun LumaMeterRow(luma: FrameLuma, effectiveEv: Float) {
    Row(
        modifier = Modifier
            .clip(OphotoShape.Chip)
            .background(Color(0x66000000))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.width(42.dp).height(4.dp)) {
            drawRoundRect(
                color = Color(0x33FFFFFF),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                color = when {
                    luma.isBlack -> AlertCrimson
                    luma.isDim -> ShutterOrange
                    else -> OpticalEmerald
                },
                size = androidx.compose.ui.geometry.Size(size.width * luma.mean.coerceIn(0.02f, 1f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
        Text(
            text = "${luma.meanPercent}%  ${"%+.1f".format(effectiveEv)}EV",
            style = com.ophoto.ui.theme.ReadingStyle,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ZoomPillRow(zoomRatio: Float, onZoom: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .clip(OphotoShape.Chip)
            .background(Color(0x4D000000))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZOOM_PRESETS.forEach { z ->
            val active = kotlin.math.abs(zoomRatio - z) < 0.12f
            Text(
                text = formatZoomLabel(z),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) TextOnAccent else Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) BrassGold else Color.Transparent)
                    .clickable { onZoom(z) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun CameraPermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(ObsidianBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ControlPillBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PhotoCamera, null, tint = TextMuted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("需要相机权限", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "授权后才会显示实时取景",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(20.dp))
        OphotoBrassButton(onClick = onRequest, text = "允许访问相机")
    }
}

/**
 * 底部抽屉宿主 · Depth
 *
 * 面板从底部「延伸」而出（带圆顶与抬起阴影），背景只做轻微压暗而不黑屏，
 * 让你在调参数时仍然看到画面变化。关闭 = 向下拖 / 点空白 / 点收起条。
 */
@Composable
private fun CameraSheetHost(
    sheet: CameraSheet?,
    onDismiss: () -> Unit,
    content: @Composable (CameraSheet?) -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = sheet != null,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(140)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = sheet != null,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                ) { it } + fadeIn(tween(120)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(OphotoShape.Sheet)
                        .background(MaterialThick)
                        .border(0.7.dp, MaterialHairline, OphotoShape.Sheet)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                ) {
                    // 拖拽指示条（物理隐喻：可以推下去）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(34.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF)),
                        )
                    }
                    content(sheet)
                }
            }
        }
    }
}

@Composable
private fun FiltersSheetContent(
    presets: List<FilterPreset>,
    selectedPresetId: String?,
    strength: Float,
    onSelect: (String?) -> Unit,
    onStrength: (Float) -> Unit,
) {
    val context = LocalContext.current
    val thumbs = remember(presets.size) {
        runCatching { DemoPhotos.loadForPreview(context, 220) }.getOrNull()
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("色彩", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                "点选即生效 · 快门直接出片",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "原片",
                style = MaterialTheme.typography.labelMedium,
                color = if (selectedPresetId == null) BrassGold else TextMuted,
                modifier = Modifier
                    .clip(OphotoShape.Chip)
                    .clickable { onSelect(null) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(presets.size) { idx ->
                val preset = presets[idx]
                val selected = preset.id == selectedPresetId
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .pressScale()
                        .clickable { onSelect(preset.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF17181C))
                            .border(
                                width = if (selected) 2.dp else 0.7.dp,
                                color = if (selected) BrassGold else Color(0x1FFFFFFF),
                                shape = RoundedCornerShape(14.dp),
                            ),
                    ) {
                        if (thumbs != null) {
                            Image(
                                bitmap = thumbs.asImageBitmap(),
                                contentDescription = preset.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // 用预设自身的色彩倾向做一层色彩提示，让每格看起来是「不同底片」
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(android.graphics.Color.parseColor(preset.accentHex)).copy(alpha = 0.26f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) BrassGold else TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (selectedPresetId != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("浓度", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Slider(
                    value = strength,
                    onValueChange = onStrength,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = BrassGold,
                        activeTrackColor = BrassGold,
                        inactiveTrackColor = Color(0x33FFFFFF),
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(
                    "${(strength * 100).roundToInt()}%",
                    style = com.ophoto.ui.theme.ReadingStyle,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun BrightnessSheetContent(
    brightnessMode: ViewfinderBrightnessMode,
    onBrightnessMode: (ViewfinderBrightnessMode) -> Unit,
    manualBoost: Float,
    onManualBoost: (Float) -> Unit,
    autoLift: Boolean,
    onToggleAutoLift: (Boolean) -> Unit,
    frameLuma: FrameLuma?,
    onRescue: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("取景亮度", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            frameLuma?.let {
                Text(
                    "实测 ${it.meanPercent}%",
                    style = com.ophoto.ui.theme.ReadingStyle,
                    color = if (it.isBlack) AlertCrimson else TextMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ViewfinderBrightnessMode.entries.forEach { mode ->
                OphotoChip(
                    label = mode.label,
                    selected = mode == brightnessMode,
                    onClick = { onBrightnessMode(mode) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("额外提亮", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Slider(
                value = manualBoost,
                onValueChange = onManualBoost,
                valueRange = 0f..0.35f,
                colors = SliderDefaults.colors(
                    thumbColor = BrassGold,
                    activeTrackColor = BrassGold,
                    inactiveTrackColor = Color(0x33FFFFFF),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(
                "${(manualBoost * 100).roundToInt()}%",
                style = com.ophoto.ui.theme.ReadingStyle,
                color = TextSecondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("自动提亮", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    "只提亮屏幕观感，不影响成片曝光",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Switch(
                checked = autoLift,
                onCheckedChange = onToggleAutoLift,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextOnAccent,
                    checkedTrackColor = BrassGold,
                ),
            )
        }
        if (frameLuma?.isBlack == true || frameLuma?.isDim == true) {
            Spacer(Modifier.height(6.dp))
            OphotoOutlinedButton(onClick = onRescue, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Exposure, null, tint = BrassGold, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("一键修复自动曝光", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun MoreSheetContent(
    captureMode: CameraCaptureMode,
    onCaptureMode: (CameraCaptureMode) -> Unit,
    bracketMode: Boolean,
    onBracket: () -> Unit,
    burstCount: Int,
    onBurst: (Boolean) -> Unit,
    onProDial: () -> Unit,
    exposure: ExposureConfig,
    focus: FocusConfig,
    wb: WhiteBalanceConfig,
    onExposure: (ExposureConfig) -> Unit,
    onFocus: (FocusConfig) -> Unit,
    onWb: (WhiteBalanceConfig) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("更多", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "专业调节",
                style = MaterialTheme.typography.labelMedium,
                color = BrassGold,
                modifier = Modifier
                    .clip(OphotoShape.Chip)
                    .background(BrassGoldSoft)
                    .clickable(onClick = onProDial)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OphotoChip(
                label = if (bracketMode) "包围曝光 开" else "包围曝光",
                selected = bracketMode,
                onClick = onBracket,
            )
            OphotoChip(
                label = if (burstCount > 1) "连拍 ${burstCount}张" else "连拍",
                selected = burstCount > 1,
                onClick = { onBurst(burstCount == 1) },
            )
            OphotoChip(label = captureMode.label, selected = true, onClick = {})
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "包围曝光会连续拍 3 张（-1 / 0 / +1 EV），便于后期合成；长按画面可隐藏全部控件。",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        // 直方图与网格在专业面板里集中
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OphotoChip(label = "锁定曝光", selected = exposure.aeLocked, onClick = {
                onExposure(exposure.copy(aeLocked = !exposure.aeLocked))
            })
            OphotoChip(label = "手动对焦", selected = focus.manualFocus, onClick = {
                onFocus(focus.copy(manualFocus = !focus.manualFocus))
            })
            OphotoChip(label = "手动白平衡", selected = wb.mode == WhiteBalanceMode.MANUAL, onClick = {
                onWb(wb.copy(mode = if (wb.mode == WhiteBalanceMode.MANUAL) WhiteBalanceMode.AUTO else WhiteBalanceMode.MANUAL))
            })
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ProSheetContent(
    exposure: ExposureConfig,
    focus: FocusConfig,
    wb: WhiteBalanceConfig,
    onExposure: (ExposureConfig) -> Unit,
    onFocus: (FocusConfig) -> Unit,
    onWb: (WhiteBalanceConfig) -> Unit,
) {
    var tab by remember { mutableStateOf(ProDialTab.EV) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProDialTab.entries.forEach { t ->
                OphotoChip(label = t.label, selected = tab == t, onClick = { tab = t })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            ProDialTab.EV -> ProSliderRow(
                label = "曝光补偿",
                value = exposure.evCompensation,
                range = -2f..2f,
                display = "%+.1f EV".format(exposure.evCompensation),
                onChange = { onExposure(exposure.copy(evCompensation = it, manualMode = false)) },
            )
            ProDialTab.ISO -> ProSliderRow(
                label = "感光度",
                value = exposure.iso.toFloat(),
                range = 50f..3200f,
                display = "ISO ${exposure.iso}",
                onChange = { onExposure(exposure.copy(iso = it.roundToInt(), manualMode = true)) },
            )
            ProDialTab.SHUTTER -> ProSliderRow(
                label = "快门速度",
                value = exposure.shutterSeconds.toFloat(),
                range = 1f / 4000f..0.5f,
                display = MetadataEngine.formatShutterSpeed(exposure.shutterSeconds),
                onChange = { onExposure(exposure.copy(shutterSeconds = it.toDouble(), manualMode = true)) },
            )
            ProDialTab.FOCUS -> ProSliderRow(
                label = "手动对焦",
                value = focus.focusDistanceDiopters,
                range = 0f..1f,
                display = if (focus.manualFocus) "%.2f".format(focus.focusDistanceDiopters) else "AF 自动",
                onChange = { onFocus(focus.copy(manualFocus = true, focusDistanceDiopters = it)) },
            )
            ProDialTab.METERING -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val modes = listOf(
                    MeteringMode.CENTER_WEIGHTED to "中央重点",
                    MeteringMode.MATRIX to "全局测光",
                    MeteringMode.SPOT to "点测光",
                    MeteringMode.FACE_PRIORITY to "人像优先",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    modes.forEach { (mode, label) ->
                        OphotoChip(
                            label = label,
                            selected = exposure.meteringMode == mode,
                            onClick = { onExposure(exposure.copy(meteringMode = mode)) },
                        )
                    }
                }
                Text(
                    "点测光只读取画面中心区域；人像优先会自动保护面部亮度。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            ProDialTab.WB -> ProSliderRow(
                label = "色温",
                value = wb.manualKelvin.toFloat(),
                range = 2000f..8000f,
                display = "${wb.manualKelvin}K",
                onChange = { onWb(wb.copy(mode = WhiteBalanceMode.MANUAL, manualKelvin = it.roundToInt())) },
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ProSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(84.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(display, style = com.ophoto.ui.theme.ReadingLargeStyle, color = BrassGold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = BrassGold,
                activeTrackColor = BrassGold,
                inactiveTrackColor = Color(0x33FFFFFF),
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 兼容组件：其它页面仍在使用的旧「HUD 芯片」。
 * 现统一渲染为药丸芯片（选中金底），保证全应用只有一种控制态语言。
 */
@Composable
fun TopHudChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    OphotoChip(label = label, selected = active, onClick = onClick)
}
