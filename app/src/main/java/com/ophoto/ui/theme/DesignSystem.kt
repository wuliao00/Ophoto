package com.ophoto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ========================================================================
   Ophoto Design System — 基础视觉组件
   全局复用，保持相机取景、滤镜库、暗房、助手、设置页的视觉一致性
   ======================================================================== */

@Composable
fun OphotoCard(
    modifier: Modifier = Modifier,
    borderDp: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(InstrumentDeck, shape)
            .border(borderDp, HairlineBorder, shape),
    ) {
        content
    }
}

@Composable
fun OphotoGlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF141926), Color(0xFF0F121A))),
                shape
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun OphotoSectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accent: Color = BrassGold,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon?.let {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.18f)).border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(it, null, tint = accent, modifier = Modifier.size(14.dp)) }
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(accent.copy(0.45f), Color.Transparent))))
    }
}

/* 药丸芯片（统一替代分散的 TopHudChip 调用） */
@Composable
fun OphotoChip(
    label: String,
    selected: Boolean = false,
    accent: Color = BrassGold,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) accent.copy(alpha = 0.20f) else ControlPillBg
    val contentColor = if (selected) TextOnAccent else TextPrimary
    val border = if (selected) 1.dp else 0.dp
    val borderColor = if (selected) accent else Color.Transparent

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor, RoundedCornerShape(10.dp))
            .border(border, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

/* 主按钮：黄铜金填充，黑曜文字 */
@Composable
fun OphotoPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (enabled) BrassGold else HairlineBorder,
            contentColor = if (enabled) TextOnAccent else TextMuted,
        ),
        content = { content() },
    )
}

@Composable
fun OphotoBrassButton(
    onClick: () -> Unit,
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp).clip(RoundedCornerShape(11.dp)),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = BrassGold, contentColor = ObsidianBg
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        shape = RoundedCornerShape(11.dp)
    ) {
        icon?.let { Icon(it, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/* 描边按钮 */
@Composable
fun OphotoOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = BrassGold,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = accent,
        ),
        shape = RoundedCornerShape(12.dp),
        content = { content() },
    )
}

/* 图标按钮（圆润控制盘） */
@Composable
fun OphotoIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    tint: Color = TextPrimary,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .background(ControlPillBg, CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/* 分节标题 */
@Composable
fun OphotoSection(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = BrassGold,
        )
        subtitle?.let { sub ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

/* 空状态页 */
@Composable
fun EmptyState(
    icon: ImageVector = Icons.Default.Info,
    title: String = "暂无内容",
    desc: String? = null,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF1E2430), Color(0xFF0D1017))), CircleShape)
                .border(1.dp, HairlineBorder, CircleShape)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrassGold,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        desc?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        if (onAction != null && actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            val action = onAction
            val label = actionLabel
            OphotoPrimaryButton(onClick = action) {
                Text(label)
            }
        }
    }
}

/* 骨架屏行（加载占位） */
@Composable
fun SkeletonRow(
    height: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(ControlPillBg),
    )
}

/* 骨架屏块（方块占位，如预览缩略图） */
@Composable
fun SkeletonBlock(
    size: Dp = 80.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(ControlPillBg),
    )
}

/* 毛玻璃浮层面板（HUD 覆盖层） */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(GlassBlur, shape)
            .border(0.8.dp, GlassBorder, shape),
        content = { content() },
    )
}

/* HUD 计量芯片：标题 + 数值（用于滤镜参数/曝光读数） */
@Composable
fun OphotoMetricCell(
    title: String,
    value: String,
    valueColor: Color = TextPrimary,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = valueColor)
    }
}

/* 横向分割线 */
@Composable
fun OphotoDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.8.dp,
    color: Color = HairlineBorder,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(thickness).background(color),
    )
}

/* 信息标签（带图标的轻量提示） */
@Composable
fun OinfoChip(
    label: String,
    icon: ImageVector = Icons.Default.Info,
    tint: Color = CyanTelemetry,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ControlPillBg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
fun PremiumHeroHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    stats: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2232), Color(0xFF0F121A))), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(0.07f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(BrassGold.copy(0.18f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = BrassGold, modifier = Modifier.size(14.dp))
                    }
                    Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = BrassGold, letterSpacing = 1.2.sp)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (stats.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        stats.forEach { (k, v) ->
                            Column {
                                Text(k, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                                Text(v, style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
