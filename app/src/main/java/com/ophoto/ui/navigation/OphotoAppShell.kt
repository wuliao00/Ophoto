package com.ophoto.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ophoto.OphotoContainer
import com.ophoto.ui.assistant.AssistantScreen
import com.ophoto.ui.camera.CameraScreen
import com.ophoto.ui.editor.GalleryEditorScreen
import com.ophoto.ui.filter.FilterLibraryScreen
import com.ophoto.ui.settings.SettingsScreen
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
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
        // Foldable / Tablet Inner Screen Layout (NavigationRail + Dual-Pane Workspace)
        Row(
            modifier = modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            NavigationRail(
                containerColor = InstrumentDeck,
                contentColor = BrassGold,
            ) {
                TopDestination.entries.forEach { dest ->
                    NavigationRailItem(
                        selected = activeTab == dest,
                        onClick = { activeTab = dest },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
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
                .systemBarsPadding(),
            containerColor = ObsidianBg,
            bottomBar = {
                NavigationBar(
                    containerColor = InstrumentDeck,
                ) {
                    TopDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = activeTab == dest,
                            onClick = { activeTab = dest },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ObsidianBg,
                                selectedTextColor = BrassGold,
                                indicatorColor = BrassGold,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                        )
                    }
                }
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
