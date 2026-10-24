package com.ophoto.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ophoto.OphotoContainer
import com.ophoto.core.storage.ExportImageFormat
import com.ophoto.core.storage.MediaStoreSaver
import com.ophoto.data.local.UserSettings
import com.ophoto.data.local.entity.EditHistoryEntity
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.engine.filter.FilterEngineImpl
import com.ophoto.engine.image.CropAspectPreset
import com.ophoto.engine.image.CurvePoint
import com.ophoto.engine.image.FrameStyle
import com.ophoto.engine.image.HslBand
import com.ophoto.engine.image.HslShift
import com.ophoto.engine.image.ImageProcessEngine
import com.ophoto.engine.image.LocalMaskType
import com.ophoto.engine.image.PhotoEditRecipe
import com.ophoto.engine.metadata.CaptureExifMetadata
import com.ophoto.engine.metadata.MetadataEngine
import com.ophoto.ui.camera.TopHudChip
import com.ophoto.ui.theme.BorderHighlight
import com.ophoto.ui.theme.BorderSubtle
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.DeckGradientBottom
import com.ophoto.ui.theme.DeckGradientTop
import com.ophoto.ui.theme.GlassScrim
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OphotoChip
import com.ophoto.ui.theme.OphotoSegmentedBar
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.SurfaceRaised
import com.ophoto.ui.theme.SurfaceRecessed
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextOnAccent
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DarkroomToolTab(val label: String) {
    FILTER("滤镜"),
    CURVES("曲线"),
    HSL("HSL"),
    LOCAL_CROP("构图"),
    FRAME_EXPORT("画框"),
}

private enum class CurveChannel(val label: String, val strokeColor: Color) {
    MASTER("RGB", BrassGold),
    RED("红 R", Color(0xFFF87171)),
    GREEN("绿 G", Color(0xFF34D399)),
    BLUE("蓝 B", Color(0xFF60A5FA)),
}

