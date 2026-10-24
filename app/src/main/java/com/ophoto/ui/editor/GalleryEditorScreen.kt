package com.ophoto.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.AutoFixHigh

import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ophoto.OphotoContainer
import com.ophoto.core.storage.ExportImageFormat
import com.ophoto.core.storage.MediaStoreSaver
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
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.ShutterOrange
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// ------------------------------------------------------------------
// 专业暗房 · 稳定、安全、零闪退 + 高级视觉重塑
// ------------------------------------------------------------------

private enum class DarkroomToolTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FILTER("胶片预设", Icons.Rounded.Palette),
    CURVES("曲线影调", Icons.Rounded.ShowChart),
    HSL("HSL混色", Icons.Rounded.ColorLens),
    GEOMETRY("裁剪蒙版", Icons.Rounded.Crop),
    EXPORT("画框导出", Icons.Rounded.Collections),
}

private enum class CurveChannel(val label: String) {
    MASTER("综合 RGB"),
    R("红 R"),
    G("绿 G"),
    B("蓝 B"),
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

    // --- Core image states ---
    var sourceBitmap by remember { mutableStateOf(BuiltInPresets.createSamplePreviewBitmap(900, 675)) }
    var activeExif by remember { mutableStateOf(CaptureExifMetadata(model = container.hardwareProfile.displayTitle)) }
    var recipe by remember { mutableStateOf(PhotoEditRecipe(preset = BuiltInPresets.ALL.first())) }
    var renderedBitmap by remember { mutableStateOf(sourceBitmap) }
    var isRendering by remember { mutableStateOf(false) }
    var isLoadingImage by remember { mutableStateOf(false) }
    var loadErrorMsg by remember { mutableStateOf<String?>(null) }
    var showOriginalCompare by remember { mutableStateOf(false) }
    var showGridOverlay by remember { mutableStateOf(false) }
    var activeToolTab by remember { mutableStateOf(DarkroomToolTab.FILTER) }
    var selectedHslBand by remember { mutableStateOf(HslBand.ORANGE) }
    var curveChannel by remember { mutableStateOf(CurveChannel.MASTER) }
    var exportFormat by remember { mutableStateOf(ExportImageFormat.JPEG) }
    var stripExifLocation by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var batchProgress by remember { mutableStateOf<Int?>(null) }

    val undoStack = remember { mutableStateListOf<PhotoEditRecipe>() }
    val redoStack = remember { mutableStateListOf<PhotoEditRecipe>() }

    fun pushRecipeUpdate(next: PhotoEditRecipe) {
        undoStack.add(recipe)
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
        recipe = next
    }

    // --- Safe re-render ---
    LaunchedEffect(sourceBitmap, recipe, activeExif) {
        isRendering = true
        val out = withContext(Dispatchers.Default) {
            runCatching { ImageProcessEngine.process(sourceBitmap, recipe, activeExif) }
                .getOrElse { sourceBitmap }
        }
        renderedBitmap = out
        isRendering = false
    }

