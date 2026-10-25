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
    val accent = remember(preset.accentHex) { runCatching { Color(android.graphics.Color.parseColor(preset.accentHex)) }.getOrDefault(BrassGold) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF171C2A), Color(0xFF11131A))), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(0.07f), RoundedCornerShape(18.dp)),
    ) {
        // Cinematic hero image with overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
        ) {
            Image(
                bitmap = previewGraded.asImageBitmap(),
                contentDescription = preset.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA0B0D12)), startY = 300f)))
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC0B0D12)).border(0.8.dp, accent.copy(0.4f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(preset.category.label, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                if (preset.isFavorite) Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ShutterOrange.copy(0.22f)).padding(horizontal = 6.dp, vertical = 4.dp)) { Text("★ 收藏", style = MaterialTheme.typography.labelSmall, color = ShutterOrange, fontSize = 10.sp) }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(preset.subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.85f))
            }
            Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { scope.launch { container.filterRepository.toggleFavorite(preset) } }, modifier = Modifier.size(30.dp).background(Color(0xCC0B0D12), RoundedCornerShape(8.dp))) {
                    Icon(if (preset.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (preset.isFavorite) ShutterOrange else Color.White, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onShareQr, modifier = Modifier.size(30.dp).background(Color(0xCC0B0D12), RoundedCornerShape(8.dp))) {
                    Icon(Icons.Rounded.QrCode2, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                if (!preset.isBuiltIn) IconButton(onClick = { scope.launch { container.filterRepository.deletePreset(preset) } }, modifier = Modifier.size(30.dp).background(Color(0xCC0B0D12), RoundedCornerShape(8.dp))) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(preset.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF12151E)).border(0.8.dp, HairlineBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MasterMetricCell("底色", m.baseFilterLabel)
                MasterMetricCell("影调", formatSigned(m.tone))
                MasterMetricCell("饱和", formatSigned(m.saturation))
                MasterMetricCell("冷暖", formatSigned(m.warmth))
                MasterMetricCell("锐度", formatSigned(m.sharpness))
            }
            Text("建议 ${m.recommendedIso} · ${m.recommendedShutter} · ${m.recommendedEv} · ${m.recommendedKelvin}K", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApplyCamera, colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = Color(0xFF0B0D12)), shape = RoundedCornerShape(11.dp), modifier = Modifier.weight(1f).height(40.dp)) {
                    Text("应用至相机", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onLaunchOverlay, shape = RoundedCornerShape(11.dp), modifier = Modifier.weight(1f).height(40.dp)) {
                    Icon(Icons.Rounded.PictureInPictureAlt, null, tint = BrassGold, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("悬浮窗", color = BrassGold, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onClick = onExportCube, shape = RoundedCornerShape(11.dp), modifier = Modifier.height(40.dp)) { Text(".cube", color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
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

    val baseSample = remember { BuiltInPresets.createSamplePreviewBitmap(640, 360) }

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
            val matchSearch = q.isEmpty() || p.name.lowercase().contains(q) || p.subtitle.lowercase().contains(q) || p.description.lowercase().contains(q)
            matchFav && matchCat && matchSearch
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0E14), ObsidianBg))).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        PremiumHeroHeader(
            eyebrow = "FILMVAULT  /  12 PRESETS",
            title = "大师调色库",
            subtitle = ".cube / .filterpack / OPHOTO1 口令 / 社区预设 · 一键悬浮窗参考",
            icon = Icons.Rounded.Palette,
            stats = listOf("总数" to "${allPresets.size}", "收藏" to "${allPresets.count { it.isFavorite }}", "导入" to "${allPresets.count { !it.isBuiltIn }}")
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item { ActionButtonChip(Icons.Rounded.FileUpload, "导入 .cube/滤镜包", true) { fileImportLauncher.launch(arrayOf("*/*")) } }
            item { ActionButtonChip(Icons.Rounded.ContentPaste, "口令/JSON", false) { showPasteDialog = true } }
            item { ActionButtonChip(Icons.Rounded.Add, "自制配方", false) { showCreateDialog = true } }
            item { ActionButtonChip(Icons.Rounded.Shuffle, "随机灵感", false) { filteredPresets.randomOrNull()?.let { onApplyAndOpenCamera(it.id) } } }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("搜索：夜景 / 胶片 / 人像 / 海风 / 银盐…", style = MaterialTheme.typography.bodySmall) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(11.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrassGold, unfocusedBorderColor = HairlineBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { TopHudChip("全部 ${allPresets.size}", selectedCategory == null && !favoritesOnly) { selectedCategory = null; favoritesOnly = false } }
            item { TopHudChip("★ 收藏", favoritesOnly) { favoritesOnly = !favoritesOnly } }
            items(FilterCategory.entries) { cat -> TopHudChip(cat.label, selectedCategory == cat) { selectedCategory = if (selectedCategory == cat) null else cat } }
        }
        if (overlayRunning) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BrassGold.copy(0.14f)).border(0.8.dp, BrassGold.copy(0.3f), RoundedCornerShape(10.dp)).clickable { MasterOverlayService.stop(context) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text("● 悬浮窗运行中 · 点击关闭", style = MaterialTheme.typography.labelSmall, color = BrassGold, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (filteredPresets.isEmpty()) {
            EmptyState(title = "暂无匹配滤镜", desc = "调整分类或关键词", modifier = Modifier.weight(1f).padding(horizontal = 24.dp), onAction = { selectedCategory = null; favoritesOnly = false; searchQuery = "" }, actionLabel = "重置筛选")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.weight(1f)) {
                items(filteredPresets, key = { it.id }) { preset ->
                    MasterPresetCard(
                        preset = preset, sampleBitmap = baseSample, container = container,
                        onApplyCamera = { onApplyAndOpenCamera(preset.id) },
                        onLaunchOverlay = {
                            if (!CameraPermissionHelper.canDrawFloatingOverlay(context)) showOverlayGuideDialog = true
                            else { scope.launch { container.preferences.setOverlayPresetId(preset.id) }; MasterOverlayService.startWithPreset(context, preset.id, true) }
                        },
                        onShareQr = { shareTargetPreset = preset },
                        onExportCube = {
                            scope.launch {
                                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val cubeFile = File(exportDir, "${preset.id}.cube")
                                withContext(Dispatchers.Default) { cubeFile.writeText(BuiltInPresets.exportPresetCubeText(preset, 17), Charsets.UTF_8) }
                                val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cubeFile)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, shareUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
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
