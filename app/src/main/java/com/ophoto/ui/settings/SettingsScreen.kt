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
import com.ophoto.OphotoContainer
import com.ophoto.core.device.OppoDeviceInspector
import com.ophoto.core.log.SafeLogger
import com.ophoto.core.storage.MediaStoreSaver
import com.ophoto.data.local.FloatingHudStyle
import com.ophoto.data.local.UserSettings
import com.ophoto.data.local.VideoLutMode
import com.ophoto.data.local.VolumeKeyAction
import com.ophoto.ui.camera.TopHudChip
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.PremiumHeroHeader
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
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0E14), ObsidianBg)))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            PremiumHeroHeader(
                eyebrow = "SYSTEM STUDIO",
                title = "系统工作室 · 设置",
                subtitle = "本地零上传 · OPPO/OnePlus/realme 多摄与 ColorOS 深度适配",
                icon = Icons.Rounded.Tune,
                stats = listOf("设备" to hw.displayTitle.take(12), "刷新率" to "${hw.maxRefreshRateHz}Hz", "滤镜" to "12 预设")
            )
        }
        // 1. OPPO / ColorOS Hardware & Physical Lens Inspector
        item {
            SettingsGroupCard("📷 多摄光学能力  ·  ${hw.ecosystem.displayLabel}", icon = Icons.Rounded.CameraAlt) {
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

        // 2. Camera & HUD Controls
        item {
            SettingsGroupCard("🎛️ 取景与交互  ·  仪器级控制", icon = Icons.Rounded.Brush) {
                SettingSwitchRow("电子水平仪 0.0°翡翠对齐", settings.showLeveler) { v -> scope.launch { container.preferences.update { it.copy(showLeveler = v) } } }
                SettingSwitchRow("实时直方图浮层", settings.showHistogram) { v -> scope.launch { container.preferences.update { it.copy(showHistogram = v) } } }
                SettingSwitchRow("高光斑马纹预警 96%", settings.showZebraStripes) { v -> scope.launch { container.preferences.update { it.copy(showZebraStripes = v) } } }
                SettingSwitchRow("曝光与焦段 HUD", settings.showParameterHud) { v -> scope.launch { container.preferences.update { it.copy(showParameterHud = v) } } }
                SettingSwitchRow("触屏即拍", settings.touchToCapture) { v -> scope.launch { container.preferences.update { it.copy(touchToCapture = v) } } }
                SettingSwitchRow("前摄镜像保持", settings.mirrorFrontCamera) { v -> scope.launch { container.preferences.update { it.copy(mirrorFrontCamera = v) } } }
                Spacer(Modifier.height(6.dp))
                Text("音量键映射", style = MaterialTheme.typography.labelMedium, color = BrassGold, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VolumeKeyAction.entries) { act ->
                        TopHudChip(act.label, settings.volumeKeyAction == act) { scope.launch { container.preferences.update { it.copy(volumeKeyAction = act) } } }
                    }
                }
            }
        }

        // 3. Filter, Video LUT Performance & Floating Window
        item {
            SettingsGroupCard("🎞 滤镜与大师悬浮窗", icon = Icons.Rounded.Memory) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("默认强度 ${(settings.defaultFilterStrength * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = BrassGold, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(BrassGold.copy(0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("${(settings.defaultFilterStrength*100).toInt()}%", color = BrassGold, style = MaterialTheme.typography.labelSmall) }
                }
                Slider(value = settings.defaultFilterStrength, onValueChange = { s -> scope.launch { container.preferences.update { it.copy(defaultFilterStrength = s) } } }, valueRange = 0.2f..1.0f, colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold))
                Text("录像 LUT 功耗策略", style = MaterialTheme.typography.labelMedium, color = BrassGold)
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
            SettingsGroupCard("🔒 存储与隐私沙盒", icon = Icons.Rounded.Security) {
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
            SettingsGroupCard("ℹ️ 关于 · Ophoto", icon = Icons.Rounded.FolderSpecial) {
                Text("Ophoto 1.2.1  ·  Darkroom Stable Hotfix  ·  100% 原创滤镜 · 离线计算 · 本地存储", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF161B26), Color(0xFF0F121A))), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(0.07f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(BrassGold.copy(0.16f)).border(0.8.dp, BrassGold.copy(0.30f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = BrassGold, modifier = Modifier.size(14.dp)) }
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineBorder.copy(0.6f)))
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF12151E)).border(0.8.dp, HairlineBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = BrassGold, uncheckedThumbColor = TextMuted, uncheckedTrackColor = Color(0xFF212734)))
    }
}
