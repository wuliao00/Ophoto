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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ophoto.ui.theme.GlassBorder
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.PremiumHeroHeader
import com.ophoto.ui.theme.ShutterOrange
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.rounded.Search
import com.ophoto.data.repository.DemoPhoto
import com.ophoto.data.repository.DemoPhotos
import com.ophoto.ui.theme.BrassGoldSoft
import com.ophoto.ui.theme.MaterialHairline
import com.ophoto.ui.theme.MaterialThin
import com.ophoto.ui.theme.OphotoChip
import com.ophoto.ui.theme.OphotoPageHeader
import com.ophoto.ui.theme.OphotoShape
import com.ophoto.ui.theme.TextOnAccent

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
    // 每张卡片的缩略图由「真实摄影底片 + 该预设」现算，杜绝同一张测试图重复出现
    val previewGraded = remember(preset.id, preset.params, sampleBitmap) {
        runCatching {
            val engine = com.ophoto.engine.filter.FilterEngineImpl()
            engine.loadFilter(preset)
            engine.setStrength(preset.defaultStrength)
            engine.applyToImage(sampleBitmap)
        }.getOrDefault(sampleBitmap)
    }
    val accent = remember(preset.accentHex) {
        runCatching { Color(android.graphics.Color.parseColor(preset.accentHex)) }.getOrDefault(BrassGold)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OphotoShape.Card)
            .background(MaterialThin)
            .border(0.7.dp, MaterialHairline, OphotoShape.Card),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        ) {
            Image(
                bitmap = previewGraded.asImageBitmap(),
                contentDescription = preset.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 底部信息压暗渐变（文字永远可读）
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x99000000), Color(0xE6000000)),
                            startY = 260f,
                        ),
                    ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
            ) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    preset.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 收藏：右上角单动作，不堆一排小按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x73000000))
                    .clickable { scope.launch { container.filterRepository.toggleFavorite(preset) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (preset.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (preset.isFavorite) ShutterOrange else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
            }
            // 分类色点：一个 3dp 圆点取代标签块
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(OphotoShape.Chip)
                        .background(BrassGold)
                        .clickable(onClick = onApplyCamera),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "用于拍摄",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextOnAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ControlPillBg.copy(alpha = 0.7f))
                        .clickable(onClick = onLaunchOverlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PictureInPictureAlt, "悬浮参考", tint = BrassGold, modifier = Modifier.size(16.dp))
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ControlPillBg.copy(alpha = 0.7f))
                        .clickable(onClick = onShareQr),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.QrCode2, "分享配方", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

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

    // 真实摄影底片：三张按分类自动匹配（人像 / 夜景 / 风光），不再用几何测试图
    val scenePhotos = remember {
        DemoPhoto.entries.mapNotNull { p ->
            runCatching { DemoPhotos.load(context, p) }.getOrNull()
        }
    }
    val baseSample = scenePhotos.getOrNull(2) ?: scenePhotos.firstOrNull()

    val fileImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val res = container.filterImporter.importFromUri(uri)) {
                is ImportResult.Success -> {
                    container.filterRepository.savePresets(res.importedBatch, "file_import")
                    Toast.makeText(context, "成功导入 ${res.importedBatch.size} 款：${res.filter.name}", Toast.LENGTH_SHORT).show()
                }
                is ImportResult.Error -> Toast.makeText(context, res.detail, Toast.LENGTH_LONG).show()
            }
        }
    }

    val filteredPresets = remember(allPresets, selectedCategory, favoritesOnly, searchQuery) {
        allPresets.filter { p ->
            val matchFav = !favoritesOnly || p.isFavorite
            val matchCat = selectedCategory == null || p.category == selectedCategory
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() || p.name.lowercase().contains(q) ||
                p.subtitle.lowercase().contains(q) || p.description.lowercase().contains(q)
            matchFav && matchCat && matchSearch
        }
    }

    // 分类 → 场景底片：人像用窗光、夜景用街拍、其余用风光
    fun sampleFor(preset: FilterPreset): android.graphics.Bitmap {
        val idx = when (preset.category) {
            FilterCategory.PORTRAIT -> 0
            FilterCategory.NIGHT -> 1
            FilterCategory.MONOCHROME -> 1
            else -> 2
        }
        return scenePhotos.getOrNull(idx) ?: baseSample ?: BuiltInPresets.createSamplePreviewBitmap(640, 360)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
    ) {
        // 固定头部：标题 + 搜索 + 分类。用留白分组，不再有渐变页头与三枚统计格。
        Column(Modifier.padding(horizontal = 16.dp)) {
            OphotoPageHeader(
                title = "调色库",
                subtitle = "${allPresets.size} 款预设 · 收藏 ${allPresets.count { it.isFavorite }} · 点缩略图即可用于拍摄",
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索 夜景 / 胶片 / 人像 / 海风…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = OphotoShape.Chip,
                textStyle = MaterialTheme.typography.bodySmall,
                leadingIcon = {
                    Icon(Icons.Rounded.Search, null, tint = TextMuted, modifier = Modifier.size(17.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrassGold.copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialHairline,
                    focusedContainerColor = ControlPillBg.copy(alpha = 0.5f),
                    unfocusedContainerColor = ControlPillBg.copy(alpha = 0.5f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    OphotoChip(
                        label = "全部",
                        selected = selectedCategory == null && !favoritesOnly,
                        onClick = { selectedCategory = null; favoritesOnly = false },
                    )
                }
                item {
                    OphotoChip(
                        label = "收藏",
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        leadingIcon = Icons.Rounded.FavoriteBorder,
                    )
                }
                items(FilterCategory.entries) { cat ->
                    OphotoChip(
                        label = cat.label,
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (overlayRunning) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(OphotoShape.Chip)
                        .background(BrassGoldSoft)
                        .clickable { MasterOverlayService.stop(context) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(BrassGold))
                    Spacer(Modifier.width(7.dp))
                    Text("悬浮参考窗运行中 · 点击关闭", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                }
                Spacer(Modifier.height(8.dp))
            }
            // 导入等低频操作收进一个胶囊，避免四个彩色按钮抢占视线
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OphotoChip(
                    label = "导入 .cube",
                    onClick = { fileImportLauncher.launch(arrayOf("*/*")) },
                    leadingIcon = Icons.Rounded.FileUpload,
                )
                OphotoChip(
                    label = "口令",
                    onClick = { showPasteDialog = true },
                    leadingIcon = Icons.Rounded.ContentPaste,
                )
                OphotoChip(
                    label = "自制",
                    onClick = { showCreateDialog = true },
                    leadingIcon = Icons.Rounded.Add,
                )
                OphotoChip(
                    label = "随机",
                    onClick = { filteredPresets.randomOrNull()?.let { onApplyAndOpenCamera(it.id) } },
                    leadingIcon = Icons.Rounded.Shuffle,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (filteredPresets.isEmpty()) {
            EmptyState(
                title = "没有匹配的预设",
                desc = "换个关键词，或清除筛选条件",
                modifier = Modifier.weight(1f),
                onAction = { selectedCategory = null; favoritesOnly = false; searchQuery = "" },
                actionLabel = "清除筛选",
            )
        } else {
            // 两列画廊（Clarity：一屏看到更多色彩，缩略图足够大以判断影调）
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (filteredPresets.size == 1) 1 else 2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(filteredPresets, key = { it.id }) { preset ->
                    MasterPresetCard(
                        preset = preset,
                        sampleBitmap = sampleFor(preset),
                        container = container,
                        onApplyCamera = { onApplyAndOpenCamera(preset.id) },
                        onLaunchOverlay = {
                            if (!CameraPermissionHelper.canDrawFloatingOverlay(context)) {
                                showOverlayGuideDialog = true
                            } else {
                                scope.launch { container.preferences.setOverlayPresetId(preset.id) }
                                MasterOverlayService.startWithPreset(context, preset.id, true)
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
                                val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cubeFile)
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
    }
    if (showPasteDialog) PasteImportDialog(onDismiss = { showPasteDialog = false }, onConfirmImport = { rawText -> scope.launch { when (val res = container.filterImporter.importFromTextPayload(rawText)) { is ImportResult.Success -> { container.filterRepository.savePresets(res.importedBatch, "clipboard"); showPasteDialog = false; Toast.makeText(context, "已导入 ${res.importedBatch.size} 款：${res.filter.name}", Toast.LENGTH_SHORT).show() }; is ImportResult.Error -> Toast.makeText(context, res.detail, Toast.LENGTH_LONG).show() } } })
    if (showCreateDialog) CustomFilterCreatorDialog(onDismiss = { showCreateDialog = false }, onSavePreset = { created -> scope.launch { container.filterRepository.savePreset(created, "custom"); showCreateDialog = false; Toast.makeText(context, "已创建：${created.name}", Toast.LENGTH_SHORT).show() } })
    shareTargetPreset?.let { target -> ShareRecipeDialog(preset = target, onDismiss = { shareTargetPreset = null }) }
    if (showOverlayGuideDialog) ColorOsOverlayPermissionDialog(onDismiss = { showOverlayGuideDialog = false }, onGrantNow = { showOverlayGuideDialog = false; context.startActivity(CameraPermissionHelper.createOverlayPermissionIntent(context)) })
}

@Composable
private fun MasterMetricCell(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp); Text(value, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ActionButtonChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, highlight: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(if (highlight) BrassGold else ControlPillBg).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp)) {
        Icon(icon, null, tint = if (highlight) ObsidianBg else BrassGold, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(label, style = MaterialTheme.typography.labelSmall, color = if (highlight) ObsidianBg else TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PasteImportDialog(onDismiss: () -> Unit, onConfirmImport: (String) -> Unit) {
    var inputText by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("导入口令或 JSON") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("支持 OPHOTO1:... / manifest.json / presets.json", style = MaterialTheme.typography.bodySmall, color = TextSecondary); OutlinedTextField(value = inputText, onValueChange = { inputText = it }, minLines = 5, maxLines = 9, placeholder = { Text("OPHOTO1:...") }, modifier = Modifier.fillMaxWidth()) } }, confirmButton = { Button(onClick = { onConfirmImport(inputText) }) { Text("校验入库") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun CustomFilterCreatorDialog(onDismiss: () -> Unit, onSavePreset: (FilterPreset) -> Unit) {
    var name by remember { mutableStateOf("自定义胶片配方") }
    var temperature by remember { mutableIntStateOf(6) }; var contrast by remember { mutableIntStateOf(8) }; var saturation by remember { mutableIntStateOf(-8) }; var grain by remember { mutableIntStateOf(16) }; var vignette by remember { mutableIntStateOf(18) }; var fade by remember { mutableIntStateOf(10) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("创建原创配方") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LabeledIntSlider("色温冷暖", temperature, -50..50) { temperature = it }; LabeledIntSlider("影调对比", contrast, -50..50) { contrast = it }; LabeledIntSlider("色彩饱和", saturation, -50..50) { saturation = it }; LabeledIntSlider("胶片颗粒", grain, 0..60) { grain = it }; LabeledIntSlider("光学暗角", vignette, 0..60) { vignette = it }; LabeledIntSlider("暗部褪色", fade, 0..40) { fade = it }
        }
    }, confirmButton = {
        Button(onClick = {
            val preset = FilterPreset(id = "user_${UUID.randomUUID().toString().take(8)}", name = name.ifBlank { "我的配方" }, subtitle = "原创参数配方", category = FilterCategory.IMPORTED, author = "本地创作", description = "色温 ${formatSigned(temperature)} · 对比 ${formatSigned(contrast)} · 颗粒 $grain", params = FilterParams(temperature = temperature, contrast = contrast, saturation = saturation, grainStrength = grain, vignette = vignette, fade = fade))
            onSavePreset(preset)
        }) { Text("保存至滤镜库") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ShareRecipeDialog(preset: FilterPreset, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shareCode = remember(preset) { FilterImporterImpl.exportPresetToJson(preset, asShareCode = true) }
    val matrix = remember(shareCode) { QrRecipeCodec.encodeVisualMatrix(shareCode) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("分享 · ${preset.name}") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(168.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Canvas(Modifier.fillMaxSize()) { val cell = size.width / matrix.size; for (y in matrix.indices) for (x in matrix[y].indices) if (matrix[y][x]) drawRect(Color(0xFF0B0D12), topLeft = Offset(x * cell, y * cell), size = Size(cell * 1.02f, cell * 1.02f)) }
            }
            Text(shareCode.take(96) + "…", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }, confirmButton = { Button(onClick = { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager; cb?.setPrimaryClip(ClipData.newPlainText("Ophoto Recipe", shareCode)); Toast.makeText(context, "已复制口令", Toast.LENGTH_SHORT).show(); onDismiss() }) { Text("复制口令") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
fun ColorOsOverlayPermissionDialog(onDismiss: () -> Unit, onGrantNow: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("开启相机悬浮窗") }, text = { Text("1. 开启“显示在其他应用上层”\n2. ColorOS 若提示受限：设置→应用→Ophoto→⋮→解除受限设置\n3. 关闭电竞屏蔽悬浮通知", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }, confirmButton = { Button(onClick = onGrantNow) { Text("前往授权") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } })
}

@Composable
private fun LabeledIntSlider(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text("$label ${formatSigned(value)}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, modifier = Modifier.width(86.dp)); Slider(value = value.toFloat(), onValueChange = { onValueChange(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(), colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold), modifier = Modifier.weight(1f).height(24.dp)) }
}

private fun formatSigned(value: Int): String = if (value > 0) "+$value" else value.toString()