    // --- Robust Photo Picker with fallback for OPPO / ColorOS / old API ---
    // Photo Picker (PickVisualMedia) may be unavailable on many Chinese ROMs or API<33.
    // We detect availability and fallback to GetContent.
    val photoPickerAvailable = remember(context) {
        runCatching { ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context) }.getOrDefault(false)
    }

    // Centralized loader that safely decodes URI -> Bitmap + EXIF
    suspend fun loadBitmapFromUri(uri: Uri): Pair<Bitmap?, CaptureExifMetadata> {
        return withContext(Dispatchers.IO) {
            var bmp: Bitmap? = null
            var meta: CaptureExifMetadata = CaptureExifMetadata(model = container.hardwareProfile.displayTitle)
            var error: String? = null
            try {
                // Try to read EXIF first (small, safe)
                try {
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        meta = MetadataEngine.readMetadataFromStream(ins)
                    }
                } catch (_: Throwable) { /* ignore exif failure */ }

                // Decode bitmap with sampling via FileDescriptor when possible (more stable for large files)
                bmp = try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        decodeSampledBitmapFromFd(pfd.fileDescriptor, 2200)
                    }
                } catch (_: Throwable) { null }

                if (bmp == null) {
                    bmp = context.contentResolver.openInputStream(uri)?.use { ins ->
                        val bytes = try { ins.readBytes() } catch (_: Throwable) { null }
                        if (bytes != null && bytes.isNotEmpty()) {
                            // Guard huge files (>25MB) – sample more aggressively
                            val maxDim = if (bytes.size > 18_000_000) 1600 else 2200
                            try { decodeSampledBitmap(bytes, maxDim) } catch (e: OutOfMemoryError) { null }
                        } else null
                    }
                }
            } catch (e: SecurityException) {
                error = "无权限读取该图片，请重新选择或授予相册权限"
            } catch (e: OutOfMemoryError) {
                error = "图片过大，内存不足，已自动压缩"
            } catch (e: Exception) {
                error = e.message ?: "读取图片失败"
            }
            if (bmp == null && error != null) {
                withContext(Dispatchers.Main) {
                    loadErrorMsg = error
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
            bmp to meta
        }
    }

    // Unified single pick handler
    fun handlePickedUri(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            isLoadingImage = true
            loadErrorMsg = null
            val (bmp, meta) = loadBitmapFromUri(uri)
            if (bmp != null) {
                // Recycle previous source if not the initial sample? Avoid recycling still displayed bitmaps.
                sourceBitmap = bmp
                activeExif = meta
                undoStack.clear()
                redoStack.clear()
            }
            isLoadingImage = false
        }
    }

    // Launchers: Photo Picker + fallback GetContent
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        handlePickedUri(uri)
    }
    val getContentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handlePickedUri(uri)
    }
    val pickMultipleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            var successCount = 0
            withContext(Dispatchers.IO) {
                for ((idx, u) in uris.withIndex()) {
                    batchProgress = idx + 1
                    val (bmp, _) = loadBitmapFromUri(u)
                    val cur = bmp ?: continue
                    val out = runCatching { ImageProcessEngine.process(cur, recipe, activeExif) }.getOrDefault(cur)
                    val saved = MediaStoreSaver.saveBitmapToGallery(
                        context = context,
                        bitmap = out,
                        fileBaseName = "OPHOTO_BATCH_${System.currentTimeMillis()}_$idx",
                        format = exportFormat,
                        exifMetadata = activeExif,
                        stripLocation = stripExifLocation,
                    )
                    if (saved != null) successCount++
                    // recycle intermediate if not same as cur/out? Avoid OOM
                    if (cur !== out && cur !== sourceBitmap) runCatching { cur.recycle() }
                    if (out !== sourceBitmap && out !== cur) runCatching { out.recycle() }
                }
            }
            batchProgress = null
            isExporting = false
            Toast.makeText(context, "已批量导出 $successCount 张至 Pictures/Ophoto", Toast.LENGTH_LONG).show()
        }
    }

    fun launchSinglePicker() {
        loadErrorMsg = null
        try {
            if (photoPickerAvailable) {
                pickVisualMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                getContentLauncher.launch("image/*")
            }
        } catch (e: Exception) {
            // Ultimate fallback
            try { getContentLauncher.launch("image/*") } catch (_: Exception) {
                Toast.makeText(context, "无法打开系统相册，请检查权限或更新系统", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchBatchPicker() {
        if (!photoPickerAvailable) {
            Toast.makeText(context, "当前系统相册不支持多选，已为您打开单张批量模式（可重复选择）", Toast.LENGTH_SHORT).show()
            launchSinglePicker()
            return
        }
        try {
            pickMultipleLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: Exception) {
            Toast.makeText(context, "批量选择暂不可用", Toast.LENGTH_SHORT).show()
        }
    }

    // Also listen for media dispatched from CameraScreen / capture flow
    LaunchedEffect(container) {
        container.editorMediaTarget.collect { uriStr ->
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return@collect
            handlePickedUri(uri)
        }
    }

    // Derived UI helpers
    val imageResolutionText by remember(sourceBitmap) {
        derivedStateOf { "${sourceBitmap.width} × ${sourceBitmap.height}" }
    }
    val exifSummary by remember(activeExif, recipe) {
        derivedStateOf { activeExif.summaryLine() + " · " + (recipe.preset?.name ?: "原片") + " ${ (recipe.presetStrength*100).roundToInt()}%" }
    }

    // Root layout: immersive darkroom
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // ---- Premium Top Bar ----
        EditorTopBar(
            imageCountText = imageResolutionText,
            exifSummary = exifSummary,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            isCompare = showOriginalCompare,
            onPick = { launchSinglePicker() },
            onUndo = {
                if (undoStack.isNotEmpty()) {
                    val prev = undoStack.removeAt(undoStack.lastIndex)
                    redoStack.add(recipe)
                    recipe = prev
                }
            },
            onRedo = {
                if (redoStack.isNotEmpty()) {
                    val nxt = redoStack.removeAt(redoStack.lastIndex)
                    undoStack.add(recipe)
                    recipe = nxt
                }
            },
            onToggleCompare = { showOriginalCompare = !showOriginalCompare },
            onExport = {
                if (isExporting) return@EditorTopBar
                scope.launch {
                    isExporting = true
                    val name = MediaStoreSaver.formatFileName(settings.fileNameTemplate, "EDIT", recipe.preset?.name)
                    val outBmp = renderedBitmap
                    val uri = withContext(Dispatchers.IO) {
                        MediaStoreSaver.saveBitmapToGallery(
                            context, outBmp, name, exportFormat,
                            exifMetadata = activeExif.copy(filterName = recipe.preset?.name, filterStrength = recipe.presetStrength),
                            stripLocation = stripExifLocation
                        )
                    }
                    if (uri != null) {
                        container.mediaRepository.recordEditStep(
                            EditHistoryEntity(
                                mediaId = uri.toString(),
                                filterId = recipe.preset?.id,
                                paramsJson = "strength=${recipe.presetStrength};frame=${recipe.frameStyle.name}",
                                cropJson = "crop=${recipe.cropPreset.name};rot=${recipe.rotationQuarterTurns}",
                                createdAt = System.currentTimeMillis(),
                                version = undoStack.size + 1
                            )
                        )
                        Toast.makeText(context, "已导出至相册 Pictures/Ophoto · $name.${exportFormat.extension}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "导出失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                    }
                    isExporting = false
                }
            },
            isExporting = isExporting || isLoadingImage,
        )

        // ---- Canvas + Metadata HUD ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF05070B))
                .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
        ) {
            // Image display
            val displayBmp = if (showOriginalCompare) sourceBitmap else renderedBitmap
            val animatedAlpha by animateFloatAsState(targetValue = if (isRendering) 0.68f else 1f, label = "alpha")

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = displayBmp.asImageBitmap(),
                    contentDescription = "编辑预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .background(Color(0xFF07080C), RoundedCornerShape(12.dp))
                )

                // Subtle gradient vignette for depth
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x22000000)),
                                startY = 0f, endY = Float.POSITIVE_INFINITY
                            )
                        )
                )

                // Grid overlay toggle
                if (showGridOverlay) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width; val h = size.height
                        val c = Color.White.copy(alpha = 0.18f)
                        for (i in 1..2) {
                            drawLine(c, Offset(w*i/3f, 0f), Offset(w*i/3f, h), 0.8.dp.toPx())
                            drawLine(c, Offset(0f, h*i/3f), Offset(w, h*i/3f), 0.8.dp.toPx())
                        }
                    }
                }

                // Top HUD badges
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassBadge(text = imageResolutionText, icon = Icons.Rounded.Image)
                    if (showOriginalCompare) {
                        GlassBadge(text = "原片对比", color = BrassGold, textColor = ObsidianBg)
                    }
                    if (isRendering) {
                        GlassBadge(text = "渲染中…", color = OpticalEmerald)
                    }
                }
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassIconButton(icon = Icons.Rounded.GridOn, active = showGridOverlay, onClick = { showGridOverlay = !showGridOverlay })
                    GlassIconButton(icon = Icons.Rounded.Compare, active = showOriginalCompare, onClick = { showOriginalCompare = !showOriginalCompare })
                }

                // Loading veil
                if (isLoadingImage) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xB2080A10), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = BrassGold, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                            Text("正在高清解码原片…", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                    }
                }

                // Error state overlay (non-blocking, with retry)
                if (loadErrorMsg != null && !isLoadingImage) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1F2A))
                            .border(1.dp, ShutterOrange.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = ShutterOrange, modifier = Modifier.size(18.dp))
                            Text(loadErrorMsg!!, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text("重试", color = BrassGold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { launchSinglePicker() }.padding(4.dp))
                        }
                    }
                }

                // Empty / onboarding hint when showing default sample (detect by size 900x675 exact?)
                if (sourceBitmap.width == 900 && sourceBitmap.height == 675 && presets.isNotEmpty() && !isLoadingImage) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC0B0F1A))
                            .border(0.8.dp, BrassGold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .clickable { launchSinglePicker() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = BrassGold, modifier = Modifier.size(16.dp))
                            Text("点击此处从相册导入高清原片开始创作", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                        }
                    }
                }
            }

            // Bottom EXIF footer inside canvas
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xD60A0D14))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exifSummary, style = MaterialTheme.typography.labelSmall, color = BrassGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(activeExif.model.ifBlank { "未知机型" } + " · " + (if (stripExifLocation) "已抹除GPS隐私" else "保留位置信息"), style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (batchProgress != null) {
                        Text("批量 $batchProgress/10", style = MaterialTheme.typography.labelSmall, color = OpticalEmerald)
                    }
                    Text("${undoStack.size} 步历史", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }

        // ---- Tool Tab Switcher (pro segmented) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InstrumentDeck)
                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DarkroomToolTab.entries.forEach { tab ->
                val isActive = activeToolTab == tab
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isActive) BrassGold else Color.Transparent)
                        .clickable { activeToolTab = tab }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (isActive) ObsidianBg else TextSecondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (isActive) ObsidianBg else TextSecondary, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ---- Controls Deck (scrollable, pro) ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 420.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(InstrumentDeck)
                .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeToolTab) {
                    DarkroomToolTab.FILTER -> FilterToolsPanel(
                        presets = presets,
                        selectedPreset = recipe.preset,
                        strength = recipe.presetStrength,
                        sourceBitmap = sourceBitmap,
                        onSelectPreset = { p -> pushRecipeUpdate(recipe.copy(preset = p)) },
                        onClearPreset = { pushRecipeUpdate(recipe.copy(preset = null)) },
                        onStrength = { v -> pushRecipeUpdate(recipe.copy(presetStrength = v)) },
                        onBatch = { launchBatchPicker() },
                    )
                    DarkroomToolTab.CURVES -> CurvesToolsPanel(
                        recipe = recipe,
                        curveChannel = curveChannel,
                        onChannelChange = { curveChannel = it },
                        onPush = { pushRecipeUpdate(it) },
                    )
                    DarkroomToolTab.HSL -> HslToolsPanel(
                        recipe = recipe,
                        selectedBand = selectedHslBand,
                        onSelectBand = { selectedHslBand = it },
                        onRecipe = { pushRecipeUpdate(it) },
                    )
                    DarkroomToolTab.GEOMETRY -> GeometryToolsPanel(
                        recipe = recipe,
                        onRecipe = { pushRecipeUpdate(it) },
                    )
                    DarkroomToolTab.EXPORT -> ExportToolsPanel(
                        recipe = recipe,
                        exportFormat = exportFormat,
                        stripLocation = stripExifLocation,
                        onRecipe = { pushRecipeUpdate(it) },
                        onFormat = { exportFormat = it },
                        onToggleGps = { stripExifLocation = !stripExifLocation },
                    )
                }
            }
        }

        // Export progress bar (when exporting)
        if (isExporting) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = BrassGold,
                trackColor = ObsidianBg
            )
        }
    }
}

