package com.ophoto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Info,
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
                .background(ControlPillBg, CircleShape)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
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
            )
        }
        if (onAction != null && actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            OphotoPrimaryButton(onClick = onAction!!) {
                Text(actionLabel!!)
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
            .background(Color(0xAD0B0D12), shape)
            .border(0.8.dp, Color.White.copy(alpha = 0.10f), shape),
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
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Info,
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
