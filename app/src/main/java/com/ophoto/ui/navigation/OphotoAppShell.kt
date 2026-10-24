package com.ophoto.ui.navigation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ophoto.OphotoContainer
import com.ophoto.ui.assistant.AssistantScreen
import com.ophoto.ui.camera.CameraScreen
import com.ophoto.ui.editor.GalleryEditorScreen
import com.ophoto.ui.filter.FilterLibraryScreen
import com.ophoto.ui.settings.SettingsScreen
import com.ophoto.ui.theme.BorderHighlight
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.SurfaceRecessed
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextSecondary

enum class TopDestination(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
) {
    CAMERA("专业相机", "相机", Icons.Rounded.Camera),
    FILTERS("大师调色", "调色", Icons.Rounded.Palette),
    ASSISTANT("现场助手", "助手", Icons.Rounded.Explore),
    EDITOR("暗房编辑", "暗房", Icons.Rounded.AutoFixHigh),
    SETTINGS("系统设置", "设置", Icons.Rounded.Settings),
}

@Composable
fun OphotoAppShell(
    container: OphotoContainer,
    isExpandedDualPane: Boolean,
    modifier: Modifier = Modifier,
) {
    var activeTab by rememberSaveable { mutableStateOf(TopDestination.CAMERA) }

    if (isExpandedDualPane) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .systemBarsPadding(),
        ) {
            NavigationRail(
                containerColor = SurfaceRecessed,
                contentColor = BrassGold,
            ) {
                TopDestination.entries.forEach { dest ->
                    NavigationRailItem(
                        selected = activeTab == dest,
                        onClick = { activeTab = dest },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.shortLabel) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ObsidianBg,
                            selectedTextColor = BrassGold,
                            indicatorColor = BrassGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                        ),
                    )
                }
            }
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
                PrecisionStudioBottomDock(
                    activeTab = activeTab,
                    onSelectTab = { activeTab = it },
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

/**
 * Slim 56dp Precision Optical Studio Dock replacing the bulky 80dp default NavigationBar,
 * giving the Camera viewfinder and Darkroom workspace maximum vertical clarity.
 */
@Composable
private fun PrecisionStudioBottomDock(
    activeTab: TopDestination,
    onSelectTab: (TopDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceRecessed),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.7.dp)
                .background(BorderHighlight),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopDestination.entries.forEach { dest ->
                val selected = dest == activeTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectTab(dest) }
                        .padding(vertical = 5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) BrassGold.copy(alpha = 0.16f) else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            tint = if (selected) BrassGold else TextMuted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(BrassGold),
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            text = dest.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) BrassGold else TextMuted,
                        )
                    }
                }
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
                        tipBanner = "已加载调色预设",
                        applyManualWb = false,
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
