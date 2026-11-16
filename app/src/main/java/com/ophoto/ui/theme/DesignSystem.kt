package com.ophoto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============================================================================
   Ophoto Design System · 2026-11 「Clarity / Deference / Depth」
   ----------------------------------------------------------------------------
   · Clarity  —— 只有三级文字、单强调色、留白切分，不靠线框堆叠信息
   · Deference —— 材质浮在内容之上，控件退后；工具条可自动隐去
   · Depth   —— 统一的 press 缩放 / 抬起阴影 / 材质高光边，交互有物理回馈
   ============================================================================ */

/** 标准圆角：连续圆角感（外 16 / 内 12 / 胶囊 999）。 */
object OphotoShape {
    val Card = RoundedCornerShape(18.dp)
    val Panel = RoundedCornerShape(22.dp)
    val Inner = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(999.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/**
 * 按下反馈：极轻的 0.97 缩放 + 透明度，模拟 iOS 的响应式「贴手感」。
 * 用于所有自定义 clickable 组件。
 */
@Composable
fun Modifier.pressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (enabled && pressed) pressedScale else 1f
    return this.scale(scale)
}

/** 轻材质：贴近内容的浮层（按钮组、小卡片）。 */
@Composable
fun OphotoMaterial(
    modifier: Modifier = Modifier,
    shape: Shape = OphotoShape.Card,
    color: Color = MaterialThin,
    borderColor: Color = MaterialHairline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(color, shape)
            .border(0.7.dp, borderColor, shape),
        content = content,
    )
}

/** 标准卡片：分组容器，用留白与材质而不是重边框来分组。 */
@Composable
fun OphotoCard(
    modifier: Modifier = Modifier,
    borderDp: Dp = 0.7.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(OphotoShape.Card)
            .background(MaterialThin, OphotoShape.Card)
            .border(borderDp, MaterialHairline, OphotoShape.Card),
    ) {
        content()
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
                Brush.verticalGradient(listOf(Color(0xFF17191F), Color(0xFF0E0F13))),
                shape,
            )
            .border(0.7.dp, MaterialHairline, shape)
            .padding(16.dp),
        content = content,
    )
}

/**
 * 页面标题：以大标题 + 一行副文案承担全部信息层级。
 * 取代此前的「eyebrow + 渐变卡 + 指标格」装饰性页头（Deference：让内容先说）。
 */
@Composable
fun OphotoPageHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 2.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 兼容旧调用的页头（内部已简化为纯排版，不再有渐变/装饰）。 */
@Composable
fun PremiumHeroHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    stats: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = BrassGold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = BrassGoldDim,
                letterSpacing = 1.2.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                stats.forEach { (k, v) ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(v, style = MaterialTheme.typography.titleSmall, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(4.dp))
                        Text(k, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }
    }
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
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
        trailing?.invoke()
    }
}

/**
 * 药丸芯片：控制态唯一形态。
 * selected = 实心金底黑字；unselected = 半透明材质。
 */
@Composable
fun OphotoChip(
    label: String,
    selected: Boolean = false,
    accent: Color = BrassGold,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .scale(if (pressed && onClick != null) 0.96f else 1f)
            .clip(OphotoShape.Chip)
            .background(if (selected) accent else ControlPillBg.copy(alpha = 0.72f), OphotoShape.Chip)
            .border(
                0.7.dp,
                if (selected) Color.Transparent else MaterialHairline,
                OphotoShape.Chip,
            )
            .clickable(
                enabled = onClick != null,
                interactionSource = interaction,
                indication = null,
                onClick = onClick ?: {},
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Icon(it, null, tint = if (selected) TextOnAccent else TextSecondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) TextOnAccent else TextSecondary,
            maxLines = 1,
        )
    }
}

/** 主要按钮：全宽金底，仅用于页面唯一的主动作。 */
@Composable
fun OphotoPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(if (pressed) 0.98f else 1f)
            .clip(OphotoShape.Inner)
            .background(if (enabled) BrassGold else ControlPillBg)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (enabled) TextOnAccent else TextMuted,
        ) { content() }
    }
}

@Composable
fun OphotoBrassButton(
    onClick: () -> Unit,
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .height(42.dp)
            .scale(if (pressed) 0.97f else 1f)
            .clip(OphotoShape.Inner)
            .background(if (enabled) BrassGold else ControlPillBg)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, null, tint = TextOnAccent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = TextOnAccent)
    }
}

/** 次要按钮：材质底 + 细边，不抢主动作。 */
@Composable
fun OphotoOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TextPrimary,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .height(42.dp)
            .scale(if (pressed) 0.97f else 1f)
            .clip(OphotoShape.Inner)
            .background(ControlPillBg.copy(alpha = 0.5f))
            .border(0.7.dp, MaterialHairline, OphotoShape.Inner)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides accent,
        ) { content() }
    }
}

@Composable
fun OphotoIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    tint: Color = TextPrimary,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(size)
            .scale(if (pressed) 0.92f else 1f)
            .clip(CircleShape)
            .background(MaterialThin)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun OphotoSection(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        subtitle?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

/** 空状态：图标 + 一句话 + 唯一行动。 */
@Composable
fun EmptyState(
    title: String,
    desc: String? = null,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(ControlPillBg.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Info, null, tint = TextMuted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        desc?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (onAction != null && actionLabel != null) {
            Spacer(Modifier.height(18.dp))
            OphotoBrassButton(onClick = onAction, text = actionLabel)
        }
    }
}

@Composable
fun SkeletonRow(height: Dp = 16.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(6.dp))
            .background(ControlPillBg.copy(alpha = 0.55f)),
    )
}

@Composable
fun SkeletonBlock(size: Dp = 80.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp))
            .background(ControlPillBg.copy(alpha = 0.55f)),
    )
}

@Composable
fun GlassSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.clip(OphotoShape.Inner).background(GlassBlur)
            .border(0.7.dp, GlassBorder, OphotoShape.Inner),
    ) { content() }
}

@Composable
fun OphotoMetricCell(title: String, value: String, valueColor: Color = TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = ReadingStyle, color = valueColor)
    }
}

@Composable
fun OphotoDivider(modifier: Modifier = Modifier, thickness: Dp = 0.7.dp, color: Color = MaterialHairline) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
fun OinfoChip(
    label: String,
    icon: ImageVector = Icons.Rounded.Info,
    tint: Color = TextMuted,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clip(OphotoShape.Inner)
            .background(ControlPillBg.copy(alpha = 0.4f)).padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** 统计块（设置/体检页）：数字在上，说明在下，弱化装饰。 */
@Composable
fun OphotoStatCell(label: String, value: String, accent: Color = TextPrimary, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/** 全屏空白的加载遮罩（Deference：只在必要时出现）。 */
@Composable
fun OphotoLoadingVeil(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xB308090B)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(
                color = BrassGold, strokeWidth = 2.dp, modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}
