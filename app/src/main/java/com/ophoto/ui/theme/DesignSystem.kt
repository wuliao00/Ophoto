package com.ophoto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ========================================================================
   Ophoto Optical Design System — 4-Tier Precision Instrument UI
   ======================================================================== */

/**
 * Tier-2 Brushed Anodized Card with subtle vertical gradient, outer margin support via [modifier],
 * and guaranteed internal [contentPadding] so content always breathes cleanly.
 */
@Composable
fun OphotoCard(
    modifier: Modifier = Modifier,
    borderDp: Dp = 1.dp,
    borderColor: Color = HairlineBorder,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(listOf(DeckGradientTop, DeckGradientBottom)),
                shape = shape,
            )
            .border(borderDp, borderColor, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * High-contrast Optical Pill Chip:
 * - [solidWhenSelected] = true -> Solid accent fill + dark crisp text
 * - [solidWhenSelected] = false -> Tinted translucent fill + bright accent text + hairline border
 */
@Composable
fun OphotoChip(
    label: String,
    selected: Boolean = false,
    accent: Color = BrassGold,
    solidWhenSelected: Boolean = false,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val containerColor = when {
        selected && solidWhenSelected -> accent
        selected -> accent.copy(alpha = 0.18f)
        else -> ControlPillBg
    }
    val contentColor = when {
        selected && solidWhenSelected -> TextOnAccent
        selected -> accent
        else -> TextSecondary
    }
    val borderWidth = if (selected && !solidWhenSelected) 1.dp else 0.8.dp
    val borderColor = when {
        selected && solidWhenSelected -> accent
        selected -> accent.copy(alpha = 0.65f)
        else -> BorderSubtle
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .border(borderWidth, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Recessed Segmented Control Track (Tier-1 trough + Tier-3 elevated active segment).
 * Gives clear primary section hierarchy across Filter, Assistant, and Darkroom workspaces.
 */
@Composable
fun <T> OphotoSegmentedBar(
    items: List<T>,
    selectedItem: T,
    labelSelector: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackShape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(SurfaceRecessed, trackShape)
            .border(1.dp, HairlineBorder, trackShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = item == selectedItem
            val segShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(segShape)
                    .background(if (active) SurfaceRaised else Color.Transparent, segShape)
                    .then(
                        if (active) {
                            Modifier.border(0.8.dp, BrassGold.copy(alpha = 0.55f), segShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 4.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelSelector(item),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) BrassGold else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Studio Page Header with optical eyebrow tag, bold title, optional subtitle, and trailing action.
 */
@Composable
fun OphotoSection(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = BrassGold,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun OphotoPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = BrassGold,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) accent else HairlineBorder,
            contentColor = if (enabled) TextOnAccent else TextMuted,
        ),
        content = content,
    )
}

@Composable
fun OphotoOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = BrassGold,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceRecessed,
            contentColor = accent,
        ),
        shape = RoundedCornerShape(12.dp),
        content = content,
    )
}

@Composable
fun OphotoIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    tint: Color = TextPrimary,
    active: Boolean = false,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) BrassGold.copy(alpha = 0.20f) else ControlPillBg, CircleShape)
            .border(
                width = 0.8.dp,
                color = if (active) BrassGold.copy(alpha = 0.7f) else BorderSubtle,
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) BrassGold else tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

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
                .size(76.dp)
                .clip(CircleShape)
                .background(SurfaceRaised, CircleShape)
                .border(1.dp, HairlineBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrassGold,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        if (!desc.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (onAction != null && actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            OphotoPrimaryButton(
                onClick = onAction,
                modifier = Modifier.width(160.dp),
            ) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(GlassScrim, shape)
            .border(0.8.dp, BorderHighlight, shape),
        content = content,
    )
}

@Composable
fun OphotoMetricCell(
    title: String,
    value: String,
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(SurfaceRecessed, RoundedCornerShape(9.dp))
            .border(0.6.dp, BorderSubtle, RoundedCornerShape(9.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun OphotoDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.8.dp,
    color: Color = BorderSubtle,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}

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
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRecessed, RoundedCornerShape(10.dp))
            .border(0.8.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}