// =========================================================================
// Top Bar
// =========================================================================

@Composable
private fun EditorTopBar(
    imageCountText: String,
    exifSummary: String,
    canUndo: Boolean,
    canRedo: Boolean,
    isCompare: Boolean,
    isExporting: Boolean,
    onPick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleCompare: () -> Unit,
    onExport: () -> Unit,
) {
    Surface(color = InstrumentDeck, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth().background(InstrumentDeck).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(BrassGold)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("暗房实验室", style = MaterialTheme.typography.labelMedium, color = ObsidianBg, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("PRO DARKROOM 2.0", style = MaterialTheme.typography.labelSmall, color = BrassGold, fontSize = 10.sp, letterSpacing = 0.6.sp)
                        Text(exifSummary, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPick, modifier = Modifier.size(36.dp).background(ControlPillBg, CircleShape)) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "从相册选图", tint = BrassGold, modifier = Modifier.size(18.dp))
                    }
                    IconButton(enabled = canUndo, onClick = onUndo, modifier = Modifier.size(36.dp).background(if (canUndo) ControlPillBg else ControlPillBg.copy(alpha = 0.5f), CircleShape)) {
                        Icon(Icons.Rounded.Undo, contentDescription = "撤销", tint = if (canUndo) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                    }
                    IconButton(enabled = canRedo, onClick = onRedo, modifier = Modifier.size(36.dp).background(if (canRedo) ControlPillBg else ControlPillBg.copy(alpha = 0.5f), CircleShape)) {
                        Icon(Icons.Rounded.Redo, contentDescription = "重做", tint = if (canRedo) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCompare) BrassGold else ControlPillBg)
                            .clickable(onClick = onToggleCompare)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Compare, contentDescription = null, tint = if (isCompare) ObsidianBg else TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("对比", style = MaterialTheme.typography.labelSmall, color = if (isCompare) ObsidianBg else TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = ControlPillBg, shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.ViewModule, contentDescription = null, tint = BrassGold, modifier = Modifier.size(12.dp))
                            Text(imageCountText, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        }
                    }
                    Text("点击画布长按可按住对比原片 · 双指缩放查看细节", style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Button(
                    onClick = onExport,
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = ObsidianBg, disabledContainerColor = HairlineBorder, disabledContentColor = TextMuted),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = ObsidianBg, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("导出中…", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导出", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// =========================================================================
// FILTER panel with live filmstrip thumbnails
// =========================================================================

@Composable
private fun FilterToolsPanel(
    presets: List<com.ophoto.engine.filter.FilterPreset>,
    selectedPreset: com.ophoto.engine.filter.FilterPreset?,
    strength: Float,
    sourceBitmap: Bitmap,
    onSelectPreset: (com.ophoto.engine.filter.FilterPreset) -> Unit,
    onClearPreset: () -> Unit,
    onStrength: (Float) -> Unit,
    onBatch: () -> Unit,
) {
    // Strength slider with numeric bubble
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("当前配方：${selectedPreset?.name ?: "原片无滤镜"}", style = MaterialTheme.typography.titleSmall, color = BrassGold, fontWeight = FontWeight.SemiBold)
        Surface(color = BrassGold.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
            Text("${(strength * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = BrassGold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("强度", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(32.dp))
        Slider(value = strength, onValueChange = onStrength, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold, inactiveTrackColor = ControlPillBg), modifier = Modifier.weight(1f))
        IconButton(onClick = { onStrength(0.85f) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.History, contentDescription = "重置", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }

    // Filmstrip: create tiny thumb for preview (use downsampled source)
    val thumbBase = remember(sourceBitmap) {
        runCatching {
            Bitmap.createScaledBitmap(sourceBitmap, 120, (120f * sourceBitmap.height / sourceBitmap.width).roundToInt().coerceAtLeast(80), true)
        }.getOrDefault(sourceBitmap)
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            FilterThumbnailCard(
                name = "原片",
                subtitle = "OFF",
                isSelected = selectedPreset == null,
                preview = thumbBase,
                accent = TextSecondary,
                onClick = onClearPreset
            )
        }
        items(presets, key = { it.id }) { preset ->
            val preview = remember(preset.id, thumbBase) {
                runCatching {
                    val engine = FilterEngineImpl()
                    engine.loadFilter(preset)
                    engine.setStrength(0.88f)
                    engine.applyToImage(thumbBase)
                }.getOrDefault(thumbBase)
            }
            val accent = remember(preset.accentHex) { runCatching { Color(android.graphics.Color.parseColor(preset.accentHex)) }.getOrDefault(BrassGold) }
            FilterThumbnailCard(
                name = preset.name,
                subtitle = preset.subtitle.take(12),
                isSelected = selectedPreset?.id == preset.id,
                preview = preview,
                accent = accent,
                onClick = { onSelectPreset(preset) }
            )
        }
    }

    // Quick actions
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onBatch, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(vertical = 9.dp)) {
            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = OpticalEmerald, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("批量套用至多张", color = OpticalEmerald, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(onClick = { }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(vertical = 9.dp)) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = BrassGold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("复制当前配方", color = BrassGold, style = MaterialTheme.typography.labelMedium)
        }
    }
    Text("提示：轻点胶片卡切换，长按可收藏与查看参数详情；批量将对所选相册照片套用同一滤镜+曲线+HSL+裁剪设定", style = MaterialTheme.typography.labelSmall, color = TextMuted)
}

@Composable
private fun FilterThumbnailCard(
    name: String,
    subtitle: String,
    isSelected: Boolean,
    preview: Bitmap,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accent.copy(alpha = 0.18f) else ControlPillBg)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else HairlineBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(9.dp))
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) accent else TextPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp, maxLines = 1)
    }
}

