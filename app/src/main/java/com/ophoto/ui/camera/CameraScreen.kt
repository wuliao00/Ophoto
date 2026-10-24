package com.ophoto.ui.camera

import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ophoto.OphotoContainer
import com.ophoto.core.permission.CameraPermissionHelper
import com.ophoto.core.storage.ExportImageFormat
import com.ophoto.core.storage.MediaStoreSaver
import com.ophoto.data.local.CompositionGuideType
import com.ophoto.data.local.UserSettings
import com.ophoto.data.local.VolumeKeyAction
import com.ophoto.data.local.entity.CaptureRecordEntity
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.engine.camera.CameraCaptureMode
import com.ophoto.engine.camera.CameraLiveTelemetry
import com.ophoto.engine.camera.CaptureConfig
import com.ophoto.engine.camera.ExposureConfig
import com.ophoto.engine.camera.FlashSetting
import com.ophoto.engine.camera.FocusConfig
import com.ophoto.engine.camera.MeteringMode
import com.ophoto.engine.camera.VideoConfig
import com.ophoto.engine.camera.ViewfinderAspectRatio
import com.ophoto.engine.camera.WhiteBalanceConfig
import com.ophoto.engine.camera.WhiteBalanceMode
import com.ophoto.engine.filter.FilterParams
import com.ophoto.engine.filter.FilterPreset
import com.ophoto.engine.metadata.CaptureExifMetadata
import com.ophoto.engine.metadata.MetadataEngine
import com.ophoto.engine.render.FocusPeakingColor
import com.ophoto.engine.render.GlLutPreviewView
import com.ophoto.ui.theme.AlertCrimson
import com.ophoto.ui.theme.BorderHighlight
import com.ophoto.ui.theme.BorderSubtle
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.CyanTelemetry
import com.ophoto.ui.theme.DeckGradientBottom
import com.ophoto.ui.theme.DeckGradientTop
import com.ophoto.ui.theme.GlassScrim
import com.ophoto.ui.theme.GlassScrimHeavy
import com.ophoto.ui.theme.GlowBrass
import com.ophoto.ui.theme.GlowOrange
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.ShutterOrange
import com.ophoto.ui.theme.SurfaceRaised
import com.ophoto.ui.theme.SurfaceRecessed
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextOnAccent
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ProDialTab(val shortCode: String, val title: String) {
    EV("EV", "曝光补偿"),
    ISO("ISO", "感光度 ISO"),
    SHUTTER("S", "快门速度"),
    WB("WB", "白平衡色温"),
    FOCUS("AF", "对焦方式"),
    METERING("测光", "测光权重"),
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
    0.5,
    1.0,
    2.0,
    4.0,
    8.0,
    15.0,
    30.0,
)

