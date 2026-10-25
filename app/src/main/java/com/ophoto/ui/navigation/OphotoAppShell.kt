package com.ophoto.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ophoto.OphotoContainer
import com.ophoto.ui.assistant.AssistantScreen
import com.ophoto.ui.camera.CameraScreen
import com.ophoto.ui.editor.GalleryEditorScreen
import com.ophoto.ui.filter.FilterLibraryScreen
import com.ophoto.ui.settings.SettingsScreen
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary

enum class TopDestination(
    val label: String,
    val icon: ImageVector,
) {
    CAMERA("专业相机", Icons.Rounded.Camera),
    FILTERS("大师调色", Icons.Rounded.Palette),
    ASSISTANT("现场助手", Icons.Rounded.Explore),
    EDITOR("暗房编辑", Icons.Rounded.AutoFixHigh),
    SETTINGS("系统设置", Icons.Rounded.Settings),
}

@Composable
fun OphotoAppShell(
    container: OphotoContainer,
    isExpandedDualPane: Boolean,
    modifier: Modifier = Modifier,
) {
    var activeTab by rememberSaveable { mutableStateOf(TopDestination.CAMERA) }

    if (isExpandedDualPane) {
        // Foldable / Tablet 双栏自适应（专业工作台）
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .systemBarsPadding(),
        ) {
            PremiumNavigationRail(
                activeTab = activeTab,
                onSelect = { activeTab = it }
            )
            DestinationBody(
                activeTab = activeTab,
                container = container,
                isExpandedDualPane = true,
                onNavigateTab = { activeTab = it },
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .systemBarsPadding(),
            containerColor = ObsidianBg,
            bottomBar = {
                PremiumBottomBar(
                    activeTab = activeTab,
                    onSelect = { activeTab = it }
                )
            },
        ) { innerPadding ->
            DestinationBody(
                activeTab = activeTab,
                container = container,
                isExpandedDualPane = false,
                onNavigateTab = { activeTab = it },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun PremiumNavigationRail(
    activeTab: TopDestination,
    onSelect: (TopDestination) -> Unit
) {
    Surface(
        color = InstrumentDeck,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .background(InstrumentDeck)
            .border(0.8.dp, HairlineBorder, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Brand header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF2D384E), Color(0xFF0D1017))
                            )
                        )
                        .border(1.8.dp, BrassGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OP", style = MaterialTheme.typography.titleSmall, color = BrassGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF6A2B))
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OPHOTO", style = MaterialTheme.typography.labelMedium, color = BrassGold, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 11.sp)
                    Text("OPTICAL STUDIO", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp, letterSpacing = 0.6.sp)
                }
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(1.dp)
                        .background(HairlineBorder, RoundedCornerShape(1.dp))
                )
            }

            // Nav items
            TopDestination.entries.forEach { dest ->
                val isActive = dest == activeTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) BrassGold else Color.Transparent)
                        .border(1.dp, if (isActive) BrassGold else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    NavigationRailItem(
                        selected = isActive,
                        onClick = { onSelect(dest) },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                dest.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ObsidianBg,
                            selectedTextColor = ObsidianBg,
                            indicatorColor = BrassGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                        ),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Version badge
            Surface(color = ControlPillBg, shape = RoundedCornerShape(8.dp)) {
                Text("v1.0.0", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun PremiumBottomBar(
    activeTab: TopDestination,
    onSelect: (TopDestination) -> Unit
) {
    Surface(
        color = InstrumentDeck,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, HairlineBorder, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
    ) {
        NavigationBar(
            containerColor = InstrumentDeck,
            contentColor = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
        ) {
            TopDestination.entries.forEach { dest ->
                val isActive = dest == activeTab
                NavigationBarItem(
                    selected = isActive,
                    onClick = { onSelect(dest) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) BrassGold.copy(alpha = 0.18f) else Color.Transparent)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                modifier = Modifier.size(if (isActive) 22.dp else 20.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            dest.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.5.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrassGold,
                        selectedTextColor = BrassGold,
                        indicatorColor = BrassGold.copy(alpha = 0.22f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DestinationBody(
    activeTab: TopDestination,
    container: OphotoContainer,
    isExpandedDualPane: Boolean,
    onNavigateTab: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (activeTab) {
        TopDestination.CAMERA -> CameraScreen(
            container = container,
            onJumpToEditor = { onNavigateTab(TopDestination.EDITOR) },
            modifier = modifier,
        )
        TopDestination.FILTERS -> FilterLibraryScreen(
            container = container,
            onApplyAndOpenCamera = { presetId ->
                container.applyFieldCardToCamera(
                    com.ophoto.FieldToCameraSetupCommand(
                        presetId = presetId,
                        compositionGuide = com.ophoto.data.local.CompositionGuideType.RULE_OF_THIRDS,
                        evOffset = 0f,
                        kelvin = 5500,
                        focalMmHint = 24,
                        tipBanner = "已切换滤镜预设",
                    ),
                )
                onNavigateTab(TopDestination.CAMERA)
            },
            modifier = modifier,
        )
        TopDestination.ASSISTANT -> AssistantScreen(
            container = container,
            isExpandedDualPane = isExpandedDualPane,
            onApplyToCamera = {
                onNavigateTab(TopDestination.CAMERA)
            },
            modifier = modifier,
        )
        TopDestination.EDITOR -> GalleryEditorScreen(
            container = container,
            modifier = modifier,
        )
        TopDestination.SETTINGS -> SettingsScreen(
            container = container,
            modifier = modifier,
        )
    }
}
