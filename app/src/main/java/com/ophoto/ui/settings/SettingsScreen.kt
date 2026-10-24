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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ophoto.OphotoContainer
import com.ophoto.core.permission.CameraPermissionHelper
import com.ophoto.data.local.UserSettings
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.service.MasterOverlayService
import com.ophoto.ui.theme.BorderSubtle
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.CyanTelemetry
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OphotoCard
import com.ophoto.ui.theme.OphotoDivider
import com.ophoto.ui.theme.OphotoMetricCell
import com.ophoto.ui.theme.OphotoSection
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.SurfaceRecessed
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextOnAccent
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: OphotoContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.preferences.settingsFlow.collectAsState(initial = UserSettings())
    val hw = container.hardwareProfile
    val overlayGranted = CameraPermissionHelper.canDrawFloatingOverlay(context)
    val overlayRunning by MasterOverlayService.isRunningFlow.collectAsState()

    var templateDraft by remember(settings.fileNameTemplate) { mutableStateOf(settings.fileNameTemplate) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        OphotoSection(
            eyebrow = "OPTICAL HARDWARE & STUDIO PREFS",
            title = "设备诊断与偏好",
            subtitle = "Camera2 传感器能力枚举 · ColorOS 悬浮窗 · 纯离线隐私沙盒",
        )

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.weight(1f),
        ) {
            // 1. Hardware Telemetry Card
            item {
                OphotoCard(
                    borderColor = BrassGold.copy(alpha = 0.40f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "影像硬件能力报告",
                                style = MaterialTheme.typography.labelSmall,
                                color = OpticalEmerald,
                            )
                            Text(
                                text = hw.displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                        }
                        Text(
                            text = hw.camera2HardwareLevel,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrassGold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceRecessed)
                                .border(0.7.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OphotoMetricCell(
                            title = "DNG RAW",
                            value = if (hw.supportsRawCapture) "支持" else "直出",
                            valueColor = if (hw.supportsRawCapture) OpticalEmerald else TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        OphotoMetricCell(
                            title = "最高帧率",
                            value = "${hw.maxSupportedFps}fps",
                            valueColor = CyanTelemetry,
                            modifier = Modifier.weight(1f),
                        )
                        OphotoMetricCell(
                            title = "逻辑多摄",
                            value = if (hw.supportsLogicalMultiCamera) "已就绪" else "单摄",
                            valueColor = BrassGold,
                            modifier = Modifier.weight(1f),
                        )
                        OphotoMetricCell(
                            title = "折叠适配",
                            value = if (hw.isFoldableCandidate) "双栏" else "直板",
                            valueColor = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hw.lenses) { lens ->
                            Text(
                                text = "${lens.label} · ${lens.equivalentFocalMm}mm f/${lens.maxAperture}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanTelemetry,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(SurfaceRecessed)
                                    .border(0.6.dp, BorderSubtle, RoundedCornerShape(7.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Text(
                        text = hw.colorOsOverlayNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            // 2. ColorOS Floating Master Assistant Card
            item {
                OphotoCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ColorOS 原生相机大师模式悬浮窗",
                            style = MaterialTheme.typography.titleMedium,
                            color = BrassGold,
                        )
                        Text(
                            text = if (overlayGranted) "✓ 已授权" else "待授权",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overlayGranted) OpticalEmerald else TextMuted,
                        )
                    }

                    Text(
                        text = "在 OPPO 系统原生相机「大师模式」界面悬浮展示实时影调、冷暖、青品参数刻度与水平仪参考。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!overlayGranted) {
                            Button(
                                onClick = {
                                    context.startActivity(CameraPermissionHelper.createOverlayPermissionIntent(context))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrassGold,
                                    contentColor = TextOnAccent,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("前往开启悬浮窗权限", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (overlayRunning) {
                                        MasterOverlayService.stop(context)
                                    } else {
                                        MasterOverlayService.startWithPreset(
                                            context = context,
                                            presetId = BuiltInPresets.ALL.first().id,
                                            launchSystemCamera = false,
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (overlayRunning) OpticalEmerald else BrassGold,
                                    contentColor = TextOnAccent,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    text = if (overlayRunning) "关闭当前悬浮窗" else "启动大师悬浮窗测试",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // 3. Viewfinder & Capture Preferences Card
            item {
                OphotoCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "取景器与快门操控",
                        style = MaterialTheme.typography.titleMedium,
                        color = BrassGold,
                    )
                    Spacer(Modifier.height(4.dp))
                    SettingSwitchRow(
                        title = "实时 RGB / 明度直方图",
                        subtitle = "取景器左上角实时采样 GPU 帧缓冲亮度分布，防止高光死白与暗部死黑",
                        checked = settings.showHistogram,
                    ) { v ->
                        scope.launch { container.preferences.update { it.copy(showHistogram = v) } }
                    }
                    OphotoDivider()
                    SettingSwitchRow(
                        title = "双翼光学电子水平仪",
                        subtitle = "融合重力传感器倾角，在 ±1° 水平对准时自动点亮光学翡翠绿",
                        checked = settings.showLeveler,
                    ) { v ->
                        scope.launch { container.preferences.update { it.copy(showLeveler = v) } }
                    }
                    OphotoDivider()
                    SettingSwitchRow(
                        title = "物理音量键充当快门",
                        subtitle = "抓拍街景与冬季戴手套时直接按压音量加减键触发快门",
                        checked = settings.volumeKeyAsShutter,
                    ) { v ->
                        scope.launch { container.preferences.update { it.copy(volumeKeyAsShutter = v) } }
                    }
                    OphotoDivider()
                    SettingSwitchRow(
                        title = "保留上一次滤镜与构图状态",
                        subtitle = "重启应用时自动恢复上次使用的构图参考线与比例",
                        checked = settings.rememberLastCameraState,
                    ) { v ->
                        scope.launch { container.preferences.update { it.copy(rememberLastCameraState = v) } }
                    }
                    OphotoDivider()
                    SettingSwitchRow(
                        title = "同时保存无滤镜原片备份",
                        subtitle = "使用胶片预设拍照时额外保存一张纯净无调色底片至相册",
                        checked = settings.keepUnfilteredBackup,
                    ) { v ->
                        scope.launch { container.preferences.update { it.copy(keepUnfilteredBackup = v) } }
                    }
                    OphotoDivider()
                    SettingSwitchRow(
                        title = "导出照片默认剥离 GPS 坐标",
                        subtitle = "社交平台分享时保护拍摄地隐私，保留快门/ISO/焦段光学 EXIF",
                        checked = settings.stripGpsOnShare,
                    ) { v ->
                        scope.launch { container.preferences.update { it.copy(stripGpsOnShare = v) } }
                    }
                }
            }

            // 4. File Naming & Offline Privacy Card
            item {
                OphotoCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "导出文件命名规则与本地沙盒",
                        style = MaterialTheme.typography.titleMedium,
                        color = BrassGold,
                    )
                    Text(
                        text = "可用占位符：{DATE} 日期 · {TIME} 时间 · {MODE} 拍摄模式 · {FILTER} 滤镜名",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    OutlinedTextField(
                        value = templateDraft,
                        onValueChange = { templateDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    container.preferences.update {
                                        it.copy(fileNameTemplate = templateDraft.ifBlank { "OPHOTO_{DATE}_{TIME}_{FILTER}" })
                                    }
                                    Toast.makeText(context, "已保存导出文件命名模板", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = TextOnAccent),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("保存命名模板", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                context.cacheDir.resolve("exports").deleteRecursively()
                                Toast.makeText(context, "已清理临时导出缓存（收藏与滤镜完整保留）", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceRecessed,
                                contentColor = BrassGold,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("清理导出缓存", color = BrassGold)
                        }
                    }
                    OphotoDivider()
                    Text(
                        text = "零联网隐私承诺：Ophoto 未声明 INTERNET 权限，全部 3D LUT 渲染、照片编辑与天文光学计算 100% 在手机本地沙盒离线完成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpticalEmerald,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextOnAccent,
                checkedTrackColor = BrassGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = ControlPillBg,
                uncheckedBorderColor = BorderSubtle,
            ),
        )
    }
}