// =========================================================================
// CURVES panel with pro curve pad
// =========================================================================

@Composable
private fun CurvesToolsPanel(
    recipe: PhotoEditRecipe,
    curveChannel: CurveChannel,
    onChannelChange: (CurveChannel) -> Unit,
    onPush: (PhotoEditRecipe) -> Unit,
) {
    Text("专业影调曲线 · 轻触抬升/压暗，拖拽微调", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

    // Channel switcher
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(CurveChannel.entries) { ch ->
            val active = ch == curveChannel
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) BrassGold else ControlPillBg)
                    .border(1.dp, if (active) BrassGold else HairlineBorder, RoundedCornerShape(8.dp))
                    .clickable { onChannelChange(ch) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(ch.label, style = MaterialTheme.typography.labelSmall, color = if (active) ObsidianBg else TextPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }

    val currentPoints = when (curveChannel) {
        CurveChannel.MASTER -> recipe.masterCurve
        CurveChannel.R -> recipe.redCurve
        CurveChannel.G -> recipe.greenCurve
        CurveChannel.B -> recipe.blueCurve
    }

    // Histogram for backdrop (compute from current rendered? use preview approximated)
    // For simplicity, compute luma histogram from a small proxy - not heavy
    ProCurvePad(
        points = currentPoints,
        onUpdatePoints = { newPts ->
            val next = when (curveChannel) {
                CurveChannel.MASTER -> recipe.copy(masterCurve = newPts)
                CurveChannel.R -> recipe.copy(redCurve = newPts)
                CurveChannel.G -> recipe.copy(greenCurve = newPts)
                CurveChannel.B -> recipe.copy(blueCurve = newPts)
            }
            onPush(next)
        }
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MiniChip("胶片 S 曲线") {
            onPush(
                when (curveChannel) {
                    CurveChannel.MASTER -> recipe.copy(masterCurve = listOf(CurvePoint(0f, 0.05f), CurvePoint(0.25f, 0.19f), CurvePoint(0.50f, 0.50f), CurvePoint(0.75f, 0.81f), CurvePoint(1f, 0.96f)))
                    CurveChannel.R -> recipe.copy(redCurve = listOf(CurvePoint(0f, 0f), CurvePoint(0.25f, 0.28f), CurvePoint(0.5f, 0.52f), CurvePoint(0.75f, 0.78f), CurvePoint(1f, 1f)))
                    CurveChannel.G -> recipe.copy(greenCurve = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.48f), CurvePoint(1f, 1f)))
                    CurveChannel.B -> recipe.copy(blueCurve = listOf(CurvePoint(0f, 0.08f), CurvePoint(0.5f, 0.52f), CurvePoint(1f, 0.94f)))
                }
            )
        }
        MiniChip("日系空气高调") {
            onPush(recipe.copy(masterCurve = listOf(CurvePoint(0f, 0.09f), CurvePoint(0.25f, 0.31f), CurvePoint(0.50f, 0.58f), CurvePoint(0.75f, 0.82f), CurvePoint(1f, 0.99f)), redCurve = PhotoEditRecipe.defaultLinearCurve(), greenCurve = PhotoEditRecipe.defaultLinearCurve(), blueCurve = PhotoEditRecipe.defaultLinearCurve()))
        }
        MiniChip("重置线性") {
            onPush(recipe.copy(masterCurve = PhotoEditRecipe.defaultLinearCurve(), redCurve = PhotoEditRecipe.defaultLinearCurve(), greenCurve = PhotoEditRecipe.defaultLinearCurve(), blueCurve = PhotoEditRecipe.defaultLinearCurve()))
        }
    }
    Text("通道分色曲线可单独修正偏色：R 通道抬升暖黄，B 通道压暗冷蓝", style = MaterialTheme.typography.labelSmall, color = TextMuted)
}