@Composable
fun GalleryEditorScreen(
    container: OphotoContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by container.filterRepository.allPresetsFlow.collectAsState(initial = BuiltInPresets.ALL)
    val settings by container.preferences.settingsFlow.collectAsState(initial = UserSettings())

    var sourceBitmap by remember { mutableStateOf(BuiltInPresets.createSamplePreviewBitmap(800, 600)) }
    val miniThumbSample = remember { BuiltInPresets.createSamplePreviewBitmap(128, 96) }
    var activeExif by remember { mutableStateOf(CaptureExifMetadata(model = container.hardwareProfile.displayTitle)) }
    var recipe by remember { mutableStateOf(PhotoEditRecipe(preset = BuiltInPresets.ALL.first())) }
    var renderedBitmap by remember { mutableStateOf(sourceBitmap) }
    var showOriginalCompare by remember { mutableStateOf(false) }
    var activeToolTab by remember { mutableStateOf(DarkroomToolTab.FILTER) }
    var activeCurveChannel by remember { mutableStateOf(CurveChannel.MASTER) }
    var selectedHslBand by remember { mutableStateOf(HslBand.ORANGE) }
    var exportFormat by remember { mutableStateOf(ExportImageFormat.JPEG) }
    var stripExifLocation by remember { mutableStateOf(true) }

    val undoStack = remember { mutableStateListOf<PhotoEditRecipe>() }
    val redoStack = remember { mutableStateListOf<PhotoEditRecipe>() }

    fun pushRecipeUpdate(next: PhotoEditRecipe) {
        if (next == recipe) return
        undoStack.add(recipe)
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
        recipe = next
    }

    LaunchedEffect(sourceBitmap, recipe) {
        renderedBitmap = withContext(Dispatchers.Default) {
            ImageProcessEngine.process(sourceBitmap, recipe, activeExif)
        }
    }

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val meta = context.contentResolver.openInputStream(uri)?.use {
                    MetadataEngine.readMetadataFromStream(it)
                } ?: CaptureExifMetadata()
                val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                    decodeSampledBitmap(stream.readBytes(), 1600)
                }
                bmp to meta
            }
            loaded.first?.let { bmp ->
                sourceBitmap = bmp
                activeExif = loaded.second
                undoStack.clear()
                redoStack.clear()
            }
        }
    }

    val batchPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var count = 0
            withContext(Dispatchers.IO) {
                for (u in uris) {
                    val bmp = context.contentResolver.openInputStream(u)?.use {
                        decodeSampledBitmap(it.readBytes(), 1600)
                    } ?: continue
                    val out = ImageProcessEngine.process(bmp, recipe, activeExif)
                    val saved = MediaStoreSaver.saveBitmapToGallery(
                        context = context,
                        bitmap = out,
                        fileBaseName = "OPHOTO_BATCH_${System.currentTimeMillis()}_$count",
                        format = exportFormat,
                        exifMetadata = activeExif,
                        stripLocation = stripExifLocation,
                    )
                    if (saved != null) count++
                }
            }
            Toast.makeText(context, "已批量渲染并导出 $count 张照片至相册", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 1. Sleek Darkroom Top Studio Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(SurfaceRecessed)
                .border(0.8.dp, HairlineBorder, RoundedCornerShape(13.dp))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ControlPillBg)
                        .clickable {
                            singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = BrassGold,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("选图", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ControlPillBg)
                        .clickable {
                            batchPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BurstMode,
                        contentDescription = null,
                        tint = OpticalEmerald,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("批处理", style = MaterialTheme.typography.labelSmall, color = OpticalEmerald)
                }

                IconButton(
                    enabled = undoStack.isNotEmpty(),
                    onClick = {
                        val prev = undoStack.removeAt(undoStack.lastIndex)
                        redoStack.add(recipe)
                        recipe = prev
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "撤销",
                        tint = if (undoStack.isNotEmpty()) TextPrimary else TextMuted,
                        modifier = Modifier.size(17.dp),
                    )
                }

                IconButton(
                    enabled = redoStack.isNotEmpty(),
                    onClick = {
                        val nxt = redoStack.removeAt(redoStack.lastIndex)
                        undoStack.add(recipe)
                        recipe = nxt
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "重做",
                        tint = if (redoStack.isNotEmpty()) TextPrimary else TextMuted,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (showOriginalCompare) BrassGold else ControlPillBg, CircleShape)
                        .clickable { showOriginalCompare = !showOriginalCompare },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Compare,
                        contentDescription = "前后对比",
                        tint = if (showOriginalCompare) TextOnAccent else TextPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            val name = MediaStoreSaver.formatFileName(
                                template = settings.fileNameTemplate,
                                modeTag = "EDIT",
                                filterName = recipe.preset?.name,
                            )
                            val uri = MediaStoreSaver.saveBitmapToGallery(
                                context = context,
                                bitmap = renderedBitmap,
                                fileBaseName = name,
                                format = exportFormat,
                                exifMetadata = activeExif.copy(
                                    filterName = recipe.preset?.name,
                                    filterStrength = recipe.presetStrength,
                                ),
                                stripLocation = stripExifLocation,
                            )
                            container.mediaRepository.recordEditStep(
                                EditHistoryEntity(
                                    mediaId = uri?.toString() ?: name,
                                    filterId = recipe.preset?.id,
                                    paramsJson = "strength=${recipe.presetStrength};frame=${recipe.frameStyle.name}",
                                    cropJson = "crop=${recipe.cropPreset.name};rot=${recipe.rotationQuarterTurns}",
                                    createdAt = System.currentTimeMillis(),
                                    version = undoStack.size + 1,
                                ),
                            )
                            Toast.makeText(
                                context,
                                "已无损导出至系统相册 ($name.${exportFormat.extension})",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = TextOnAccent),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 2. Generous Optical Darkroom Canvas Viewport (50%)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.50f)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF040507))
                .border(0.9.dp, BorderHighlight, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val shownBmp = if (showOriginalCompare) sourceBitmap else renderedBitmap
            Image(
                bitmap = shownBmp.asImageBitmap(),
                contentDescription = "编辑预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
            )
            if (showOriginalCompare) {
                Text(
                    text = "原片对比中",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextOnAccent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BrassGold)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(
                text = "${shownBmp.width}×${shownBmp.height} · ${recipe.preset?.name ?: "无滤镜"}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(GlassScrim)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }

        // 3. 5-Segment Darkroom Tool Bar
        OphotoSegmentedBar(
            items = DarkroomToolTab.entries,
            selectedItem = activeToolTab,
            labelSelector = { it.label },
            onSelect = { activeToolTab = it },
        )

        Spacer(Modifier.height(8.dp))

        // 4. Stationary Anodized Tool Deck with Clean Internal Scroll
        val deckShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.50f)
                .clip(deckShape)
                .background(Brush.verticalGradient(listOf(DeckGradientTop, DeckGradientBottom)), deckShape)
                .border(1.dp, HairlineBorder, deckShape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (activeToolTab) {
                    DarkroomToolTab.FILTER -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "色彩配方 · ${recipe.preset?.name ?: "原片无滤镜"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrassGold,
                            )
                            Text(
                                text = "强度 ${(recipe.presetStrength * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                        Slider(
                            value = recipe.presetStrength,
                            onValueChange = { pushRecipeUpdate(recipe.copy(presetStrength = it)) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = BrassGold,
                                activeTrackColor = BrassGold,
                                inactiveTrackColor = ControlPillBg,
                            ),
                            modifier = Modifier.height(24.dp),
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                DarkroomPresetTile(
                                    name = "原片",
                                    thumb = miniThumbSample,
                                    selected = recipe.preset == null,
                                    onClick = { pushRecipeUpdate(recipe.copy(preset = null)) },
                                )
                            }
                            items(presets, key = { it.id }) { p ->
                                val gradedThumb = remember(p.id, p.params) {
                                    val eng = FilterEngineImpl()
                                    eng.loadFilter(p)
                                    eng.setStrength(p.defaultStrength)
                                    eng.applyToImage(miniThumbSample)
                                }
                                DarkroomPresetTile(
                                    name = p.name,
                                    thumb = gradedThumb,
                                    selected = recipe.preset?.id == p.id,
                                    onClick = { pushRecipeUpdate(recipe.copy(preset = p)) },
                                )
                            }
                        }
                    }

                    DarkroomToolTab.CURVES -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CurveChannel.entries.forEach { ch ->
                                    OphotoChip(
                                        label = ch.label,
                                        selected = activeCurveChannel == ch,
                                        accent = ch.strokeColor,
                                        onClick = { activeCurveChannel = ch },
                                    )
                                }
                            }
                            TopHudChip("重置通道", false) {
                                val linear = PhotoEditRecipe.defaultLinearCurve()
                                val updated = when (activeCurveChannel) {
                                    CurveChannel.MASTER -> recipe.copy(masterCurve = linear)
                                    CurveChannel.RED -> recipe.copy(redCurve = linear)
                                    CurveChannel.GREEN -> recipe.copy(greenCurve = linear)
                                    CurveChannel.BLUE -> recipe.copy(blueCurve = linear)
                                }
                                pushRecipeUpdate(updated)
                            }
                        }

                        val activePoints = when (activeCurveChannel) {
                            CurveChannel.MASTER -> recipe.masterCurve
                            CurveChannel.RED -> recipe.redCurve
                            CurveChannel.GREEN -> recipe.greenCurve
                            CurveChannel.BLUE -> recipe.blueCurve
                        }
                        InteractiveCurvePad(
                            points = activePoints,
                            strokeColor = activeCurveChannel.strokeColor,
                            onUpdatePoints = { newPts ->
                                val updated = when (activeCurveChannel) {
                                    CurveChannel.MASTER -> recipe.copy(masterCurve = newPts)
                                    CurveChannel.RED -> recipe.copy(redCurve = newPts)
                                    CurveChannel.GREEN -> recipe.copy(greenCurve = newPts)
                                    CurveChannel.BLUE -> recipe.copy(blueCurve = newPts)
                                }
                                pushRecipeUpdate(updated)
                            },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TopHudChip("经典 S 反差", false) {
                                pushRecipeUpdate(
                                    recipe.copy(
                                        masterCurve = listOf(
                                            CurvePoint(0f, 0.04f),
                                            CurvePoint(0.25f, 0.19f),
                                            CurvePoint(0.50f, 0.50f),
                                            CurvePoint(0.75f, 0.81f),
                                            CurvePoint(1f, 0.96f),
                                        ),
                                    ),
                                )
                            }
                            TopHudChip("空气哑光黑位", false) {
                                pushRecipeUpdate(
                                    recipe.copy(
                                        masterCurve = listOf(
                                            CurvePoint(0f, 0.09f),
                                            CurvePoint(0.25f, 0.30f),
                                            CurvePoint(0.50f, 0.56f),
                                            CurvePoint(0.75f, 0.81f),
                                            CurvePoint(1f, 0.98f),
                                        ),
                                    ),
                                )
                            }
                        }
                    }

                    DarkroomToolTab.HSL -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(HslBand.entries) { band ->
                                val selected = band == selectedHslBand
                                val bandCol = Color(band.swatchHex)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (selected) bandCol.copy(alpha = 0.24f) else SurfaceRecessed)
                                        .border(
                                            1.dp,
                                            if (selected) bandCol else BorderSubtle,
                                            RoundedCornerShape(9.dp),
                                        )
                                        .clickable { selectedHslBand = band }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(bandCol),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = band.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) TextPrimary else TextSecondary,
                                    )
                                }
                            }
                        }

                        val curShift = recipe.hslAdjustments[selectedHslBand] ?: HslShift()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${selectedHslBand.label}通道 · H ${curShift.hue} / S ${curShift.saturation} / L ${curShift.luminance}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrassGold,
                            )
                            if (curShift.hue != 0 || curShift.saturation != 0 || curShift.luminance != 0) {
                                Text(
                                    text = "归零",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OpticalEmerald,
                                    modifier = Modifier.clickable {
                                        val updated = recipe.hslAdjustments.toMutableMap()
                                        updated.remove(selectedHslBand)
                                        pushRecipeUpdate(recipe.copy(hslAdjustments = updated))
                                    },
                                )
                            }
                        }

                        HslAxisSlider("色相 Hue", curShift.hue, BrassGold) { v ->
                            val updated = recipe.hslAdjustments.toMutableMap()
                            updated[selectedHslBand] = curShift.copy(hue = v)
                            pushRecipeUpdate(recipe.copy(hslAdjustments = updated))
                        }
                        HslAxisSlider("饱和度 Sat", curShift.saturation, OpticalEmerald) { v ->
                            val updated = recipe.hslAdjustments.toMutableMap()
                            updated[selectedHslBand] = curShift.copy(saturation = v)
                            pushRecipeUpdate(recipe.copy(hslAdjustments = updated))
                        }
                        HslAxisSlider("明度 Lum", curShift.luminance, TextPrimary) { v ->
                            val updated = recipe.hslAdjustments.toMutableMap()
                            updated[selectedHslBand] = curShift.copy(luminance = v)
                            pushRecipeUpdate(recipe.copy(hslAdjustments = updated))
                        }
                    }

                    DarkroomToolTab.LOCAL_CROP -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                items(CropAspectPreset.entries) { cp ->
                                    TopHudChip(cp.label, recipe.cropPreset == cp) {
                                        pushRecipeUpdate(recipe.copy(cropPreset = cp))
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    pushRecipeUpdate(recipe.copy(mirrorHorizontal = !recipe.mirrorHorizontal))
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(SurfaceRecessed, CircleShape),
                            ) {
                                Icon(
                                    Icons.Rounded.Flip,
                                    contentDescription = "水平镜像",
                                    tint = if (recipe.mirrorHorizontal) BrassGold else TextSecondary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    pushRecipeUpdate(
                                        recipe.copy(rotationQuarterTurns = (recipe.rotationQuarterTurns + 1) % 4),
                                    )
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(SurfaceRecessed, CircleShape),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.RotateRight,
                                    contentDescription = "旋转90度",
                                    tint = BrassGold,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = String.format(Locale.US, "水平校正 %+.1f°", recipe.straightenDegrees),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            if (abs(recipe.straightenDegrees) > 0.05f) {
                                Text(
                                    text = "归零",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrassGold,
                                    modifier = Modifier.clickable {
                                        pushRecipeUpdate(recipe.copy(straightenDegrees = 0f))
                                    },
                                )
                            }
                        }
                        Slider(
                            value = recipe.straightenDegrees,
                            onValueChange = { pushRecipeUpdate(recipe.copy(straightenDegrees = it)) },
                            valueRange = -15f..15f,
                            colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                            modifier = Modifier.height(22.dp),
                        )

                        Text("局部光效蒙版", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LocalMaskType.entries.forEach { mask ->
                                TopHudChip(mask.label, recipe.localMaskType == mask) {
                                    pushRecipeUpdate(recipe.copy(localMaskType = mask))
                                }
                            }
                        }
                        if (recipe.localMaskType != LocalMaskType.NONE) {
                            Text(
                                text = String.format(Locale.US, "蒙版曝光 %+.1f EV · 冷暖 %+d", recipe.localMaskEv, recipe.localMaskWarmth),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Slider(
                                value = recipe.localMaskEv,
                                onValueChange = { pushRecipeUpdate(recipe.copy(localMaskEv = it)) },
                                valueRange = -1.5f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = OpticalEmerald,
                                    activeTrackColor = OpticalEmerald,
                                ),
                                modifier = Modifier.height(22.dp),
                            )
                        }
                    }

                    DarkroomToolTab.FRAME_EXPORT -> {
                        Text("大师参数签注画框", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(FrameStyle.entries) { fs ->
                                TopHudChip(fs.label, recipe.frameStyle == fs) {
                                    pushRecipeUpdate(recipe.copy(frameStyle = fs))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = recipe.customWatermarkText,
                            onValueChange = { pushRecipeUpdate(recipe.copy(customWatermarkText = it)) },
                            label = { Text("自定义底栏签名（留空默认机型标识）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExportImageFormat.entries.forEach { fmt ->
                                TopHudChip(fmt.name, exportFormat == fmt) { exportFormat = fmt }
                            }
                            TopHudChip(
                                label = if (stripExifLocation) "✓ 抹除GPS隐私" else "保留GPS",
                                active = stripExifLocation,
                            ) {
                                stripExifLocation = !stripExifLocation
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DarkroomPresetTile(
    name: String,
    thumb: Bitmap,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .clip(shape)
            .background(if (selected) BrassGold.copy(alpha = 0.18f) else SurfaceRecessed, shape)
            .border(if (selected) 1.3.dp else 0.7.dp, if (selected) BrassGold else BorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(5.dp),
    ) {
        Image(
            bitmap = thumb.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(7.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) BrassGold else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HslAxisSlider(
    label: String,
    value: Int,
    accent: Color,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.width(68.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = ControlPillBg,
            ),
            modifier = Modifier
                .weight(1f)
                .height(22.dp),
        )
        Text(
            text = if (value > 0) "+$value" else value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.width(32.dp),
        )
    }
}

@Composable
private fun InteractiveCurvePad(
    points: List<CurvePoint>,
    strokeColor: Color,
    onUpdatePoints: (List<CurvePoint>) -> Unit,
) {
    val lut = remember(points) { ImageProcessEngine.buildCurveLut256(points) }

    fun updateAtOffset(offset: Offset, width: Int, height: Int) {
        val nx = (offset.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        val ny = (1f - offset.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        val nearestIdx = points.indices.minByOrNull { i -> abs(points[i].x - nx) } ?: 2
        val next = points.toMutableList()
        next[nearestIdx] = CurvePoint(points[nearestIdx].x, ny)
        onUpdatePoints(next)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRecessed)
            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
            .pointerInput(points) {
                detectTapGestures { offset ->
                    updateAtOffset(offset, size.width, size.height)
                }
            }
            .pointerInput(points) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateAtOffset(change.position, size.width, size.height)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (g in 1..3) {
                drawLine(Color.White.copy(alpha = 0.07f), Offset(w * g / 4f, 0f), Offset(w * g / 4f, h), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, h * g / 4f), Offset(w, h * g / 4f), 1.dp.toPx())
            }
            drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, h), Offset(w, 0f), 1.dp.toPx())

            val path = Path()
            for (i in 0..255) {
                val px = (i / 255f) * w
                val py = (1f - lut[i] / 255f) * h
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, strokeColor, style = Stroke(2.2.dp.toPx()))

            points.forEach { pt ->
                val center = Offset(pt.x * w, (1f - pt.y) * h)
                drawCircle(color = ObsidianBg, radius = 6.dp.toPx(), center = center)
                drawCircle(color = strokeColor, radius = 4.5.dp.toPx(), center = center)
            }
        }
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}
