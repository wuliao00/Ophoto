package com.ophoto.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ophoto.BuildConfig
import com.ophoto.OphotoContainer
import com.ophoto.core.device.OppoDeviceInspector
import com.ophoto.core.log.SafeLogger
import com.ophoto.engine.camera.CameraX2Engine
import com.ophoto.engine.camera.MeteringSample
import com.ophoto.core.storage.MediaStoreSaver
import com.ophoto.data.local.FloatingHudStyle
import com.ophoto.data.local.ViewfinderBrightnessMode
import com.ophoto.data.local.UserSettings
import com.ophoto.data.local.VideoLutMode
import com.ophoto.data.local.VolumeKeyAction
import com.ophoto.ui.camera.TopHudChip
import com.ophoto.ui.theme.AlertCrimson
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.ShutterOrange
import com.ophoto.ui.theme.OphotoPageHeader
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    container: OphotoContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.preferences.settingsFlow.collectAsState(initial = UserSettings())
    val hw = container.hardwareProfile

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            OphotoPageHeader(
                title = "设置",
                subtitle = "${hw.displayTitle} · ${hw.maxRefreshRateHz}Hz · 全部数据保存在本机",
            )
        }
        // 交互（Deference）：取景时控件自动隐去，轻触唤回
        item {
            SettingsGroupCard("交互手感", icon = Icons.Rounded.Tune) {
                SettingSwitchRow("取景时 4 秒后自动隐去工具栏", settings.autoHideChrome) { v ->
                    scope.launch { container.preferences.update { it.copy(autoHideChrome = v) } }
                }
                SettingSwitchRow("长按画面隐藏全部叠层（纯构图）", settings.longPressCleanView) { v ->
                    scope.launch { container.preferences.update { it.copy(longPressCleanView = v) } }
                }
            }
        }
        // 1. OPPO / ColorOS Hardware & Physical Lens Inspector
        item {
            SettingsGroupCard("镜头与传感器", icon = Icons.Rounded.CameraAlt) {
                Text(
                    text = hw.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = OpticalEmerald,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${hw.romVersionHint}  ·  ${hw.maxRefreshRateHz}Hz  ·  ${hw.mainSensorFormat}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                if (hw.lenses.isEmpty()) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1F2A)).border(0.8.dp, HairlineBorder, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        Text("授予相机权限后可一键枚举全部物理副摄焦段、ISO 上限与 DNG RAW 支持", style = MaterialTheme.typography.labelSmall, color = BrassGold)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        hw.lenses.forEach { lens ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF12151E)).border(0.8.dp, HairlineBorder, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("● ${lens.roleLabel}  ·  ID ${lens.cameraId}", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("f/${lens.maxAperture ?: 1.8f}  ·  ISO ${lens.isoRange.first}–${lens.isoRange.last}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                                Box(Modifier.clip(CircleShape).background(if (lens.supportsRaw) OpticalEmerald.copy(0.22f) else Color(0xFF252C3A)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(if (lens.supportsRaw) "RAW" else "JPEG", style = MaterialTheme.typography.labelSmall, color = if (lens.supportsRaw) OpticalEmerald else TextMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1.5 取景亮度与暗场自诊断
        item {
            CameraHealthCard(container)
        }

        // 2. Camera & HUD Controls
        item {
            SettingsGroupCard("取景叠加与操控", icon = Icons.Rounded.Brush) {
                SettingSwitchRow("电子水平仪 0.0°翡翠对齐", settings.showLeveler) { v -> scope.launch { container.preferences.update { it.copy(showLeveler = v) } } }
                SettingSwitchRow("实时直方图浮层", settings.showHistogram) { v -> scope.launch { container.preferences.update { it.copy(showHistogram = v) } } }
                SettingSwitchRow("高光斑马纹预警 96%", settings.showZebraStripes) { v -> scope.launch { container.preferences.update { it.copy(showZebraStripes = v) } } }
                SettingSwitchRow("曝光与焦段 HUD", settings.showParameterHud) { v -> scope.launch { container.preferences.update { it.copy(showParameterHud = v) } } }
                SettingSwitchRow("触屏即拍", settings.touchToCapture) { v -> scope.launch { container.preferences.update { it.copy(touchToCapture = v) } } }
                SettingSwitchRow("前摄镜像保持", settings.mirrorFrontCamera) { v -> scope.launch { container.preferences.update { it.copy(mirrorFrontCamera = v) } } }
                Spacer(Modifier.height(6.dp))
                Text("取景亮度（只影响屏幕观感，不改变成片曝光）", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ViewfinderBrightnessMode.entries) { mode ->
                        TopHudChip(mode.label, settings.viewfinderBrightness == mode) {
                            scope.launch { container.preferences.update { it.copy(viewfinderBrightness = mode) } }
                        }
                    }
                }
                SettingSwitchRow("暗场自动提亮（画面偏暗时自诊断）", settings.autoBrightnessRescue) { v ->
                    scope.launch { container.preferences.update { it.copy(autoBrightnessRescue = v) } }
                }
                SettingSwitchRow("点屏幕保持 AE 曝光锁定", settings.keepAeOnTap) { v ->
                    scope.launch { container.preferences.update { it.copy(keepAeOnTap = v) } }
                }
                Spacer(Modifier.height(6.dp))
                Text("音量键映射", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VolumeKeyAction.entries) { act ->
                        TopHudChip(act.label, settings.volumeKeyAction == act) { scope.launch { container.preferences.update { it.copy(volumeKeyAction = act) } } }
                    }
                }
            }
        }

        // 3. Filter, Video LUT Performance & Floating Window
        item {
            SettingsGroupCard("滤镜与悬浮参考", icon = Icons.Rounded.Memory) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("默认强度 ${(settings.defaultFilterStrength * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = BrassGold, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(BrassGold.copy(0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("${(settings.defaultFilterStrength*100).toInt()}%", color = BrassGold, style = MaterialTheme.typography.labelSmall) }
                }
                Slider(value = settings.defaultFilterStrength, onValueChange = { s -> scope.launch { container.preferences.update { it.copy(defaultFilterStrength = s) } } }, valueRange = 0.2f..1.0f, colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold))
                Text("录像 LUT 策略", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VideoLutMode.entries) { mode -> TopHudChip(mode.label, settings.videoLutMode == mode) { scope.launch { container.preferences.update { it.copy(videoLutMode = mode) } } } }
                }
                Spacer(Modifier.height(6.dp))
                Text("悬浮窗形态 · ${(settings.floatingHudOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FloatingHudStyle.entries) { st -> TopHudChip(st.label, settings.floatingHudStyle == st) { scope.launch { container.preferences.update { it.copy(floatingHudStyle = st) } } } }
                }
                Slider(value = settings.floatingHudOpacity, onValueChange = { op -> scope.launch { container.preferences.update { it.copy(floatingHudOpacity = op) } } }, valueRange = 0.50f..1.0f, colors = SliderDefaults.colors(thumbColor = OpticalEmerald, activeTrackColor = OpticalEmerald))
                OutlinedButton(onClick = { context.startActivity(OppoDeviceInspector.buildAppDetailsIntent(context)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                    Text("打开权限详情（ColorOS 受限设置）", color = BrassGold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 4. Storage Naming & Privacy Sandbox
        item {
            SettingsGroupCard("存储与隐私", icon = Icons.Rounded.Security) {
                OutlinedTextField(
                    value = settings.fileNameTemplate,
                    onValueChange = { tpl -> scope.launch { container.preferences.update { it.copy(fileNameTemplate = tpl) } } },
                    label = { Text("命名模板 {yyyyMMdd} {HHmmss} {MODE} {FILTER}", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrassGold, unfocusedBorderColor = HairlineBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF141926)).border(0.8.dp, HairlineBorder, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text("预览：${MediaStoreSaver.formatFileName(settings.fileNameTemplate, "PRO", "青绿胶片")}.jpg", style = MaterialTheme.typography.labelSmall, color = OpticalEmerald)
                }
                SettingSwitchRow("保存 DNG RAW", settings.enableRawWhenSupported) { v -> scope.launch { container.preferences.update { it.copy(enableRawWhenSupported = v) } } }
                SettingSwitchRow("写入 GPS 位置（默认关闭）", settings.saveGpsLocation) { v -> scope.launch { container.preferences.update { it.copy(saveGpsLocation = v) } } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { File(context.cacheDir, "exports").deleteRecursively(); SafeLogger.clear(); Toast.makeText(context, "已清理缓存与日志", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) { Text("清理缓存", color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(onClick = { scope.launch { container.filterRepository.clearAllUserImportedFilters(); Toast.makeText(context, "已重置导入滤镜", Toast.LENGTH_SHORT).show() } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) { Text("重置滤镜", color = TextSecondary, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }

        item {
            SettingsGroupCard("关于 Ophoto", icon = Icons.Rounded.FolderSpecial) {
                Text(
                    "Ophoto ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})  ·  Luminous Viewfinder  ·  100% 原创滤镜 · 离线计算 · 本地存储",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text("MIT 开源 · 摄影师工具 · 为 OPPO/一加/realme 优化", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(com.ophoto.ui.theme.MaterialThin)
            .border(0.7.dp, com.ophoto.ui.theme.MaterialHairline, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = BrassGold, modifier = Modifier.size(15.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = BrassGold, uncheckedThumbColor = TextMuted, uncheckedTrackColor = Color(0xFF212734)))
    }
}

/**
 * 相机体检卡：把「为什么取景黑」变成可读的数据。
 * - 显示当前绑定会话能力（预览 / 拍照 / 录像 / 高分辨率）；
 * - 显示真实预览分辨率；
 * - 可触发一次真实传感器测光（32×32 YUV），给出实测亮度与 AE 状态；
 * - 内嵌运行日志尾部，方便用户反馈问题时截图。
 */
@Composable
private fun CameraHealthCard(container: OphotoContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var probeResult by remember { mutableStateOf<MeteringSample?>(null) }
    var probing by remember { mutableStateOf(false) }
    var probeFailed by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf(SafeLogger.snapshot().takeLast(12)) }

    val engine = container.cameraEngine as? CameraX2Engine
    val binding = engine?.currentBinding()

    SettingsGroupCard("相机体检", icon = Icons.Rounded.CameraAlt) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(
                label = "预览",
                value = if (binding != null) "正常" else "待启动",
                accent = if (binding != null) OpticalEmerald else TextMuted,
                modifier = Modifier.weight(1f),
            )
            StatusPill(
                label = "录像",
                value = when {
                    binding == null -> "未知"
                    binding.videoAvailable -> "可用"
                    else -> "降级"
                },
                accent = when {
                    binding == null -> TextMuted
                    binding.videoAvailable -> OpticalEmerald
                    else -> BrassGold
                },
                modifier = Modifier.weight(1f),
            )
            StatusPill(
                label = "高像素",
                value = when {
                    binding == null -> "未知"
                    binding.highResolutionPhoto -> "开启"
                    else -> "标准"
                },
                accent = if (binding?.highResolutionPhoto == true) OpticalEmerald else TextMuted,
                modifier = Modifier.weight(1f),
            )
        }
        val format = engine?.previewFormat
        Text(
            text = "预览分辨率：${format?.display ?: "进入相机页后显示"}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        binding?.degradedReason?.let { reason ->
            Text(
                text = "会话提示：$reason",
                style = MaterialTheme.typography.labelSmall,
                color = BrassGold,
            )
        }
        OutlinedButton(
            onClick = {
                if (probing) return@OutlinedButton
                probing = true
                probeFailed = false
                scope.launch {
                    val sample = container.cameraEngine.probeViewfinderBrightness()
                    probeResult = sample
                    probeFailed = sample == null
                    probing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(11.dp),
        ) {
            Text(
                text = when {
                    probing -> "正在读取真实传感器亮度…"
                    probeResult != null -> "重新测光"
                    else -> "测一帧真实亮度"
                },
                color = BrassGold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        probeResult?.let { sample: MeteringSample ->
            val verdict: String = when {
                sample.isBlackFrame -> "疑似黑场：请点「修复曝光」，或检查镜头是否被遮挡"
                sample.isUnderexposed -> "曝光偏暗：建议提亮取景或提高 EV"
                sample.isOverexposed -> "过曝：建议降低 EV，避免高光溢出"
                else -> "曝光正常"
            }
            Text(
                text = "实测亮度 ${sample.brightnessPercent}%  ·  ISO ${sample.iso}  ·  ${sample.aeStateLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = if (sample.isBlackFrame) AlertCrimson else OpticalEmerald,
            )
            Text(
                text = verdict,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        if (probeFailed) {
            Text(
                text = "该机型不允许第三方应用短暂独占相机，探针不可用（不影响取景）。",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    container.cameraEngine.resetExposureBaseline()
                    Toast.makeText(context, "已恢复自动 AE/AWB 基线", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(11.dp),
            ) { Text("重置曝光基线", color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = {
                    logLines = SafeLogger.snapshot().takeLast(12)
                    showLog = !showLog
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(11.dp),
            ) { Text(if (showLog) "收起日志" else "查看运行日志", color = TextSecondary, style = MaterialTheme.typography.labelMedium) }
        }
        if (showLog) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A0D13))
                    .border(0.8.dp, HairlineBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (logLines.isEmpty()) {
                    Text("暂无日志", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                logLines.forEach { line ->
                    Text(
                        text = line.substringAfter("] "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if ("ERR" in line) AlertCrimson else if ("WARN" in line) BrassGold else TextSecondary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF12151E))
            .border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
    }
}
