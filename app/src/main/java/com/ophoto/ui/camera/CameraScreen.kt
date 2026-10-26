package com.ophoto.ui.camera

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ophoto.engine.filter.FilterPreset
import com.ophoto.engine.metadata.CaptureExifMetadata
import com.ophoto.engine.metadata.MetadataEngine
import com.ophoto.engine.render.FocusPeakingColor
import com.ophoto.engine.render.GlLutPreviewView
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

private enum class ViewfinderBgMode(val label: String, val bg: Color, val scrim: Color, val border: Color) {
    DARK(\"暗房黑\", Color(0xFF0F131C), Color(0xFF06070A).copy(0.55f), Color.White.copy(0.08f)),
    STEEL(\"钢蓝灰\", Color(0xFF1E2638), Color(0xFF0B0F1A).copy(0.42f), Color(0xFF6B7A95).copy(0.22f)),
    STUDIO(\"影棚灰\", Color(0xFFE6EAF2), Color(0xFF0F131C).copy(0.58f), Color(0xFFB9C2D6).copy(0.9f)),
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

@Composable
fun CameraScreen(
    container: OphotoContainer,
    onJumpToEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by container.preferences.settingsFlow.collectAsState(initial = com.ophoto.data.local.UserSettings())
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

    var captureMode by remember { mutableStateOf(CameraCaptureMode.PRO) }
    var flashSetting by remember { mutableStateOf(FlashSetting.OFF) }
    var aspectRatio by remember { mutableStateOf(ViewfinderAspectRatio.RATIO_4_3) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var burstCount by remember { mutableIntStateOf(1) }
    var bracketMode by remember { mutableStateOf(false) }
    var peakingColor by remember { mutableStateOf(FocusPeakingColor.OFF) }
    var showFilterTray by remember { mutableStateOf(true) }
    var activeProDial by remember { mutableStateOf(ProDialTab.ISO) }

    var viewfinderBg by remember { mutableStateOf(ViewfinderBgMode.STEEL) }
    var selectedPresetId by remember { mutableStateOf<String?>(BuiltInPresets.ALL.first().id) }
    var filterStrength by remember { mutableFloatStateOf(settings.defaultFilterStrength) }
    var exposureConfig by remember { mutableStateOf(ExposureConfig(manualMode = false, iso = 100, shutterSeconds = 1.0 / 125.0, evCompensation = 0.2f)) }
    var focusConfig by remember { mutableStateOf(FocusConfig()) }
    var wbConfig by remember { mutableStateOf(WhiteBalanceConfig()) }
    var activeCameraId by remember {
        mutableStateOf(container.hardwareProfile.lenses.firstOrNull { !it.isFrontFacing }?.cameraId ?: "0")
    }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var activeFieldBanner by remember { mutableStateOf<String?>(null) }

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
            exposureConfig = exposureConfig.copy(manualMode = false, evCompensation = cmd.evOffset)
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

    // Synthetic studio preview & real-time histogram fallback so HUD works even without hardware camera
    val baseSampleBitmap = remember { BuiltInPresets.createSamplePreviewBitmap(640, 480) }
    var simulatedPreviewBitmap by remember { mutableStateOf(baseSampleBitmap) }
    var histogramData by remember { mutableStateOf(container.filterEngine.computeHistogram(baseSampleBitmap)) }
    var lastCapturedThumb by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(activePreset, filterStrength, exposureConfig.evCompensation, wbConfig.manualKelvin) {
        withContext(Dispatchers.Default) {
            val basePreset = activePreset ?: FilterPreset(id = "neutral", name = "原片")
            val combined = basePreset.copy(
                params = basePreset.params.copy(
                    exposureEv = (basePreset.params.exposureEv + exposureConfig.evCompensation).coerceIn(-2f, 2f),
                    temperature = (basePreset.params.temperature + ((wbConfig.manualKelvin - 5500) / 80)).coerceIn(-100, 100),
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

    val rollDegrees = rememberDeviceRollDegrees(enabled = settings.showLeveler)
    val animatedRoll by animateFloatAsState(targetValue = rollDegrees, animationSpec = tween(220, easing = FastOutSlowInEasing), label = "rollSmooth")
    // Viewfinder luminance boost — makes dark indoor preview feel like XPan bright viewfinder
    var shutterFlash by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(targetValue = if (shutterFlash) 0.92f else 0f, animationSpec = tween(if (shutterFlash) 80 else 420), label = "flash")
    val shutterScale by animateFloatAsState(targetValue = if (shutterFlash) 0.90f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f), label = "shutterScale")

    fun triggerCapture() {
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

            val rawCapture = if (hasCameraPerm) {
                container.cameraEngine.takePhoto(config).bitmap ?: simulatedPreviewBitmap
            } else {
                simulatedPreviewBitmap
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
            val exif = CaptureExifMetadata(
                make = container.hardwareProfile.brand.ifBlank { "OPPO" },
                model = container.hardwareProfile.displayTitle,
                iso = exposureConfig.iso,
                exposureTimeSec = exposureConfig.shutterSeconds,
                aperture = 1.8f,
                focalLengthMm = 5.6f * zoomRatio,
                focalLength35mm = eqFocal,
                kelvin = wbConfig.manualKelvin,
                filterName = activePreset?.name,
                filterStrength = filterStrength,
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
                    iso = exposureConfig.iso,
                    shutter = MetadataEngine.formatShutterSpeed(exposureConfig.shutterSeconds),
                    ev = exposureConfig.evCompensation,
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
                "已保存 ${activePreset?.name ?: "原片"} · ${exif.summaryLine()}",
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
                    val next = exposureConfig.copy(
                        evCompensation = (exposureConfig.evCompensation + delta).coerceIn(-2f, 2f),
                    )
                    exposureConfig = next
                    container.cameraEngine.setExposure(next)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 背景随取景框模式同步切换：STEEL/STUDIO 时全屏提亮
            .background(
                when (viewfinderBg) {
                    ViewfinderBgMode.DARK -> Brush.verticalGradient(listOf(Color(0xFF10151F), Color(0xFF0D1017), ObsidianBg))
                    ViewfinderBgMode.STEEL -> Brush.verticalGradient(listOf(Color(0xFF1A2235), Color(0xFF162033), Color(0xFF111827)))
                    ViewfinderBgMode.STUDIO -> Brush.verticalGradient(listOf(Color(0xFFF2F4F8), Color(0xFFE6EAF2), Color(0xFFD9DFEB)))
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // 1. Top Optical Quick Control Bar — 玻璃胶囊 + 背景切换

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
                    label = "闪光 ${flashSetting.label}",
                    active = flashSetting != FlashSetting.OFF,
                    onClick = {
                        val all = FlashSetting.entries
                        flashSetting = all[(all.indexOf(flashSetting) + 1) % all.size]
                    },
                )
            }
            item {
                TopHudChip(
                    label = aspectRatio.label,
                    active = aspectRatio != ViewfinderAspectRatio.RATIO_4_3,
                    onClick = {
                        val all = ViewfinderAspectRatio.entries
                        aspectRatio = all[(all.indexOf(aspectRatio) + 1) % all.size]
                    },
                )
            }
            item {
                TopHudChip(
                    label = settings.compositionGuide.label,
                    active = settings.compositionGuide != CompositionGuideType.NONE,
                    onClick = {
                        val all = CompositionGuideType.entries
                        val next = all[(all.indexOf(settings.compositionGuide) + 1) % all.size]
                        scope.launch { container.preferences.update { it.copy(compositionGuide = next) } }
                    },
                )
            }
            item {
                TopHudChip(
                    label = if (timerSeconds == 0) "定时 关" else "${timerSeconds}s",
                    active = timerSeconds > 0,
                    onClick = {
                        timerSeconds = when (timerSeconds) {
                            0 -> 3
                            3 -> 10
                            else -> 0
                        }
                    },
                )
            }
            item {
                TopHudChip(
                    label = when {
                        bracketMode -> "包围 ±2EV"
                        burstCount > 1 -> "连拍 ${burstCount}张"
                        else -> "单张快门"
                    },
                    active = bracketMode || burstCount > 1,
                    onClick = {
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
            }
            item {
                TopHudChip(
                    label = if (peakingColor == FocusPeakingColor.OFF) "峰值 关" else peakingColor.label,
                    active = peakingColor != FocusPeakingColor.OFF,
                    onClick = {
                        val all = FocusPeakingColor.entries
                        peakingColor = all[(all.indexOf(peakingColor) + 1) % all.size]
                    },
                )
            }
            item {
                TopHudChip(
                    label = "斑马纹",
                    active = settings.showZebraStripes,
                    onClick = {
                        scope.launch {
                            container.preferences.update { it.copy(showZebraStripes = !it.showZebraStripes) }
                        }
                    },
                )
            }
            item {
                TopHudChip(
                    label = "背景 ${viewfinderBg.label}",
                    active = viewfinderBg != ViewfinderBgMode.DARK,
                    onClick = {
                        viewfinderBg = ViewfinderBgMode.entries[(viewfinderBg.ordinal + 1) % ViewfinderBgMode.entries.size]
                    },
                )
            }
        }

        // Optional Field Assistant Banner when loaded from Shot Card
        activeFieldBanner?.let { tip ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(Color(0xFF1A2420), RoundedCornerShape(10.dp))
                    .border(0.8.dp, OpticalEmerald.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .clickable { activeFieldBanner = null }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "✦ 现场指导已载入：$tip",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpticalEmerald,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }
        }

        // 2. Central Optical Viewfinder — 背景可切换（暗房/钢蓝/影棚），默认钢蓝提亮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(viewfinderBg.bg)
                .border(0.9.dp, viewfinderBg.border, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(aspectRatio.ratioValue.coerceIn(0.45f, 2.4f), matchHeightConstraintsFirst = false)
                    .fillMaxSize()
                    .pointerInput(settings.touchToCapture) {
                        detectTapGestures { offset ->
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
                    },
            ) {
                if (hasCameraPerm) {
                    AndroidView(
                        factory = { ctx ->
                            GlLutPreviewView(ctx).apply {
                                setSurfaceProviderListener { surfaceProvider ->
                                    scope.launch {
                                        container.cameraEngine.bindSession(
                                            lifecycleOwner = lifecycleOwner,
                                            surfaceProvider = surfaceProvider,
                                            cameraId = activeCameraId,
                                        )
                                    }
                                }
                            }
                        },
                        update = { glView ->
                            // 将 EV 补偿与色温实时合并进预览，避免预设自带的负曝光导致全黑
                            val liveParams = activePreset?.let { p ->
                                p.params.copy(
                                    exposureEv = (p.params.exposureEv + exposureConfig.evCompensation).coerceIn(-2f, 2f),
                                    temperature = (p.params.temperature + ((wbConfig.manualKelvin - 5500) / 90)).coerceIn(-100, 100)
                                )
                            } ?: com.ophoto.engine.filter.FilterParams()
                            glView.updateRenderState(
                                params = liveParams,
                                strength = if (activePreset == null) 0f else filterStrength,
                                lut = activeLut,
                                peaking = peakingColor,
                                peakingLevel = 0.6f,
                                zebra = settings.showZebraStripes,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        bitmap = simulatedPreviewBitmap.asImageBitmap(),
                        contentDescription = "实时滤镜预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(260.dp)
                            .clip(CircleShape)
                            .background(ControlPillBg, CircleShape)
                            .border(1.dp, HairlineBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "等待相机授权",
                                tint = BrassGold,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "光学仿真预览",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrassGold,
                            )
                            Text(
                                text = "点击下方快门区域授权真机摄像头",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                // 高光与底幕随背景模式自适应：STUDIO 时高光更亮、底幕加深以保对比
                val highlightAlpha = when (viewfinderBg) {
                    ViewfinderBgMode.DARK -> 0.05f
                    ViewfinderBgMode.STEEL -> 0.07f
                    ViewfinderBgMode.STUDIO -> 0.10f
                }
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = highlightAlpha), Color.Transparent, Color.Transparent),
                                endY = 700f
                            )
                        )
                )
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(88.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, viewfinderBg.scrim)))
                )
                // 快门闪光
                if (flashAlpha > 0.01f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha * 0.92f)))
                // Composition Guides + Horizon Leveler (animated) + Touch Focus Box
                CompositionOverlayCanvas(
                    guideType = settings.compositionGuide,
                    showLeveler = settings.showLeveler,
                    rollDegrees = animatedRoll,
                    focusTapNormalized = focusConfig.normalizedTapX?.let { x ->
                        focusConfig.normalizedTapY?.let { y -> x to y }
                    },
                )

                // Live Histogram Overlay (Top-Left)
                if (settings.showHistogram) {
                    LiveHistogramHud(
                        histogram = histogramData,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                    )
                }

                // Live Telemetry HUD Strip (Bottom-Left)
                if (settings.showParameterHud) {
                    val shutterStr = MetadataEngine.formatShutterSpeed(exposureConfig.shutterSeconds)
                    val evStr = "%+.1f EV".format(exposureConfig.evCompensation)
                    val hudLine = "${(24 * zoomRatio).roundToInt()}mm · $shutterStr · ISO ${exposureConfig.iso} · $evStr · ${wbConfig.manualKelvin}K"
                    Text(
                        text = hudLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .background(Color(0xAD0B0D12), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Multi-Lens / Focal Zoom Switcher Pills (Bottom-Right)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(Color(0xB80B0D12), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(0.6f to "0.6x", 1.0f to "1x", 2.0f to "2x", 3.0f to "3x").forEach { (z, tag) ->
                        val active = kotlin.math.abs(zoomRatio - z) < 0.15f
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) ObsidianBg else BrassGold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (active) BrassGold else Color.Transparent)
                                .clickable {
                                    zoomRatio = z
                                    container.cameraEngine.setZoomRatio(z)
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }
                }

                // Countdown Overlay
                if (countdownRemaining > 0) {
                    Text(
                        text = countdownRemaining.toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = BrassGold,
                        modifier = Modifier
                            .background(Color(0xAA000000), CircleShape)
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }
            }
        }

        // 3. Pro Manual Parameter Sliding Deck (Doc §2 & §11)
        if (captureMode == CameraCaptureMode.PRO || captureMode == CameraCaptureMode.LONG_EXPOSURE) {
            ProParameterPanel(
                activeTab = activeProDial,
                onSelectTab = { activeProDial = it },
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
            )
        }

        // 4. Real-Time Filter Ribbon + Strength Slider (Doc §3 & §11)
        if (showFilterTray) {
            OphotoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                borderDp = 1.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "实时滤镜 · ${activePreset?.name ?: "原片直出"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrassGold,
                    )
                    if (activePreset != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(165.dp),
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
                    contentPadding = PaddingValues(top = 4.dp),
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

        // 5. Camera Mode Selector + Shutter Bar (Doc §11)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            items(CameraCaptureMode.entries) { mode ->
                val selected = mode == captureMode
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) BrassGold else TextMuted,
                    modifier = Modifier
                        .clickable { captureMode = mode }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Gallery / Darkroom Editor Shortcut
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ControlPillBg)
                    .border(1.dp, BrassGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
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

            // Center: Tactile Two-Stage Precision Shutter Button — animated scale + brass glow
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .scale(shutterScale)
                    .clip(CircleShape)
                    .background(
                        when {
                            isRecordingVideo -> GlowOrange.copy(alpha = 0.45f)
                            captureMode == CameraCaptureMode.VIDEO -> ShutterOrange.copy(alpha = 0.25f)
                            else -> GlowBrass.copy(alpha = 0.28f)
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
                        .size(70.dp)
                        .clip(if (isRecordingVideo) RoundedCornerShape(14.dp) else CircleShape)
                        .background(
                            when {
                                isRecordingVideo -> AlertCrimson
                                captureMode == CameraCaptureMode.VIDEO -> ShutterOrange
                                else -> TextPrimary
                            },
                        ),
                )
            }

            // Right: Filter Tray Toggle + Front/Rear Camera Switch
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { showFilterTray = !showFilterTray },
                    modifier = Modifier
                        .size(44.dp)
                        .background(ControlPillBg, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "滤镜面板开关",
                        tint = if (showFilterTray) BrassGold else TextSecondary,
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
                        scope.launch { container.cameraEngine.openCamera(nextId) }
                    },
                    modifier = Modifier
                        .size(44.dp)
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