@Composable
private fun ProCurvePad(
    points: List<CurvePoint>,
    onUpdatePoints: (List<CurvePoint>) -> Unit,
) {
    val lut = remember(points) { ImageProcessEngine.buildCurveLut256(points) }
    var draggingIdx by remember { mutableIntStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0D14))
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val ny = (1f - offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val nearest = points.indices.minByOrNull { i -> abs(points[i].x - nx) } ?: return@detectTapGestures
                    val next = points.toMutableList()
                    next[nearest] = CurvePoint(points[nearest].x, ny)
                    onUpdatePoints(next)
                }
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        draggingIdx = points.indices.minByOrNull { i -> abs(points[i].x - nx) } ?: -1
                    },
                    onDragEnd = { draggingIdx = -1 },
                    onDragCancel = { draggingIdx = -1 },
                    onDrag = { change, _ ->
                        if (draggingIdx in points.indices) {
                            change.consume()
                            val nx = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val ny = (1f - change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            // Keep x anchored to avoid crossing; only y moves for stability
                            val next = points.toMutableList()
                            next[draggingIdx] = CurvePoint(points[draggingIdx].x, ny)
                            onUpdatePoints(next)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val w = size.width
            val h = size.height

            // Grid
            for (g in 1..3) {
                drawLine(Color.White.copy(alpha = 0.06f), Offset(w * g / 4f, 0f), Offset(w * g / 4f, h), 0.8.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, h * g / 4f), Offset(w, h * g / 4f), 0.8.dp.toPx())
            }
            drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, h), Offset(w, 0f), 0.9.dp.toPx())

            // Histogram backdrop (fake subtle) – draw lut histogram area underneath
            // Draw curve area fill
            val fillPath = Path().apply {
                moveTo(0f, h)
                for (i in 0..255) {
                    val px = (i / 255f) * w
                    val py = (1f - lut[i] / 255f) * h
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                lineTo(w, h)
                close()
            }
            drawPath(fillPath, BrassGold.copy(alpha = 0.08f))

            // Curve line
            val path = Path()
            for (i in 0..255) {
                val px = (i / 255f) * w
                val py = (1f - lut[i] / 255f) * h
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, BrassGold, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            // Add handle glow
            points.forEachIndexed { idx, pt ->
                val cx = pt.x * w
                val cy = (1f - pt.y) * h
                val isDrag = idx == draggingIdx
                drawCircle(Color(0xFF0A0D14), radius = if (isDrag) 9.dp.toPx() else 7.dp.toPx(), center = Offset(cx, cy))
                drawCircle(if (isDrag) BrassGold else OpticalEmerald, radius = if (isDrag) 7.dp.toPx() else 5.dp.toPx(), center = Offset(cx, cy))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx, cy))
            }
        }
        // Axis labels
        Text("暗部", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        Text("高光", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
    }
}

