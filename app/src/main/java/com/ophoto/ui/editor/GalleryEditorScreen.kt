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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Undo
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ophoto.OphotoContainer
import com.ophoto.core.storage.ExportImageFormat
import com.ophoto.core.storage.MediaStoreSaver
import com.ophoto.data.local.entity.EditHistoryEntity
import com.ophoto.data.repository.BuiltInPresets
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
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.EmptyState
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OphotoCard
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class DarkroomToolTab(val label: String) {
    FILTER("滤镜预设"),
    CURVES("RGB曲线"),
    HSL("8色HSL"),
    LOCAL_CROP("蒙版与裁剪"),
    FRAME_EXPORT("画框与导出"),
}

@Composable
fun GalleryEditorScreen(
    container: OphotoContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by container.filterRepository.allPresetsFlow.collectAsState(initial = BuiltInPresets.ALL)
    val settings by container.preferences.settingsFlow.collectAsState(initial = com.ophoto.data.local.UserSettings())

    var sourceBitmap by remember { mutableStateOf(BuiltInPresets.createSamplePreviewBitmap(800, 600)) }
    var activeExif by remember { mutableStateOf(CaptureExifMetadata(model = container.hardwareProfile.displayTitle)) }
    var recipe by remember { mutableStateOf(PhotoEditRecipe(preset = BuiltInPresets.ALL.first())) }
    var renderedBitmap by remember { mutableStateOf(sourceBitmap) }
    var showOriginalCompare by remember { mutableStateOf(false) }
    var activeToolTab by remember { mutableStateOf(DarkroomToolTab.FILTER) }
    var selectedHslBand by remember { mutableStateOf(HslBand.ORANGE) }
    var exportFormat by remember { mutableStateOf(ExportImageFormat.JPEG) }
    var stripExifLocation by remember { mutableStateOf(true) }

    // Undo / Redo non-destructive stack (Doc §3.5 & §12)
    val undoStack = remember { mutableStateListOf<PhotoEditRecipe>() }
    val redoStack = remember { mutableStateListOf<PhotoEditRecipe>() }

    fun pushRecipeUpdate(next: PhotoEditRecipe) {
        undoStack.add(recipe)
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
        recipe = next
    }

    // Re-render preview off the main thread whenever recipe or source changes
    LaunchedEffect(sourceBitmap, recipe) {
        renderedBitmap = withContext(Dispatchers.Default) {
            ImageProcessEngine.process(sourceBitmap, recipe, activeExif)
        }
    }

    // Single photo picker
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

    // Batch apply current recipe to multiple selected photos!
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
        // Top Toolbar: Pick Photo | Undo / Redo | Before/After | Export
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = BrassGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("选图", color = BrassGold, style = MaterialTheme.typography.labelMedium)
                }

                IconButton(
                    enabled = undoStack.isNotEmpty(),
                    onClick = {
                        val prev = undoStack.removeAt(undoStack.lastIndex)
                        redoStack.add(recipe)
                        recipe = prev
                    },
                ) {
                    Icon(Icons.Rounded.Undo, contentDescription = "撤销", tint = if (undoStack.isNotEmpty()) TextPrimary else TextSecondary)
                }
                IconButton(
                    enabled = redoStack.isNotEmpty(),
                    onClick = {
                        val nxt = redoStack.removeAt(redoStack.lastIndex)
                        undoStack.add(recipe)
                        recipe = nxt
                    },
                ) {
                    Icon(Icons.Rounded.Redo, contentDescription = "重做", tint = if (redoStack.isNotEmpty()) TextPrimary else TextSecondary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showOriginalCompare = !showOriginalCompare },
                    modifier = Modifier.background(if (showOriginalCompare) BrassGold else ControlPillBg, CircleShape).size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.Compare,
                        contentDescription = "前后对比",
                        tint = if (showOriginalCompare) ObsidianBg else TextPrimary,
                        modifier = Modifier.size(18.dp),
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
                                "已无损导出至系统相册 Pictures/Ophoto ($name.${exportFormat.extension})",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Central Image Canvas Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF06070A))
                .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val shownBmp = if (showOriginalCompare) sourceBitmap else renderedBitmap
            Image(
                bitmap = shownBmp.asImageBitmap(),
                contentDescription = "编辑预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (showOriginalCompare) {
                Text(
                    text = "原片对比中",
                    style = MaterialTheme.typography.labelSmall,
                    color = ObsidianBg,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(BrassGold, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        // Tool Category Switcher
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(DarkroomToolTab.entries) { tab ->
                TopHudChip(
                    label = tab.label,
                    active = activeToolTab == tab,
                    onClick = { activeToolTab = tab },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tool Controls Deck
        OphotoCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.54f)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            borderDp = 1.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (activeToolTab) {
                DarkroomToolTab.FILTER -> {
                    Text(
                        text = "当前滤镜：${recipe.preset?.name ?: "无滤镜"} (${(recipe.presetStrength * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrassGold,
                    )
                    Slider(
                        value = recipe.presetStrength,
                        onValueChange = { pushRecipeUpdate(recipe.copy(presetStrength = it)) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            TopHudChip("无滤镜", recipe.preset == null) {
                                pushRecipeUpdate(recipe.copy(preset = null))
                            }
                        }
                        items(presets, key = { it.id }) { p ->
                            TopHudChip(p.name, recipe.preset?.id == p.id) {
                                pushRecipeUpdate(recipe.copy(preset = p))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            batchPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("⚡ 将当前全套编辑配方批量应用到多张照片", color = OpticalEmerald)
                    }
                }

                DarkroomToolTab.CURVES -> {
                    Text(
                        text = "交互式三次样条曲线（点击画布任意位置抬升/压暗对应影调节点）",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    InteractiveCurvePad(
                        points = recipe.masterCurve,
                        onUpdatePoints = { newPts ->
                            pushRecipeUpdate(recipe.copy(masterCurve = newPts))
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TopHudChip("胶片 S 曲线", false) {
                            pushRecipeUpdate(
                                recipe.copy(
                                    masterCurve = listOf(
                                        CurvePoint(0f, 0.05f),
                                        CurvePoint(0.25f, 0.19f),
                                        CurvePoint(0.50f, 0.50f),
                                        CurvePoint(0.75f, 0.81f),
                                        CurvePoint(1f, 0.96f),
                                    ),
                                ),
                            )
                        }
                        TopHudChip("日系高调空气", false) {
                            pushRecipeUpdate(
                                recipe.copy(
                                    masterCurve = listOf(
                                        CurvePoint(0f, 0.09f),
                                        CurvePoint(0.25f, 0.31f),
                                        CurvePoint(0.50f, 0.58f),
                                        CurvePoint(0.75f, 0.82f),
                                        CurvePoint(1f, 0.99f),
                                    ),
                                ),
                            )
                        }
                        TopHudChip("重置线性", false) {
                            pushRecipeUpdate(recipe.copy(masterCurve = PhotoEditRecipe.defaultLinearCurve()))
                        }
                    }
                }

                DarkroomToolTab.HSL -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(HslBand.entries) { band ->
                            val selected = band == selectedHslBand
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(band.swatchHex).copy(alpha = 0.28f) else ControlPillBg)
                                    .border(1.dp, if (selected) Color(band.swatchHex) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { selectedHslBand = band }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(band.swatchHex)))
                                Spacer(Modifier.width(5.dp))
                                Text(band.label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            }
                        }
                    }
                    val curShift = recipe.hslAdjustments[selectedHslBand] ?: HslShift()
                    Text("${selectedHslBand.label}通道 · 色相 ${curShift.hue} / 饱和 ${curShift.saturation} / 明度 ${curShift.luminance}", style = MaterialTheme.typography.labelSmall, color = BrassGold)
                    Slider(
                        value = curShift.hue.toFloat(),
                        onValueChange = {
                            val updated = recipe.hslAdjustments.toMutableMap()
                            updated[selectedHslBand] = curShift.copy(hue = it.roundToInt())
                            pushRecipeUpdate(recipe.copy(hslAdjustments = updated))
                        },
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                    )
                    Slider(
                        value = curShift.saturation.toFloat(),
                        onValueChange = {
                            val updated = recipe.hslAdjustments.toMutableMap()
                            updated[selectedHslBand] = curShift.copy(saturation = it.roundToInt())
                            pushRecipeUpdate(recipe.copy(hslAdjustments = updated))
                        },
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(thumbColor = OpticalEmerald, activeTrackColor = OpticalEmerald),
                    )
                    Slider(
                        value = curShift.luminance.toFloat(),
                        onValueChange = {
                            val updated = recipe.hslAdjustments.toMutableMap()
                            updated[selectedHslBand] = curShift.copy(luminance = it.roundToInt())
                            pushRecipeUpdate(recipe.copy(hslAdjustments = updated))
                        },
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(thumbColor = TextPrimary, activeTrackColor = TextPrimary),
                    )
                }

                DarkroomToolTab.LOCAL_CROP -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            items(CropAspectPreset.entries) { cp ->
                                TopHudChip(cp.label, recipe.cropPreset == cp) {
                                    pushRecipeUpdate(recipe.copy(cropPreset = cp))
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                pushRecipeUpdate(recipe.copy(rotationQuarterTurns = (recipe.rotationQuarterTurns + 1) % 4))
                            },
                        ) {
                            Icon(Icons.Rounded.RotateRight, contentDescription = "旋转90度", tint = BrassGold)
                        }
                    }
                    Text("水平校正 ${"%.1f".format(recipe.straightenDegrees)}°", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = recipe.straightenDegrees,
                        onValueChange = { pushRecipeUpdate(recipe.copy(straightenDegrees = it)) },
                        valueRange = -15f..15f,
                        colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                    )
                    Text("局部光效蒙版：${recipe.localMaskType.label}", style = MaterialTheme.typography.labelSmall, color = BrassGold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LocalMaskType.entries.forEach { mask ->
                            TopHudChip(mask.label, recipe.localMaskType == mask) {
                                pushRecipeUpdate(recipe.copy(localMaskType = mask))
                            }
                        }
                    }
                    if (recipe.localMaskType != LocalMaskType.NONE) {
                        Text("局部曝光补偿 ${"%+.1f".format(recipe.localMaskEv)} EV", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Slider(
                            value = recipe.localMaskEv,
                            onValueChange = { pushRecipeUpdate(recipe.copy(localMaskEv = it)) },
                            valueRange = -1.5f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = OpticalEmerald, activeTrackColor = OpticalEmerald),
                        )
                    }
                }

                DarkroomToolTab.FRAME_EXPORT -> {
                    Text("大师参数艺术边框", style = MaterialTheme.typography.titleSmall, color = BrassGold)
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
                        label = { Text("自定义相框/角落签名（留空默认机型标识）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportImageFormat.entries.forEach { fmt ->
                            TopHudChip(fmt.name, exportFormat == fmt) { exportFormat = fmt }
                        }
                        TopHudChip(if (stripExifLocation) "✓ 抹除GPS位置" else "保留GPS", stripExifLocation) {
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
private fun InteractiveCurvePad(
    points: List<CurvePoint>,
    onUpdatePoints: (List<CurvePoint>) -> Unit,
) {
    val lut = remember(points) { ImageProcessEngine.buildCurveLut256(points) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0D14))
            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val ny = (1f - offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val nearestIdx = points.indices.minByOrNull { i -> kotlin.math.abs(points[i].x - nx) } ?: 2
                    val next = points.toMutableList()
                    val fixedX = points[nearestIdx].x
                    next[nearestIdx] = CurvePoint(fixedX, ny)
                    onUpdatePoints(next)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (g in 1..3) {
                drawLine(Color.White.copy(alpha = 0.08f), Offset(w * g / 4f, 0f), Offset(w * g / 4f, h), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, h * g / 4f), Offset(w, h * g / 4f), 1.dp.toPx())
            }
            drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, h), Offset(w, 0f), 1.dp.toPx())

            val path = Path()
            for (i in 0..255) {
                val px = (i / 255f) * w
                val py = (1f - lut[i] / 255f) * h
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, BrassGold, style = Stroke(2.2.dp.toPx()))

            points.forEach { pt ->
                drawCircle(
                    color = OpticalEmerald,
                    radius = 5.dp.toPx(),
                    center = Offset(pt.x * w, (1f - pt.y) * h),
                )
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