@Composable
fun CameraScreen(
    container: OphotoContainer,
    onJumpToEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by container.preferences.settingsFlow.collectAsState(initial = UserSettings())
    val presets by container.filterRepository.allPresetsFlow.collectAsState(initial = BuiltInPresets.ALL)
    val liveTelemetry by container.cameraEngine.liveTelemetryFlow.collectAsState(initial = CameraLiveTelemetry())

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

    var captureMode by remember { mutableStateOf(CameraCaptureMode.PRO) }
    var flashSetting by remember { mutableStateOf(FlashSetting.OFF) }
    var aspectRatio by remember { mutableStateOf(ViewfinderAspectRatio.RATIO_4_3) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var burstCount by remember { mutableIntStateOf(1) }
    var bracketMode by remember { mutableStateOf(false) }
    var peakingColor by remember { mutableStateOf(FocusPeakingColor.OFF) }

    // Mutually exclusive drawers so viewfinder stays expansive & uncluttered
    var showAssistPopover by remember { mutableStateOf(false) }
    var showFilterDrawer by remember { mutableStateOf(false) }
    var activeProDial by remember { mutableStateOf<ProDialTab?>(null) }

    // Default to pure optical output ("原片直出") in Pro/Photo mode so brightness & colors are 100% accurate
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    var filterStrength by remember { mutableFloatStateOf(settings.defaultFilterStrength) }
    var exposureConfig by remember { mutableStateOf(ExposureConfig()) }
    var focusConfig by remember { mutableStateOf(FocusConfig()) }
    var wbConfig by remember { mutableStateOf(WhiteBalanceConfig()) }

    var activeCameraId by remember {
        mutableStateOf(container.hardwareProfile.lenses.firstOrNull { !it.isFrontFacing }?.cameraId ?: "0")
    }
    val currentCameraIdState by rememberUpdatedState(activeCameraId)
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var activeFieldBanner by remember { mutableStateOf<String?>(null) }
    var retainedGlView by remember { mutableStateOf<GlLutPreviewView?>(null) }

    // Keep VideoCapture unbound unless in VIDEO mode so low-light exposure isn't clamped to 1/30s
    LaunchedEffect(captureMode) {
        container.cameraEngine.setVideoModeEnabled(captureMode == CameraCaptureMode.VIDEO)
        if (captureMode != CameraCaptureMode.PRO && captureMode != CameraCaptureMode.LONG_EXPOSURE) {
            activeProDial = null
            if (exposureConfig.isAnyManual) {
                val reset = exposureConfig.copy(manualMode = false, manualIso = false, manualShutter = false)
                exposureConfig = reset
                container.cameraEngine.setExposure(reset)
            }
        }
    }

    // Properly resume/pause GLSurfaceView across lifecycle transitions
    DisposableEffect(lifecycleOwner, retainedGlView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> retainedGlView?.onResume()
                Lifecycle.Event.ON_PAUSE -> retainedGlView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activePreset: FilterPreset? = remember(presets, selectedPresetId) {
        presets.firstOrNull { it.id == selectedPresetId }
    }

    // IMPORTANT: Only load a 3D LUT texture when the preset actually references an external .cube file!
    // Built-in parametric presets already run their full color recipe in GlFilterShaderProgram / FilterEngineImpl;
    // synthesizing a LUT on top would double-apply contrast & shadow tints and crush brightness!
    val activeLut = remember(activePreset?.id, activePreset?.lutPath) {
        val preset = activePreset
        if (preset != null && !preset.lutPath.isNullOrBlank()) {
            container.filterEngine.loadFilter(preset)
            container.filterEngine.currentLut()
        } else {
            null
        }
    }

    val proKelvinWarmth = remember(wbConfig) {
        if (wbConfig.mode == WhiteBalanceMode.MANUAL) {
            ((wbConfig.manualKelvin - 5500) / 4500f * 0.22f).coerceIn(-0.28f, 0.28f)
        } else {
            0f
        }
    }

    // Receive 1-click camera preset/composition setup from Field Assistant or LUT Library
    LaunchedEffect(container) {
        container.fieldSetupCommands.collect { cmd ->
            selectedPresetId = cmd.presetId
            val nextExp = exposureConfig.copy(
                manualMode = false,
                manualIso = false,
                manualShutter = false,
                evCompensation = cmd.evOffset,
            )
            exposureConfig = nextExp
            container.cameraEngine.setExposure(nextExp)

            val nextWb = if (cmd.applyManualWb) {
                WhiteBalanceConfig(mode = WhiteBalanceMode.MANUAL, manualKelvin = cmd.kelvin)
            } else {
                WhiteBalanceConfig(mode = WhiteBalanceMode.AUTO, manualKelvin = 5500)
            }
            wbConfig = nextWb
            container.cameraEngine.setWhiteBalance(nextWb)

            zoomRatio = when {
                cmd.focalMmHint <= 18 -> 0.6f
                cmd.focalMmHint in 35..58 -> 2.0f
                cmd.focalMmHint >= 59 -> 3.0f
                else -> 1.0f
            }
            container.cameraEngine.setZoomRatio(zoomRatio)
            container.preferences.update { it.copy(compositionGuide = cmd.compositionGuide) }
            activeFieldBanner = cmd.tipBanner
        }
    }

    // Synthetic studio preview & fallback histogram when camera permission is not yet granted
    val miniThumbSample = remember { BuiltInPresets.createSamplePreviewBitmap(128, 96) }
    val baseSampleBitmap = remember { BuiltInPresets.createSamplePreviewBitmap(640, 480) }
    var simulatedPreviewBitmap by remember { mutableStateOf(baseSampleBitmap) }
    var histogramData by remember { mutableStateOf(container.filterEngine.computeHistogram(baseSampleBitmap)) }
    var lastCapturedThumb by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(activePreset, filterStrength, exposureConfig.evCompensation, proKelvinWarmth, hasCameraPerm) {
        if (!hasCameraPerm) {
            withContext(Dispatchers.Default) {
                val base = activePreset ?: FilterPreset(id = "neutral", name = "原片")
                val combined = base.copy(
                    params = base.params.copy(
                        exposureEv = (base.params.exposureEv + exposureConfig.evCompensation).coerceIn(-2f, 2f),
                        temperature = (base.params.temperature + (proKelvinWarmth * 350f).roundToInt()).coerceIn(-100, 100),
                    ),
                )
                container.filterEngine.loadFilterWithExplicitLut(combined, activeLut)
                container.filterEngine.setStrength(if (activePreset == null) 1f else filterStrength)
                val graded = container.filterEngine.applyToImage(baseSampleBitmap)
                val hist = container.filterEngine.computeHistogram(graded)
                simulatedPreviewBitmap = graded
                histogramData = hist
            }
        }
    }

    val rollDegrees = rememberDeviceRollDegrees(enabled = settings.showLeveler)

    fun resetAllProToAuto() {
        val resetExp = ExposureConfig()
        val resetFocus = FocusConfig()
        val resetWb = WhiteBalanceConfig()
        exposureConfig = resetExp
        focusConfig = resetFocus
        wbConfig = resetWb
        container.cameraEngine.setExposure(resetExp)
        container.cameraEngine.setFocus(resetFocus)
        container.cameraEngine.setWhiteBalance(resetWb)
    }

    fun triggerCapture() {
        if (!hasCameraPerm) {
            permLauncher.launch(CameraPermissionHelper.requiredCameraPermissions(includeAudio = false).toTypedArray())
            return
        }
        scope.launch {
            if (captureMode == CameraCaptureMode.VIDEO) {
                if (isRecordingVideo) {
                    val videoRes = container.cameraEngine.stopVideoRecord()
                    isRecordingVideo = false
                    Toast.makeText(
                        context,
                        "视频已保存 (${videoRes.durationMillis / 1000}s)",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    isRecordingVideo = true
                    container.cameraEngine.startVideoRecord(
                        VideoConfig(
                            qualityTag = settings.videoResolutionTag,
                            enableAudio = CameraPermissionHelper.hasAudioPermission(context),
                            stabilization = settings.videoStabilization,
                        ),
                    )
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
                exposure = exposureConfig,
                focus = focusConfig,
                whiteBalance = wbConfig,
                filterId = selectedPresetId,
                filterStrength = filterStrength,
                timerSeconds = timerSeconds,
                burstCount = burstCount,
                bracketStops = bracketMode,
            )

            val rawCapture = container.cameraEngine.takePhoto(config).bitmap ?: simulatedPreviewBitmap

            val finalBitmap = withContext(Dispatchers.Default) {
                val cropped = cropBitmapToAspect(rawCapture, aspectRatio)
                val preset = activePreset
                val hasManualWb = abs(proKelvinWarmth) > 0.004f
                when {
                    preset != null -> {
                        val adjustedPreset = if (hasManualWb) {
                            preset.copy(
                                params = preset.params.copy(
                                    temperature = (preset.params.temperature + (proKelvinWarmth * 350f).roundToInt())
                                        .coerceIn(-100, 100),
                                ),
                            )
                        } else {
                            preset
                        }
                        container.filterEngine.loadFilterWithExplicitLut(adjustedPreset, activeLut)
                        container.filterEngine.setStrength(filterStrength)
                        val out = container.filterEngine.applyToImage(cropped)
                        if (cropped !== rawCapture) cropped.recycle()
                        out
                    }
                    hasManualWb -> {
                        val wbOnlyPreset = FilterPreset(
                            id = "wb_only",
                            name = "WB",
                            params = FilterParams(
                                temperature = (proKelvinWarmth * 350f).roundToInt().coerceIn(-100, 100),
                            ),
                        )
                        container.filterEngine.loadFilterWithExplicitLut(wbOnlyPreset, null)
                        container.filterEngine.setStrength(1f)
                        val out = container.filterEngine.applyToImage(cropped)
                        if (cropped !== rawCapture) cropped.recycle()
                        out
                    }
                    cropped !== rawCapture -> cropped
                    else -> rawCapture.copy(Bitmap.Config.ARGB_8888, false)
                }
            }

            lastCapturedThumb = finalBitmap
            val effectiveIso = if (exposureConfig.isIsoManualEffective) exposureConfig.iso else liveTelemetry.iso
            val effectiveShutter = if (exposureConfig.isShutterManualEffective) {
                exposureConfig.shutterSeconds
            } else {
                liveTelemetry.shutterSeconds
            }
            val eqFocal = (24f * zoomRatio).roundToInt()
            val exif = CaptureExifMetadata(
                make = container.hardwareProfile.brand.ifBlank { "OPPO" },
                model = container.hardwareProfile.displayTitle,
                iso = effectiveIso,
                exposureTimeSec = effectiveShutter,
                aperture = liveTelemetry.aperture,
                focalLengthMm = 5.6f * zoomRatio,
                focalLength35mm = eqFocal,
                kelvin = wbConfig.manualKelvin,
                filterName = activePreset?.name,
                filterStrength = if (activePreset == null) 0f else filterStrength,
            )
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
                exifMetadata = exif,
                stripLocation = !settings.saveGpsLocation,
            )
            val uriStr = savedUri?.toString() ?: "memory://$fileBase.jpg"
            container.mediaRepository.recordCapture(
                CaptureRecordEntity(
                    filePath = uriStr,
                    rawPath = if (settings.enableRawWhenSupported) "$fileBase.dng" else null,
                    thumbnailPath = uriStr,
                    captureMode = captureMode.label,
                    cameraId = activeCameraId,
                    iso = effectiveIso,
                    shutter = MetadataEngine.formatShutterSpeed(effectiveShutter),
                    ev = exposureConfig.evCompensation,
                    wb = wbConfig.manualKelvin,
                    focalLength = eqFocal,
                    gpsEnabled = settings.saveGpsLocation,
                    filterId = selectedPresetId,
                    filterStrength = if (activePreset == null) 0f else filterStrength,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            Toast.makeText(
                context,
                "已保存 ${activePreset?.name ?: "原片"} · ${exif.summaryLine()}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Hardware Volume Key mapping
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
                    val next = exposureConfig.copy(
                        evCompensation = (exposureConfig.evCompensation + delta).coerceIn(-2f, 2f),
                    )
                    exposureConfig = next
                    container.cameraEngine.setExposure(next)
                }
            }
        }
    }

    val isProFamily = captureMode == CameraCaptureMode.PRO || captureMode == CameraCaptureMode.LONG_EXPOSURE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // ══════════════════════════════════════════════════════════════════
        // ZONE 1: SLIM FLAGSHIP OPTICAL STATUS HEADER (42dp)
        // ══════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceRecessed)
                .border(0.7.dp, HairlineBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Flash Cycle + Aspect Ratio Pill
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val flashIcon = when (flashSetting) {
                    FlashSetting.OFF -> Icons.Rounded.FlashOff
                    FlashSetting.AUTO -> Icons.Rounded.FlashAuto
                    FlashSetting.ON -> Icons.Rounded.FlashOn
                    FlashSetting.TORCH -> Icons.Rounded.Highlight
                }
                val flashActive = flashSetting != FlashSetting.OFF
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (flashActive) BrassGold.copy(alpha = 0.18f) else ControlPillBg)
                        .clickable {
                            val all = FlashSetting.entries
                            flashSetting = all[(all.indexOf(flashSetting) + 1) % all.size]
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "闪光灯",
                        tint = if (flashActive) BrassGold else TextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                    if (flashActive) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = flashSetting.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrassGold,
                        )
                    }
                }

                // Aspect Ratio Cycle Pill
                val ratioHighlight = aspectRatio != ViewfinderAspectRatio.RATIO_4_3
                Text(
                    text = aspectRatio.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ratioHighlight) BrassGold else TextPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (ratioHighlight) BrassGold.copy(alpha = 0.18f) else ControlPillBg)
                        .clickable {
                            val all = ViewfinderAspectRatio.entries
                            aspectRatio = all[(all.indexOf(aspectRatio) + 1) % all.size]
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }

            // Center: Optical Mode / Active Film / RAW Status Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (settings.enableRawWhenSupported) {
                    Text(
                        text = "RAW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextOnAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(BrassGold)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                if (timerSeconds > 0) {
                    Text(
                        text = "${timerSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanTelemetry,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyanTelemetry.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (bracketMode || burstCount > 1) {
                    Text(
                        text = if (bracketMode) "AEB" else "${burstCount}连拍",
                        style = MaterialTheme.typography.labelSmall,
                        color = ShutterOrange,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ShutterOrange.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                activePreset?.let { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(OpticalEmerald.copy(alpha = 0.15f))
                            .clickable {
                                showFilterDrawer = !showFilterDrawer
                                if (showFilterDrawer) activeProDial = null
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(OpticalEmerald),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = OpticalEmerald,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Right: Timer Quick Cycle + Viewfinder Assist Drawer Toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (timerSeconds > 0) BrassGold.copy(alpha = 0.18f) else ControlPillBg)
                        .clickable {
                            timerSeconds = when (timerSeconds) {
                                0 -> 3
                                3 -> 10
                                else -> 0
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = "定时拍照",
                        tint = if (timerSeconds > 0) BrassGold else TextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }

                val anyAssistCustom = showAssistPopover ||
                    peakingColor != FocusPeakingColor.OFF ||
                    settings.showZebraStripes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (anyAssistCustom) BrassGold else ControlPillBg)
                        .clickable { showAssistPopover = !showAssistPopover }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "取景辅助开关",
                        tint = if (anyAssistCustom) TextOnAccent else TextPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "辅助",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (anyAssistCustom) TextOnAccent else TextPrimary,
                    )
                }
            }
        }

        // Optional Field Assistant Banner
        activeFieldBanner?.let { tip ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF13221D))
                    .border(0.8.dp, OpticalEmerald.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .clickable { activeFieldBanner = null }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "✦ 现场配方：$tip",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpticalEmerald,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭提示",
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // ZONE 2: IMMERSIVE PORTRAIT OPTICAL VIEWFINDER (weight 1f)
        // ══════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF040507))
                .border(0.9.dp, BorderHighlight, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val isXpan = aspectRatio == ViewfinderAspectRatio.RATIO_21_9
            val vfModifier = if (isXpan) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.portraitRatio)
            } else {
                Modifier
                    .aspectRatio(
                        aspectRatio.portraitRatio.coerceIn(0.50f, 1.2f),
                        matchHeightConstraintsFirst = true,
                    )
                    .fillMaxSize()
            }

            Box(
                modifier = vfModifier
                    .clip(RoundedCornerShape(if (isXpan) 4.dp else 14.dp))
                    .pointerInput(settings.touchToCapture) {
                        detectTapGestures { offset ->
                            showAssistPopover = false
                            val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val nextFocus = focusConfig.copy(
                                manualFocus = false,
                                normalizedTapX = nx,
                                normalizedTapY = ny,
                            )
                            focusConfig = nextFocus
                            container.cameraEngine.setFocus(nextFocus)
                            if (settings.touchToCapture) {
                                triggerCapture()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaEv = (-dragAmount / size.height.coerceAtLeast(1).toFloat()) * 3.0f
                            val nextEv = (exposureConfig.evCompensation + deltaEv).coerceIn(-2.0f, 2.0f)
                            val updated = exposureConfig.copy(evCompensation = nextEv)
                            exposureConfig = updated
                            container.cameraEngine.setExposure(updated)
                        }
                    },
            ) {
                if (hasCameraPerm) {
                    AndroidView(
                        factory = { ctx ->
                            GlLutPreviewView(ctx).also { view ->
                                retainedGlView = view
                                view.setSurfaceProviderListener { surfaceProvider ->
                                    scope.launch {
                                        container.cameraEngine.bindSession(
                                            lifecycleOwner = lifecycleOwner,
                                            surfaceProvider = surfaceProvider,
                                            cameraId = currentCameraIdState,
                                        )
                                    }
                                }
                            }
                        },
                        update = { glView ->
                            glView.setHistogramListener(
                                if (settings.showHistogram) {
                                    { liveHist -> histogramData = liveHist }
                                } else {
                                    null
                                },
                            )
                            glView.updateRenderState(
                                params = activePreset?.params ?: FilterParams(),
                                strength = if (activePreset == null) 0f else filterStrength,
                                lut = activeLut,
                                peaking = peakingColor,
                                peakingLevel = 0.6f,
                                zebra = settings.showZebraStripes,
                                proKelvinWarmth = proKelvinWarmth,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        bitmap = simulatedPreviewBitmap.asImageBitmap(),
                        contentDescription = "实时滤镜仿真预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(18.dp))
                            .background(GlassScrimHeavy)
                            .border(1.dp, BrassGold.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .clickable {
                                permLauncher.launch(
                                    CameraPermissionHelper.requiredCameraPermissions(includeAudio = false).toTypedArray(),
                                )
                            }
                            .padding(horizontal = 22.dp, vertical = 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "授权摄像头",
                            tint = BrassGold,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "点击开启真机光学取景器",
                            style = MaterialTheme.typography.titleSmall,
                            color = BrassGold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "授权摄像头后即刻激活多摄与 60fps 实时调色",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }

                // Optical Guides, Corner Reticles, Horizon Leveler & AF Bracket
                CompositionOverlayCanvas(
                    guideType = settings.compositionGuide,
                    showLeveler = settings.showLeveler,
                    rollDegrees = rollDegrees,
                    focusTapNormalized = focusConfig.normalizedTapX?.let { x ->
                        focusConfig.normalizedTapY?.let { y -> x to y }
                    },
                    showCornerBrackets = true,
                )
            }

            // XPan 65:24 Cinema Frame Engraving
            if (isXpan) {
                Text(
                    text = "65:24  PANORAMIC  FORMAT",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrassGold.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                )
            }

            // Top-Left: Live GPU-Sampled RGB/Luma Histogram Scope
            if (settings.showHistogram) {
                LiveHistogramHud(
                    histogram = histogramData,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                )
            }

            // Top-Right: Smart Underexposure Safety Alert (1-tap recovery when manual combo is too dark!)
            val manualUnderExposed = exposureConfig.isAnyManual && liveTelemetry.exposureDeltaEv < -1.5f
            if (manualUnderExposed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xDD2B1410))
                        .border(1.dp, ShutterOrange, RoundedCornerShape(10.dp))
                        .clickable { resetAllProToAuto() }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = ShutterOrange,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "曝光偏暗 %.1fEV · 点此恢复自动", liveTelemetry.exposureDeltaEv),
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassGold,
                    )
                }
            } else if (settings.showParameterHud && !isProFamily) {
                // Clean subtle telemetry pill in non-Pro modes (Pro mode already has the dedicated 5-cell telemetry deck!)
                val shutterStr = MetadataEngine.formatShutterSpeed(liveTelemetry.shutterSeconds)
                val evStr = String.format(Locale.US, "%+.1fEV", exposureConfig.evCompensation)
                Text(
                    text = "${(24 * zoomRatio).roundToInt()}mm · $shutterStr · ISO ${liveTelemetry.iso} · $evStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassScrim)
                        .border(0.6.dp, BorderHighlight, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Floating Assist Popover (Appears over Top of Viewfinder ONLY when toggled)
            if (showAssistPopover) {
                ViewfinderAssistOverlayTray(
                    settings = settings,
                    peakingColor = peakingColor,
                    bracketMode = bracketMode,
                    burstCount = burstCount,
                    onCycleGuide = {
                        val all = CompositionGuideType.entries
                        val next = all[(all.indexOf(settings.compositionGuide) + 1) % all.size]
                        scope.launch { container.preferences.update { it.copy(compositionGuide = next) } }
                    },
                    onToggleLeveler = {
                        scope.launch { container.preferences.update { it.copy(showLeveler = !it.showLeveler) } }
                    },
                    onToggleHistogram = {
                        scope.launch { container.preferences.update { it.copy(showHistogram = !it.showHistogram) } }
                    },
                    onCyclePeaking = {
                        val all = FocusPeakingColor.entries
                        peakingColor = all[(all.indexOf(peakingColor) + 1) % all.size]
                    },
                    onToggleZebra = {
                        scope.launch { container.preferences.update { it.copy(showZebraStripes = !it.showZebraStripes) } }
                    },
                    onCycleDrive = {
                        when {
                            !bracketMode && burstCount == 1 -> burstCount = 5
                            burstCount > 1 -> {
                                burstCount = 1
                                bracketMode = true
                            }
                            else -> bracketMode = false
                        }
                    },
                    onToggleRaw = {
                        scope.launch {
                            container.preferences.update { it.copy(enableRawWhenSupported = !it.enableRawWhenSupported) }
                        }
                    },
                    onClose = { showAssistPopover = false },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }

            // Bottom-Center of Viewfinder: Flagship Optical Focal-Length Capsule (0.6x / 1x 24mm / 2x / 3x / 6x)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(CircleShape)
                    .background(GlassScrim)
                    .border(0.8.dp, BorderHighlight, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val zoomStops = listOf(
                    0.6f to "16mm",
                    1.0f to "24mm",
                    2.0f to "48mm",
                    3.0f to "70mm",
                    6.0f to "144mm",
                )
                zoomStops.forEach { (z, focalTag) ->
                    val active = abs(zoomRatio - z) < 0.14f
                    val zText = if (z < 1f) "0.6" else "${z.toInt()}"
                    Text(
                        text = if (active) "${zText}× $focalTag" else zText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) TextOnAccent else TextPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) BrassGold else Color.Transparent)
                            .clickable {
                                zoomRatio = z
                                container.cameraEngine.setZoomRatio(z)
                            }
                            .padding(horizontal = if (active) 10.dp else 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Countdown Overlay
            if (countdownRemaining > 0) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(GlassScrimHeavy)
                        .border(2.dp, BrassGold, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = countdownRemaining.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrassGold,
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // ZONE 3: MUTUALLY EXCLUSIVE INSTRUMENT DECK (NEVER DOUBLE-STACKED!)
        // ══════════════════════════════════════════════════════════════════
        if (showFilterDrawer) {
            FilmLutDrawerPanel(
                presets = presets,
                selectedPresetId = selectedPresetId,
                activePreset = activePreset,
                filterStrength = filterStrength,
                miniThumbSample = miniThumbSample,
                onSelectPresetId = { selectedPresetId = it },
                onStrengthChange = { filterStrength = it },
                onClose = { showFilterDrawer = false },
            )
        } else if (isProFamily) {
            ProTelemetryDeck(
                activeTab = activeProDial,
                onSelectTab = { clicked ->
                    activeProDial = if (activeProDial == clicked) null else clicked
                },
                liveTelemetry = liveTelemetry,
                exposure = exposureConfig,
                focus = focusConfig,
                wb = wbConfig,
                onUpdateExposure = {
                    exposureConfig = it
                    container.cameraEngine.setExposure(it)
                },
                onUpdateFocus = {
                    focusConfig = it
                    container.cameraEngine.setFocus(it)
                },
                onUpdateWb = {
                    wbConfig = it
                    container.cameraEngine.setWhiteBalance(it)
                },
                onResetAllAuto = { resetAllProToAuto() },
            )
        }

        // ══════════════════════════════════════════════════════════════════
        // ZONE 4: OPTICAL MODE STRIP + MACHINED SHUTTER DECK
        // ══════════════════════════════════════════════════════════════════
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(CameraCaptureMode.entries) { mode ->
                val selected = mode == captureMode
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { captureMode = mode }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) BrassGold else TextMuted,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (selected) ShutterOrange else Color.Transparent),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Darkroom / Gallery Thumbnail Well
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(SurfaceRaised)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(13.dp))
                    .clickable { onJumpToEditor() },
                contentAlignment = Alignment.Center,
            ) {
                val thumb = lastCapturedThumb
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "进入暗房",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = "进入暗房编辑",
                        tint = BrassGold,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            // Center: Precision Machined Two-Stage Shutter Button
            val outerRingColor = when {
                isRecordingVideo -> AlertCrimson
                captureMode == CameraCaptureMode.VIDEO -> ShutterOrange
                isProFamily -> BrassGold
                else -> TextPrimary
            }
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isRecordingVideo -> GlowOrange.copy(alpha = 0.35f)
                            isProFamily -> GlowBrass.copy(alpha = 0.20f)
                            else -> Color.White.copy(alpha = 0.08f)
                        },
                        CircleShape,
                    )
                    .border(2.6.dp, outerRingColor, CircleShape)
                    .clickable { triggerCapture() }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isRecordingVideo) 30.dp else 58.dp)
                        .clip(if (isRecordingVideo) RoundedCornerShape(8.dp) else CircleShape)
                        .background(
                            when {
                                isRecordingVideo -> AlertCrimson
                                captureMode == CameraCaptureMode.VIDEO -> ShutterOrange
                                else -> TextPrimary
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isProFamily && !isRecordingVideo) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .border(1.2.dp, ShutterOrange.copy(alpha = 0.55f), CircleShape),
                        )
                    }
                }
            }

            // Right: Film/LUT Toggle + Physical Camera Switch
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (showFilterDrawer || activePreset != null) BrassGold.copy(alpha = 0.20f) else SurfaceRaised)
                        .border(
                            1.dp,
                            if (showFilterDrawer || activePreset != null) BrassGold.copy(alpha = 0.7f) else HairlineBorder,
                            CircleShape,
                        )
                        .clickable {
                            showFilterDrawer = !showFilterDrawer
                            if (showFilterDrawer) activeProDial = null
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = "实时胶片调色",
                        tint = if (showFilterDrawer || activePreset != null) BrassGold else TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(
                    onClick = {
                        val lenses = container.hardwareProfile.lenses
                        val curFront = lenses.firstOrNull { it.cameraId == activeCameraId }?.isFrontFacing == true
                        val targetLens = lenses.firstOrNull { it.isFrontFacing != curFront }
                        val nextId = targetLens?.cameraId ?: if (activeCameraId == "0") "1" else "0"
                        activeCameraId = nextId
                        zoomRatio = 1.0f
                        scope.launch { container.cameraEngine.openCamera(nextId) }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceRaised, CircleShape)
                        .border(1.dp, HairlineBorder, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = "切换前后摄",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Floating Translucent Assist Tray inside the Viewfinder so auxiliary toggles never clutter the main screen.
 */
@Composable
private fun ViewfinderAssistOverlayTray(
    settings: UserSettings,
    peakingColor: FocusPeakingColor,
    bracketMode: Boolean,
    burstCount: Int,
    onCycleGuide: () -> Unit,
    onToggleLeveler: () -> Unit,
    onToggleHistogram: () -> Unit,
    onCyclePeaking: () -> Unit,
    onToggleZebra: () -> Unit,
    onCycleDrive: () -> Unit,
    onToggleRaw: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassScrimHeavy, shape)
            .border(0.9.dp, BrassGold.copy(alpha = 0.38f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "光学取景辅助与驱动模式",
                style = MaterialTheme.typography.labelMedium,
                color = BrassGold,
            )
            Text(
                text = "收起 ✕",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                TopHudChip(
                    label = "构图: ${settings.compositionGuide.label}",
                    active = settings.compositionGuide != CompositionGuideType.NONE,
                    onClick = onCycleGuide,
                )
            }
            item {
                TopHudChip(
                    label = "水平仪",
                    active = settings.showLeveler,
                    onClick = onToggleLeveler,
                )
            }
            item {
                TopHudChip(
                    label = "直方图",
                    active = settings.showHistogram,
                    onClick = onToggleHistogram,
                )
            }
            item {
                TopHudChip(
                    label = if (peakingColor == FocusPeakingColor.OFF) "峰值: 关" else peakingColor.label,
                    active = peakingColor != FocusPeakingColor.OFF,
                    onClick = onCyclePeaking,
                )
            }
            item {
                TopHudChip(
                    label = "斑马纹",
                    active = settings.showZebraStripes,
                    onClick = onToggleZebra,
                )
            }
            item {
                TopHudChip(
                    label = when {
                        bracketMode -> "包围 ±1.7EV"
                        burstCount > 1 -> "连拍 ${burstCount}张"
                        else -> "单张驱动"
                    },
                    active = bracketMode || burstCount > 1,
                    onClick = onCycleDrive,
                )
            }
            item {
                TopHudChip(
                    label = "RAW+JPG",
                    active = settings.enableRawWhenSupported,
                    onClick = onToggleRaw,
                )
            }
        }
    }
}

/**
 * Hasselblad / Leica-Inspired 5-Cell Live Telemetry Bar + Expandable Engraved Scale Dial.
 */
@Composable
private fun ProTelemetryDeck(
    activeTab: ProDialTab?,
    onSelectTab: (ProDialTab) -> Unit,
    liveTelemetry: CameraLiveTelemetry,
    exposure: ExposureConfig,
    focus: FocusConfig,
    wb: WhiteBalanceConfig,
    onUpdateExposure: (ExposureConfig) -> Unit,
    onUpdateFocus: (FocusConfig) -> Unit,
    onUpdateWb: (WhiteBalanceConfig) -> Unit,
    onResetAllAuto: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    val anyManualOverride = exposure.isAnyManual ||
        abs(exposure.evCompensation) > 0.05f ||
        exposure.aeLocked ||
        focus.manualFocus ||
        wb.mode != WhiteBalanceMode.AUTO

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(DeckGradientTop, DeckGradientBottom)), shape)
            .border(0.9.dp, HairlineBorder, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Expandable Precision Dial Drawer (ONLY shown when a parameter cell is tapped!)
        if (activeTab != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(SurfaceRecessed)
                    .border(0.7.dp, BorderSubtle, RoundedCornerShape(11.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activeTab.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = BrassGold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (anyManualOverride) {
                            Text(
                                text = "↺ 全部自动",
                                style = MaterialTheme.typography.labelSmall,
                                color = OpticalEmerald,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(OpticalEmerald.copy(alpha = 0.14f))
                                    .clickable(onClick = onResetAllAuto)
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                        TopHudChip(
                            label = if (exposure.meteringMode == MeteringMode.CENTER_WEIGHTED) {
                                "中央测光"
                            } else {
                                exposure.meteringMode.label
                            },
                            active = activeTab == ProDialTab.METERING,
                            onClick = { onSelectTab(ProDialTab.METERING) },
                        )
                    }
                }

                when (activeTab) {
                    ProDialTab.EV -> {
                        EngravedScaleMarks(
                            normalizedPos = ((exposure.evCompensation + 2f) / 4f).coerceIn(0f, 1f),
                            tickCount = 25,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TopHudChip(
                                label = "0.0",
                                active = abs(exposure.evCompensation) < 0.05f,
                                onClick = { onUpdateExposure(exposure.copy(evCompensation = 0f)) },
                            )
                            Slider(
                                value = exposure.evCompensation,
                                onValueChange = { onUpdateExposure(exposure.copy(evCompensation = it)) },
                                valueRange = -2.0f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = BrassGold,
                                    activeTrackColor = BrassGold,
                                    inactiveTrackColor = ControlPillBg,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp),
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f", exposure.evCompensation),
                                style = MaterialTheme.typography.labelMedium,
                                color = BrassGold,
                                modifier = Modifier.width(38.dp),
                            )
                        }
                    }

                    ProDialTab.ISO -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                TopHudChip(
                                    label = "AUTO (${liveTelemetry.ambientIso})",
                                    active = !exposure.isIsoManualEffective,
                                    onClick = {
                                        onUpdateExposure(
                                            exposure.copy(manualMode = false, manualIso = false),
                                        )
                                    },
                                )
                            }
                            items(ISO_STOPS) { isoVal ->
                                TopHudChip(
                                    label = isoVal.toString(),
                                    active = exposure.isIsoManualEffective && exposure.iso == isoVal,
                                    onClick = {
                                        onUpdateExposure(
                                            exposure.copy(
                                                manualMode = false,
                                                manualIso = true,
                                                iso = isoVal,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    ProDialTab.SHUTTER -> {
                        val ambShutterStr = MetadataEngine.formatShutterSpeed(
                            liveTelemetry.ambientExposureNs / 1_000_000_000.0,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                TopHudChip(
                                    label = "AUTO ($ambShutterStr)",
                                    active = !exposure.isShutterManualEffective,
                                    onClick = {
                                        onUpdateExposure(
                                            exposure.copy(manualMode = false, manualShutter = false),
                                        )
                                    },
                                )
                            }
                            items(SHUTTER_STOPS) { sec ->
                                val text = MetadataEngine.formatShutterSpeed(sec)
                                TopHudChip(
                                    label = text,
                                    active = exposure.isShutterManualEffective && abs(exposure.shutterSeconds - sec) < 1e-5,
                                    onClick = {
                                        onUpdateExposure(
                                            exposure.copy(
                                                manualMode = false,
                                                manualShutter = true,
                                                shutterSeconds = sec,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    ProDialTab.WB -> {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "${wb.manualKelvin}K",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrassGold,
                                    modifier = Modifier.width(54.dp),
                                )
                                Slider(
                                    value = wb.manualKelvin.toFloat(),
                                    onValueChange = { onUpdateWb(wb.copy(manualKelvin = it.roundToInt())) },
                                    valueRange = 2500f..10000f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = BrassGold,
                                        activeTrackColor = BrassGold,
                                        inactiveTrackColor = ControlPillBg,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp),
                                )
                            }
                        }
                    }

                    ProDialTab.FOCUS -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TopHudChip(
                                label = "AF 自动",
                                active = !focus.manualFocus,
                                onClick = { onUpdateFocus(focus.copy(manualFocus = false)) },
                            )
                            Slider(
                                value = focus.focusDistanceDiopters,
                                onValueChange = {
                                    onUpdateFocus(focus.copy(manualFocus = true, focusDistanceDiopters = it))
                                },
                                valueRange = 0f..10f,
                                colors = SliderDefaults.colors(
                                    thumbColor = BrassGold,
                                    activeTrackColor = BrassGold,
                                    inactiveTrackColor = ControlPillBg,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp),
                            )
                            Text(
                                text = if (focus.focusDistanceDiopters < 0.2f) {
                                    "∞ 远景"
                                } else {
                                    String.format(Locale.US, "%.1fdpt", focus.focusDistanceDiopters)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = BrassGold,
                            )
                        }
                    }

                    ProDialTab.METERING -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

        // Primary 5-Cell Telemetry Readout Row + AE-L Lock
        val displayIso = if (exposure.isIsoManualEffective) exposure.iso else liveTelemetry.iso
        val displayShutter = MetadataEngine.formatShutterSpeed(
            if (exposure.isShutterManualEffective) exposure.shutterSeconds else liveTelemetry.shutterSeconds,
        )
        val displayWb = when (wb.mode) {
            WhiteBalanceMode.AUTO -> "AWB"
            WhiteBalanceMode.MANUAL -> "${wb.manualKelvin}K"
            else -> wb.mode.label
        }
        val displayFocus = if (focus.manualFocus) {
            if (focus.focusDistanceDiopters < 0.2f) "MF ∞" else String.format(Locale.US, "%.1f", focus.focusDistanceDiopters)
        } else {
            "AF-C"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProTelemetryCell(
                code = "EV",
                value = String.format(Locale.US, "%+.1f", exposure.evCompensation),
                isManual = abs(exposure.evCompensation) > 0.05f,
                selected = activeTab == ProDialTab.EV,
                onClick = { onSelectTab(ProDialTab.EV) },
                modifier = Modifier.weight(1f),
            )
            ProTelemetryCell(
                code = "ISO",
                value = displayIso.toString(),
                isManual = exposure.isIsoManualEffective,
                selected = activeTab == ProDialTab.ISO,
                onClick = { onSelectTab(ProDialTab.ISO) },
                modifier = Modifier.weight(1f),
            )
            ProTelemetryCell(
                code = "S",
                value = displayShutter,
                isManual = exposure.isShutterManualEffective,
                selected = activeTab == ProDialTab.SHUTTER,
                onClick = { onSelectTab(ProDialTab.SHUTTER) },
                modifier = Modifier.weight(1.1f),
            )
            ProTelemetryCell(
                code = "WB",
                value = displayWb,
                isManual = wb.mode != WhiteBalanceMode.AUTO,
                selected = activeTab == ProDialTab.WB,
                onClick = { onSelectTab(ProDialTab.WB) },
                modifier = Modifier.weight(1f),
            )
            ProTelemetryCell(
                code = "FOCUS",
                value = displayFocus,
                isManual = focus.manualFocus,
                selected = activeTab == ProDialTab.FOCUS,
                onClick = { onSelectTab(ProDialTab.FOCUS) },
                modifier = Modifier.weight(1f),
            )

            // Compact AE-L Lock Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (exposure.aeLocked) ShutterOrange.copy(alpha = 0.22f) else SurfaceRecessed)
                    .border(
                        0.8.dp,
                        if (exposure.aeLocked) ShutterOrange else BorderSubtle,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onUpdateExposure(exposure.copy(aeLocked = !exposure.aeLocked)) }
                    .padding(horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = if (exposure.aeLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = "曝光锁定",
                    tint = if (exposure.aeLocked) ShutterOrange else TextMuted,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "AE-L",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exposure.aeLocked) ShutterOrange else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun ProTelemetryCell(
    code: String,
    value: String,
    isManual: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(if (selected) BrassGold.copy(alpha = 0.16f) else SurfaceRecessed, shape)
            .border(
                width = if (selected) 1.dp else 0.7.dp,
                color = if (selected) BrassGold else BorderSubtle,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) BrassGold else TextMuted,
            )
            Spacer(Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(if (isManual) ShutterOrange else OpticalEmerald.copy(alpha = 0.65f)),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isManual || selected) BrassGold else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Engraved optical ruler tick marks rendered above sliders for authentic camera dial tactility.
 */
@Composable
private fun EngravedScaleMarks(
    normalizedPos: Float,
    tickCount: Int = 25,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .padding(horizontal = 8.dp),
    ) {
        val step = size.width / (tickCount - 1).coerceAtLeast(1)
        for (i in 0 until tickCount) {
            val x = i * step
            val isMajor = i % 6 == 0 || i == tickCount / 2
            val top = if (isMajor) 1.dp.toPx() else 4.dp.toPx()
            drawLine(
                color = if (i == tickCount / 2) BrassGold else Color.White.copy(alpha = if (isMajor) 0.45f else 0.20f),
                start = Offset(x, top),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 1.3.dp.toPx() else 0.9.dp.toPx(),
            )
        }
        val markerX = normalizedPos.coerceIn(0f, 1f) * size.width
        drawCircle(
            color = ShutterOrange,
            radius = 2.8.dp.toPx(),
            center = Offset(markerX, 3.dp.toPx()),
        )
    }
}

/**
 * Clean Film Emulsion / 3D LUT Drawer with live color-graded preview tiles & strength slider.
 */
@Composable
private fun FilmLutDrawerPanel(
    presets: List<FilterPreset>,
    selectedPresetId: String?,
    activePreset: FilterPreset?,
    filterStrength: Float,
    miniThumbSample: Bitmap,
    onSelectPresetId: (String?) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(DeckGradientTop, DeckGradientBottom)), shape)
            .border(0.9.dp, BrassGold.copy(alpha = 0.40f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "胶片色彩 · ${activePreset?.name ?: "原片直出"}",
                style = MaterialTheme.typography.labelMedium,
                color = BrassGold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activePreset != null) {
                    Text(
                        text = "${(filterStrength * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassGold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Slider(
                        value = filterStrength,
                        onValueChange = onStrengthChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = BrassGold,
                            activeTrackColor = BrassGold,
                            inactiveTrackColor = ControlPillBg,
                        ),
                        modifier = Modifier
                            .width(120.dp)
                            .height(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "收起 ✕",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilmSwatchCard(
                    name = "原片直出",
                    previewBitmap = miniThumbSample,
                    accent = TextSecondary,
                    selected = selectedPresetId == null,
                    onClick = { onSelectPresetId(null) },
                )
            }
            items(presets, key = { it.id }) { preset ->
                val gradedThumb = remember(preset.id, preset.params) {
                    val engine = com.ophoto.engine.filter.FilterEngineImpl()
                    engine.loadFilter(preset)
                    engine.setStrength(preset.defaultStrength)
                    engine.applyToImage(miniThumbSample)
                }
                val parsedAccent = remember(preset.accentHex) {
                    runCatching { Color(android.graphics.Color.parseColor(preset.accentHex)) }
                        .getOrDefault(BrassGold)
                }
                FilmSwatchCard(
                    name = preset.name,
                    previewBitmap = gradedThumb,
                    accent = parsedAccent,
                    selected = selectedPresetId == preset.id,
                    onClick = { onSelectPresetId(preset.id) },
                )
            }
        }
    }
}

@Composable
private fun FilmSwatchCard(
    name: String,
    previewBitmap: Bitmap,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.20f) else SurfaceRecessed, shape)
            .border(if (selected) 1.3.dp else 0.7.dp, if (selected) accent else BorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Image(
            bitmap = previewBitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(7.dp)),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TopHudChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) TextOnAccent else TextPrimary,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (active) BrassGold else ControlPillBg, shape)
            .border(0.7.dp, if (active) BrassGold else BorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun cropBitmapToAspect(source: Bitmap, aspect: ViewfinderAspectRatio): Bitmap {
    if (source.width <= 4 || source.height <= 4) return source
    val targetRatio = if (aspect == ViewfinderAspectRatio.RATIO_21_9) {
        aspect.portraitRatio
    } else if (source.height >= source.width) {
        aspect.portraitRatio
    } else {
        1f / aspect.portraitRatio
    }
    val curRatio = source.width.toFloat() / source.height.toFloat()
    if (abs(curRatio - targetRatio) < 0.02f) return source

    val (cropW, cropH) = if (curRatio > targetRatio) {
        (source.height * targetRatio).roundToInt().coerceAtLeast(1) to source.height
    } else {
        source.width to (source.width / targetRatio).roundToInt().coerceAtLeast(1)
    }
    val left = ((source.width - cropW) / 2).coerceAtLeast(0)
    val top = ((source.height - cropH) / 2).coerceAtLeast(0)
    return Bitmap.createBitmap(source, left, top, cropW, cropH)
}