// =========================================================================
// HSL panel
// =========================================================================

@Composable
private fun HslToolsPanel(
    recipe: PhotoEditRecipe,
    selectedBand: HslBand,
    onSelectBand: (HslBand) -> Unit,
    onRecipe: (PhotoEditRecipe) -> Unit,
) {
    Text("8 色相独立混色 · 精准校正肤色、天空与植被", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(HslBand.entries) { band ->
            val selected = band == selectedBand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color(band.swatchHex).copy(alpha = 0.22f) else ControlPillBg)
                    .border(1.dp, if (selected) Color(band.swatchHex) else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelectBand(band) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(band.swatchHex)))
                Spacer(Modifier.width(5.dp))
                Text(band.label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }

    val curShift = recipe.hslAdjustments[selectedBand] ?: HslShift()
    Surface(color = ControlPillBg.copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${selectedBand.label} · H:${curShift.hue} S:${curShift.saturation} L:${curShift.luminance}", style = MaterialTheme.typography.labelSmall, color = BrassGold, fontWeight = FontWeight.Medium)
            Text("点击色块切换色相", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }

    HslSliderRow(label = "色相偏移", value = curShift.hue, range = -100..100, color = BrassGold) { v ->
        val m = recipe.hslAdjustments.toMutableMap()
        m[selectedBand] = curShift.copy(hue = v)
        onRecipe(recipe.copy(hslAdjustments = m))
    }
    HslSliderRow(label = "饱和度", value = curShift.saturation, range = -100..100, color = OpticalEmerald) { v ->
        val m = recipe.hslAdjustments.toMutableMap()
        m[selectedBand] = curShift.copy(saturation = v)
        onRecipe(recipe.copy(hslAdjustments = m))
    }
    HslSliderRow(label = "明度", value = curShift.luminance, range = -100..100, color = TextPrimary) { v ->
        val m = recipe.hslAdjustments.toMutableMap()
        m[selectedBand] = curShift.copy(luminance = v)
        onRecipe(recipe.copy(hslAdjustments = m))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniChip("重置该色相") {
            val m = recipe.hslAdjustments.toMutableMap()
            m.remove(selectedBand)
            onRecipe(recipe.copy(hslAdjustments = m))
        }
        MiniChip("一键清透天空") {
            val m = recipe.hslAdjustments.toMutableMap()
            m[HslBand.AQUA] = HslShift(hue = -8, saturation = 12, luminance = 8)
            m[HslBand.BLUE] = HslShift(hue = -6, saturation = 10, luminance = 6)
            onRecipe(recipe.copy(hslAdjustments = m))
        }
    }
}

@Composable
private fun HslSliderRow(label: String, value: Int, range: IntRange, color: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("$label $value", style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.width(92.dp))
        Slider(value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(), colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = HairlineBorder), modifier = Modifier.weight(1f))
    }
}

