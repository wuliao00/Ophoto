package com.ophoto.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Text("设置与机型适配中心", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(
                "本地零上传 · 针对 OPPO / OnePlus / realme 多摄、高刷与 ColorOS 深度优化",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        // 1. OPPO / ColorOS Hardware & Physical Lens Inspector
        item {
            SettingsGroupCard("📱 设备与物理多摄能力检测") {
                Text(
                    text = "${hw.displayTitle}  ·  ${hw.ecosystem.displayLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    color = OpticalEmerald,
                )
                Text(
                    text = "系统版本：${hw.romVersionHint}  |  最高刷新率：${hw.maxRefreshRateHz}Hz  |  主摄规格参考：${hw.mainSensorFormat}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                if (hw.lenses.isEmpty()) {
                    Text(
                        text = "提示：授予相机权限后可枚举全部物理副摄焦段、ISO 上限与 DNG RAW 支持情况。",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassGold,
                    )
                } else {
                    hw.lenses.forEach { lens ->
                        Text(
                            text = "• ID ${lens.cameraId}: ${lens.roleLabel} | f/${lens.maxAperture ?: 1.8f} | ISO ${lens.isoRange.first}–${lens.isoRange.last} | RAW: ${if (lens.supportsRaw) "支持" else "JPEG"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }

        // 2. Camera & HUD Controls
        item {
            SettingsGroupCard("📷 相机与取景辅助") {
                SettingSwitchRow("电子水平仪（0.0° 翡翠绿对齐提示）", settings.showLeveler) { v ->
                    scope.launch { container.preferences.update { it.copy(showLeveler = v) } }
                }
                SettingSwitchRow("实时 RGB / 亮度直方图浮层", settings.showHistogram) { v ->
                    scope.launch { container.preferences.update { it.copy(showHistogram = v) } }
                }
                SettingSwitchRow("高光过曝斑马纹预警（>96%）", settings.showZebraStripes) { v ->
                    scope.launch { container.preferences.update { it.copy(showZebraStripes = v) } }
                }
                SettingSwitchRow("取景器曝光与焦段参数 HUD", settings.showParameterHud) { v ->
                    scope.launch { container.preferences.update { it.copy(showParameterHud = v) } }
                }
                SettingSwitchRow("触摸取景框即刻拍照", settings.touchToCapture) { v ->
                    scope.launch { container.preferences.update { it.copy(touchToCapture = v) } }
                }
                SettingSwitchRow("前置自拍水平镜像保持", settings.mirrorFrontCamera) { v ->
                    scope.launch { container.preferences.update { it.copy(mirrorFrontCamera = v) } }
                }

                Spacer(Modifier.height(4.dp))
                Text("音量实体键功能映射", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VolumeKeyAction.entries) { act ->
                        TopHudChip(act.label, settings.volumeKeyAction == act) {
                            scope.launch { container.preferences.update { it.copy(volumeKeyAction = act) } }
                        }
                    }
                }
            }
        }

        // 3. Filter, Video LUT Performance & Floating Window
        item {
            SettingsGroupCard("🎞 滤镜引擎与大师悬浮窗") {
                Text(
                    text = "默认滤镜强度：${(settings.defaultFilterStrength * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrassGold,
                )
                Slider(
                    value = settings.defaultFilterStrength,
                    onValueChange = { s ->
                        scope.launch { container.preferences.update { it.copy(defaultFilterStrength = s) } }
                    },
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                )

                Text("录像实时 LUT 功耗与温控策略（防发热优化）", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VideoLutMode.entries) { mode ->
                        TopHudChip(mode.label, settings.videoLutMode == mode) {
                            scope.launch { container.preferences.update { it.copy(videoLutMode = mode) } }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "系统相机悬浮窗形态（不透明度 ${(settings.floatingHudOpacity * 100).roundToInt()}%）",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrassGold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FloatingHudStyle.entries) { st ->
                        TopHudChip(st.label, settings.floatingHudStyle == st) {
                            scope.launch { container.preferences.update { it.copy(floatingHudStyle = st) } }
                        }
                    }
                }
                Slider(
                    value = settings.floatingHudOpacity,
                    onValueChange = { op ->
                        scope.launch { container.preferences.update { it.copy(floatingHudOpacity = op) } }
                    },
                    valueRange = 0.50f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = OpticalEmerald, activeTrackColor = OpticalEmerald),
                )
                OutlinedButton(
                    onClick = { context.startActivity(OppoDeviceInspector.buildAppDetailsIntent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开应用权限详情页（ColorOS 解除受限设置入口）", color = BrassGold)
                }
            }
        }

        // 4. Storage Naming & Privacy Sandbox
        item {
            SettingsGroupCard("🔒 存储命名与本地隐私保护") {
                OutlinedTextField(
                    value = settings.fileNameTemplate,
                    onValueChange = { tpl ->
                        scope.launch { container.preferences.update { it.copy(fileNameTemplate = tpl) } }
                    },
                    label = { Text("文件命名模板（支持 {yyyyMMdd} {HHmmss} {MODE} {FILTER}）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "实时命名预览：${MediaStoreSaver.formatFileName(settings.fileNameTemplate, "PRO", "青绿胶片")}.jpg",
                    style = MaterialTheme.typography.labelSmall,
                    color = OpticalEmerald,
                )
                SettingSwitchRow("同时保存 DNG RAW（设备支持时）", settings.enableRawWhenSupported) { v ->
                    scope.launch { container.preferences.update { it.copy(enableRawWhenSupported = v) } }
                }
                SettingSwitchRow("照片 EXIF 写入地理位置（默认关闭保护隐私）", settings.saveGpsLocation) { v ->
                    scope.launch { container.preferences.update { it.copy(saveGpsLocation = v) } }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            File(context.cacheDir, "exports").deleteRecursively()
                            SafeLogger.clear()
                            Toast.makeText(context, "已清理临时导出缓存与诊断日志", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("清理临时缓存", color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                container.filterRepository.clearAllUserImportedFilters()
                                Toast.makeText(context, "已移除全部外部导入滤镜，保留内置预设", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重置导入滤镜", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InstrumentDeck, RoundedCornerShape(16.dp))
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = BrassGold)
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
