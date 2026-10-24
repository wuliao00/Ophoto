package com.ophoto.ui.filter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ophoto.OphotoContainer
import com.ophoto.core.permission.CameraPermissionHelper
import com.ophoto.data.importer.FilterImporterImpl
import com.ophoto.data.importer.ImportResult
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.engine.filter.FilterCategory
import com.ophoto.engine.filter.FilterParams
import com.ophoto.engine.filter.FilterPreset
import com.ophoto.service.MasterOverlayService
import com.ophoto.ui.camera.TopHudChip
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.EmptyState
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OphotoCard
import com.ophoto.ui.theme.OphotoChip
import com.ophoto.ui.theme.OphotoSection
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.ShutterOrange
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun FilterLibraryScreen(
    container: OphotoContainer,
    onApplyAndOpenCamera: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allPresets by container.filterRepository.allPresetsFlow.collectAsState(initial = BuiltInPresets.ALL)
    val overlayRunning by MasterOverlayService.isRunningFlow.collectAsState()

    var selectedCategory by remember { mutableStateOf<FilterCategory?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var shareTargetPreset by remember { mutableStateOf<FilterPreset?>(null) }
    var showOverlayGuideDialog by remember { mutableStateOf(false) }

    val baseSample = remember { BuiltInPresets.createSamplePreviewBitmap(320, 200) }

    // File picker for .cube / .json / .filterpack / .zip
    val fileImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val res = container.filterImporter.importFromUri(uri)) {
                is ImportResult.Success -> {
                    container.filterRepository.savePresets(res.importedBatch, "file_import")
                    Toast.makeText(
                        context,
                        "成功导入 ${res.importedBatch.size} 款滤镜：${res.filter.name}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is ImportResult.Error -> {
                    Toast.makeText(context, res.detail, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val filteredPresets = remember(allPresets, selectedCategory, favoritesOnly, searchQuery) {
        allPresets.filter { p ->
            val matchFav = !favoritesOnly || p.isFavorite
            val matchCat = selectedCategory == null || p.category == selectedCategory
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                p.subtitle.lowercase().contains(q) ||
                p.description.lowercase().contains(q)
            matchFav && matchCat && matchSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Header + Import / Create / Floating HUD Actions
        OphotoSection(
            title = "大师调色与 LUT 仓库",
            subtitle = "支持 .cube / .filterpack / 社区预设导入与相机悬浮窗参考",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (overlayRunning) {
                TopHudChip(
                    label = "关闭悬浮窗",
                    active = true,
                    onClick = { MasterOverlayService.stop(context) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Action Bar: Import File | Paste Code/JSON | Create Custom | Random Pick
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                ActionButtonChip(
                    icon = Icons.Rounded.FileUpload,
                    label = "导入 .cube / 滤镜包",
                    highlight = true,
                    onClick = { fileImportLauncher.launch(arrayOf("*/*")) },
                )
            }
            item {
                ActionButtonChip(
                    icon = Icons.Rounded.ContentPaste,
                    label = "口令 / 社区JSON导入",
                    highlight = false,
                    onClick = { showPasteDialog = true },
                )
            }
            item {
                ActionButtonChip(
                    icon = Icons.Rounded.Add,
                    label = "自制滤镜配方",
                    highlight = false,
                    onClick = { showCreateDialog = true },
                )
            }
            item {
                ActionButtonChip(
                    icon = Icons.Rounded.Shuffle,
                    label = "随机灵感应用",
                    highlight = false,
                    onClick = {
                        filteredPresets.randomOrNull()?.let { pick ->
                            onApplyAndOpenCamera(pick.id)
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索滤镜名称、场景（如：夜景 / 胶片 / 人像 / 海风）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // Category Filter Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                TopHudChip(
                    label = "全部 (${allPresets.size})",
                    active = selectedCategory == null && !favoritesOnly,
                    onClick = {
                        selectedCategory = null
                        favoritesOnly = false
                    },
                )
            }
            item {
                TopHudChip(
                    label = "★ 收藏",
                    active = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                )
            }
            items(FilterCategory.entries) { cat ->
                TopHudChip(
                    label = cat.label,
                    active = selectedCategory == cat,
                    onClick = {
                        selectedCategory = if (selectedCategory == cat) null else cat
                    },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Preset Cards List
        if (filteredPresets.isEmpty()) {
            EmptyState(
                title = "暂无匹配滤镜",
                desc = "尝试调整分类、取消收藏筛选或更换关键词再搜索",
                modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
                onAction = {
                    selectedCategory = null
                    favoritesOnly = false
                    searchQuery = ""
                },
                actionLabel = "重置筛选",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(filteredPresets, key = { it.id }) { preset ->
                MasterPresetCard(
                    preset = preset,
                    sampleBitmap = baseSample,
                    container = container,
                    onApplyCamera = { onApplyAndOpenCamera(preset.id) },
                    onLaunchOverlay = {
                        if (!CameraPermissionHelper.canDrawFloatingOverlay(context)) {
                            showOverlayGuideDialog = true
                        } else {
                            scope.launch { container.preferences.setOverlayPresetId(preset.id) }
                            MasterOverlayService.startWithPreset(
                                context = context,
                                presetId = preset.id,
                                launchSystemCamera = true,
                            )
                        }
                    },
                    onShareQr = { shareTargetPreset = preset },
                    onExportCube = {
                        scope.launch {
                            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                            val cubeFile = File(exportDir, "${preset.id}.cube")
                            withContext(Dispatchers.Default) {
                                cubeFile.writeText(BuiltInPresets.exportPresetCubeText(preset, 17), Charsets.UTF_8)
                            }
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                cubeFile,
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "导出 ${preset.name}.cube"))
                        }
                    },
                )
            }
        }
        }

    if (showPasteDialog) {
        PasteImportDialog(
            onDismiss = { showPasteDialog = false },
            onConfirmImport = { rawText ->
                scope.launch {
                    when (val res = container.filterImporter.importFromTextPayload(rawText)) {
                        is ImportResult.Success -> {
                            container.filterRepository.savePresets(res.importedBatch, "clipboard")
                            showPasteDialog = false
                            Toast.makeText(
                                context,
                                "已导入 ${res.importedBatch.size} 款预设：${res.filter.name}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        is ImportResult.Error -> {
                            Toast.makeText(context, res.detail, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )
    }

    if (showCreateDialog) {
        CustomFilterCreatorDialog(
            onDismiss = { showCreateDialog = false },
            onSavePreset = { created ->
                scope.launch {
                    container.filterRepository.savePreset(created, "custom")
                    showCreateDialog = false
                    Toast.makeText(context, "已创建滤镜：${created.name}", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    shareTargetPreset?.let { target ->
        ShareRecipeDialog(
            preset = target,
            onDismiss = { shareTargetPreset = null },
        )
    }

    if (showOverlayGuideDialog) {
        ColorOsOverlayPermissionDialog(
            onDismiss = { showOverlayGuideDialog = false },
            onGrantNow = {
                showOverlayGuideDialog = false
                context.startActivity(CameraPermissionHelper.createOverlayPermissionIntent(context))
            },
        )
    }
}

@Composable
fun MasterPresetCard(
    preset: FilterPreset,
    sampleBitmap: android.graphics.Bitmap,
    container: OphotoContainer,
    onApplyCamera: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onShareQr: () -> Unit,
    onExportCube: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val previewGraded = remember(preset.id, preset.params) {
        val engine = com.ophoto.engine.filter.FilterEngineImpl()
        engine.loadFilter(preset)
        engine.setStrength(preset.defaultStrength)
        engine.applyToImage(sampleBitmap)
    }
    val m = preset.masterReference

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InstrumentDeck, RoundedCornerShape(16.dp))
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = previewGraded.asImageBitmap(),
                contentDescription = preset.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(108.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = BrassGold,
                    )
                    Row {
                        IconButton(
                            onClick = { scope.launch { container.filterRepository.toggleFavorite(preset) } },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (preset.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (preset.isFavorite) ShutterOrange else TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = onShareQr, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = "口令与二维码分享",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (!preset.isBuiltIn) {
                            IconButton(
                                onClick = { scope.launch { container.filterRepository.deletePreset(preset) } },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除滤镜",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpticalEmerald,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                )
            }
        }
        }

        Spacer(Modifier.height(10.dp))

        // Master Mode Parameter Readout Grid (OMaster-inspired clean visual spec)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ControlPillBg, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MasterMetricCell("底色", m.baseFilterLabel)
            MasterMetricCell("影调", formatSigned(m.tone))
            MasterMetricCell("饱和", formatSigned(m.saturation))
            MasterMetricCell("冷暖", formatSigned(m.warmth))
            MasterMetricCell("青品", formatSigned(m.cyanMagenta))
            MasterMetricCell("锐度", formatSigned(m.sharpness))
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "曝光建议：${m.recommendedIso} · ${m.recommendedShutter} · ${m.recommendedEv} · ${m.recommendedKelvin}K",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onApplyCamera,
                colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = ObsidianBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("应用至相机", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onLaunchOverlay,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureInPictureAlt,
                    contentDescription = null,
                    tint = BrassGold,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("悬浮窗参考", style = MaterialTheme.typography.labelMedium, color = BrassGold)
            }
            OutlinedButton(
                onClick = onExportCube,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(".cube", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun MasterMetricCell(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}

@Composable
private fun ActionButtonChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (highlight) BrassGold else ControlPillBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (highlight) ObsidianBg else BrassGold,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) ObsidianBg else TextPrimary,
        )
    }
}

@Composable
private fun PasteImportDialog(
    onDismiss: () -> Unit,
    onConfirmImport: (String) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 OPHOTO1 口令或 JSON 预设库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "支持粘贴好友分享的 OPHOTO1:... 滤镜口令、单滤镜 manifest.json 或社区 presets.json 合集：",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    minLines = 5,
                    maxLines = 9,
                    placeholder = { Text("OPHOTO1:... 或 { \"name\": \"城市夜景\", ... }") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmImport(inputText) }) {
                Text("校验并入库")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CustomFilterCreatorDialog(
    onDismiss: () -> Unit,
    onSavePreset: (FilterPreset) -> Unit,
) {
    var name by remember { mutableStateOf("自定义胶片配方") }
    var temperature by remember { mutableIntStateOf(6) }
    var contrast by remember { mutableIntStateOf(8) }
    var saturation by remember { mutableIntStateOf(-8) }
    var grain by remember { mutableIntStateOf(16) }
    var vignette by remember { mutableIntStateOf(18) }
    var fade by remember { mutableIntStateOf(10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建原创调色配方") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("预设名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledIntSlider("色温冷暖", temperature, -50..50) { temperature = it }
                LabeledIntSlider("影调对比", contrast, -50..50) { contrast = it }
                LabeledIntSlider("色彩饱和", saturation, -50..50) { saturation = it }
                LabeledIntSlider("胶片颗粒", grain, 0..60) { grain = it }
                LabeledIntSlider("光学暗角", vignette, 0..60) { vignette = it }
                LabeledIntSlider("暗部褪色", fade, 0..40) { fade = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val preset = FilterPreset(
                        id = "user_${UUID.randomUUID().toString().take(8)}",
                        name = name.ifBlank { "我的配方" },
                        subtitle = "原创参数配方",
                        category = FilterCategory.IMPORTED,
                        author = "本地创作",
                        description = "色温 ${formatSigned(temperature)} · 对比 ${formatSigned(contrast)} · 颗粒 $grain",
                        params = FilterParams(
                            temperature = temperature,
                            contrast = contrast,
                            saturation = saturation,
                            grainStrength = grain,
                            vignette = vignette,
                            fade = fade,
                        ),
                    )
                    onSavePreset(preset)
                },
            ) {
                Text("保存至滤镜库")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ShareRecipeDialog(
    preset: FilterPreset,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val shareCode = remember(preset) { FilterImporterImpl.exportPresetToJson(preset, asShareCode = true) }
    val matrix = remember(shareCode) { QrRecipeCodec.encodeVisualMatrix(shareCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享滤镜配方 · ${preset.name}") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cell = size.width / matrix.size
                        for (y in matrix.indices) {
                            for (x in matrix[y].indices) {
                                if (matrix[y][x]) {
                                    drawRect(
                                        color = Color(0xFF0B0D12),
                                        topLeft = Offset(x * cell, y * cell),
                                        size = Size(cell * 1.02f, cell * 1.02f),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = shareCode.take(96) + "...",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cb?.setPrimaryClip(ClipData.newPlainText("Ophoto Recipe", shareCode))
                    Toast.makeText(context, "已复制 OPHOTO1 配方口令到剪贴板", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            ) {
                Text("复制分享口令")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
fun ColorOsOverlayPermissionDialog(
    onDismiss: () -> Unit,
    onGrantNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启相机悬浮窗（ColorOS 适配提示）") },
        text = {
            Text(
                text = "1. 点击下方「前往授权」开启「显示在其他应用上层」权限。\n" +
                    "2. 若 ColorOS / OxygenOS 提示受限设置灰色不可点：进入「系统设置 → 应用 → 应用管理 → Ophoto → 右上角 ⋮ → 解除受限设置」，返回后再开启悬浮窗权限即可在原生大师模式取景框上方实时查看参数。\n" +
                    "3. 游戏助手/电竞模式请关闭「屏蔽悬浮通知」以免遮挡。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        },
        confirmButton = {
            Button(onClick = onGrantNow) { Text("前往授权") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}

@Composable
private fun LabeledIntSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ${formatSigned(value)}",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            modifier = Modifier.width(86.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
            modifier = Modifier.weight(1f).height(24.dp),
        )
    }
}

private fun formatSigned(value: Int): String = if (value > 0) "+$value" else value.toString()