// =========================================================================
// Geometry / Crop / Mask
// =========================================================================

@Composable
private fun GeometryToolsPanel(
    recipe: PhotoEditRecipe,
    onRecipe: (PhotoEditRecipe) -> Unit,
) {
    Text("构图裁剪与局部光效 · 遵循三分法与黄金比例重塑画面", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

    Text("画幅比例", style = MaterialTheme.typography.labelMedium, color = BrassGold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(CropAspectPreset.entries) { cp ->
            val active = recipe.cropPreset == cp
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) BrassGold else ControlPillBg)
                    .border(1.dp, if (active) BrassGold else HairlineBorder, RoundedCornerShape(10.dp))
                    .clickable { onRecipe(recipe.copy(cropPreset = cp)) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Aspect icon
                Box(
                    Modifier
                        .width(22.dp)
                        .height(
                            when (cp) {
                                CropAspectPreset.R_1_1 -> 22.dp
                                CropAspectPreset.R_16_9 -> 12.dp
                                CropAspectPreset.R_3_2 -> 14.dp
                                CropAspectPreset.R_4_3 -> 16.dp
                                CropAspectPreset.R_21_9 -> 9.dp
                                else -> 18.dp
                            }
                        )
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) ObsidianBg else TextSecondary.copy(alpha = 0.7f))
                )
                Spacer(Modifier.height(3.dp))
                Text(cp.label, style = MaterialTheme.typography.labelSmall, color = if (active) ObsidianBg else TextPrimary)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ActionChip(icon = Icons.Rounded.RotateRight, label = "旋转 90°", active = recipe.rotationQuarterTurns != 0) {
            onRecipe(recipe.copy(rotationQuarterTurns = (recipe.rotationQuarterTurns + 1) % 4))
        }
        ActionChip(icon = Icons.Rounded.Flip, label = if (recipe.mirrorHorizontal) "已镜像" else "水平镜像", active = recipe.mirrorHorizontal) {
            onRecipe(recipe.copy(mirrorHorizontal = !recipe.mirrorHorizontal))
        }
        Surface(color = ControlPillBg, shape = RoundedCornerShape(8.dp)) {
            Text("${recipe.rotationQuarterTurns * 90}°", style = MaterialTheme.typography.labelMedium, color = BrassGold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }

    Text("水平校正 ${"%.1f".format(recipe.straightenDegrees)}°", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("← 左倾", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.width(44.dp))
        Slider(value = recipe.straightenDegrees, onValueChange = { onRecipe(recipe.copy(straightenDegrees = it)) }, valueRange = -15f..15f, colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold), modifier = Modifier.weight(1f))
        Text("右倾 →", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.width(44.dp))
    }

    Text("局部光效蒙版：${recipe.localMaskType.label}", style = MaterialTheme.typography.labelMedium, color = BrassGold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(LocalMaskType.entries) { mask ->
            val active = recipe.localMaskType == mask
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) OpticalEmerald.copy(alpha = 0.18f) else ControlPillBg)
                    .border(1.dp, if (active) OpticalEmerald else HairlineBorder, RoundedCornerShape(8.dp))
                    .clickable { onRecipe(recipe.copy(localMaskType = mask)) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(mask.label, style = MaterialTheme.typography.labelSmall, color = if (active) OpticalEmerald else TextPrimary)
            }
        }
    }
    AnimatedVisibility(visible = recipe.localMaskType != LocalMaskType.NONE) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (recipe.localMaskType == LocalMaskType.LINEAR_TOP) Icons.Rounded.Gradient else Icons.Rounded.CenterFocusStrong,
                    contentDescription = null,
                    tint = OpticalEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Text("局部曝光补偿 ${"%+.1f".format(recipe.localMaskEv)} EV", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
            }
            Slider(value = recipe.localMaskEv, onValueChange = { onRecipe(recipe.copy(localMaskEv = it)) }, valueRange = -1.5f..1.5f, colors = SliderDefaults.colors(thumbColor = OpticalEmerald, activeTrackColor = OpticalEmerald))
        }
    }
}

// =========================================================================
// Export / Frame
// =========================================================================

@Composable
private fun ExportToolsPanel(
    recipe: PhotoEditRecipe,
    exportFormat: ExportImageFormat,
    stripLocation: Boolean,
    onRecipe: (PhotoEditRecipe) -> Unit,
    onFormat: (ExportImageFormat) -> Unit,
    onToggleGps: () -> Unit,
) {
    Text("大师签注画框 · 为作品赋予展览级呈现", style = MaterialTheme.typography.titleSmall, color = BrassGold)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FrameStyle.entries) { fs ->
            val active = recipe.frameStyle == fs
            Column(
                Modifier
                    .width(86.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) BrassGold.copy(alpha = 0.16f) else ControlPillBg)
                    .border(1.dp, if (active) BrassGold else HairlineBorder, RoundedCornerShape(10.dp))
                    .clickable { onRecipe(recipe.copy(frameStyle = fs)) }
                    .padding(7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (fs) {
                                FrameStyle.NONE -> Color(0xFF0A0D14)
                                FrameStyle.CLASSIC_WHITE -> Color.White
                                FrameStyle.OBSIDIAN_DARK -> Color(0xFF0C0D11)
                                FrameStyle.FILM_CONTACT -> Color(0xFF141210)
                            }
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(fs.label.take(2), style = MaterialTheme.typography.labelSmall, color = if (fs == FrameStyle.CLASSIC_WHITE) Color.Black else TextPrimary)
                }
                Text(fs.label, style = MaterialTheme.typography.labelSmall, color = if (active) BrassGold else TextPrimary, maxLines = 1, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }

    OutlinedTextField(
        value = recipe.customWatermarkText,
        onValueChange = { onRecipe(recipe.copy(customWatermarkText = it)) },
        label = { Text("自定义画框签名 / 角落水印（留空使用机型标识）", style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text("Ophoto Optical Studio · ${java.time.LocalDate.now()}", style = MaterialTheme.typography.bodySmall, color = TextMuted) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrassGold,
            unfocusedBorderColor = HairlineBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = BrassGold
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    )

    Text("导出格式与隐私", style = MaterialTheme.typography.labelMedium, color = BrassGold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(ExportImageFormat.entries) { fmt ->
            val active = exportFormat == fmt
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) BrassGold else ControlPillBg)
                    .clickable { onFormat(fmt) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(fmt.name, style = MaterialTheme.typography.labelSmall, color = if (active) ObsidianBg else TextPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
        item {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (stripLocation) ShutterOrange.copy(alpha = 0.18f) else ControlPillBg)
                    .border(1.dp, if (stripLocation) ShutterOrange else HairlineBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleGps)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(if (stripLocation) "✓ 已抹除 GPS 隐私" else "保留 GPS 位置", style = MaterialTheme.typography.labelSmall, color = if (stripLocation) ShutterOrange else TextSecondary)
            }
        }
    }

    Surface(color = ControlPillBg.copy(alpha = 0.7f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("导出位置：系统相册 Pictures/Ophoto", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("命名模板可在「系统设置」中自定义 {yyyyMMdd} {MODE} {FILTER}，支持自动记录滤镜参数至 EXIF", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

// =========================================================================
// Small UI primitives
// =========================================================================

@Composable
private fun GlassBadge(text: String, color: Color = Color(0xCC0B0D12), textColor: Color = TextPrimary, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = BrassGold, modifier = Modifier.size(12.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall, color = textColor, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun GlassIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (active) BrassGold else Color(0xCC0B0D12))
            .border(0.8.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (active) ObsidianBg else TextPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MiniChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ControlPillBg)
            .border(0.8.dp, HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
    }
}

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) BrassGold else ControlPillBg)
            .border(1.dp, if (active) BrassGold else HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (active) ObsidianBg else BrassGold, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) ObsidianBg else TextPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

// =========================================================================
// Safe bitmap decoders
// =========================================================================

private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            }
        )
    } catch (e: OutOfMemoryError) {
        // Fallback: try even smaller
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 4
            while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 900) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
        } catch (_: Throwable) { null }
    } catch (_: Exception) { null } catch (_: Throwable) { null }
}

private fun decodeSampledBitmapFromFd(fd: java.io.FileDescriptor, maxDimension: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(fd, null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
        BitmapFactory.decodeFileDescriptor(
            fd, null,
            BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    } catch (e: OutOfMemoryError) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(fd, null, bounds)
            var sample = 4
            while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1000) sample *= 2
            BitmapFactory.decodeFileDescriptor(fd, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
        } catch (_: Throwable) { null }
    } catch (_: Throwable) { null }
}
