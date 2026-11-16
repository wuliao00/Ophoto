package com.ophoto.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ophoto.BuildConfig
import com.ophoto.OphotoContainer
import com.ophoto.ui.assistant.AssistantScreen
import com.ophoto.ui.camera.CameraScreen
import com.ophoto.ui.editor.GalleryEditorScreen
import com.ophoto.ui.filter.FilterLibraryScreen
import com.ophoto.ui.settings.SettingsScreen
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.MaterialHairline
import com.ophoto.ui.theme.MaterialRegular
import com.ophoto.ui.theme.MaterialThick
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OphotoShape
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary

enum class TopDestination(
    val label: String,
    val icon: ImageVector,
) {
    CAMERA("相机", Icons.Rounded.Camera),
    FILTERS("调色", Icons.Rounded.Palette),
    ASSISTANT("现场", Icons.Rounded.Explore),
    EDITOR("暗房", Icons.Rounded.AutoFixHigh),
    SETTINGS("设置", Icons.Rounded.Settings),
}

/**
 * 应用外壳 · Deference
 *
 * - 相机页默认进入沉浸模式：底部栏与所有界面装饰自动隐去，取景画面是全屏主角；
 *   轻触画面（或上滑）才唤回控制条，继续拍摄后再次淡出。
 * - 其他页面保持常驻底部栏，但栏体是「半透明材质 + 细边」而不是实心色块，
 *   以传递「照片/内容是主角，控件浮在上层」的信号。
 * - Depth：切页时内容做极短的上浮淡入（180ms），底部栏有抬起阴影。
 */
@Composable
fun OphotoAppShell(
    container: OphotoContainer,
    isExpandedDualPane: Boolean,
    modifier: Modifier = Modifier,
) {
    var activeTab by rememberSaveable { mutableStateOf(TopDestination.CAMERA) }

    // 相机与暗房都是「内容铺满整屏、控件自己处理安全区」的页面
    val fullBleed = activeTab == TopDestination.CAMERA || activeTab == TopDestination.EDITOR
    val immersiveCamera = activeTab == TopDestination.CAMERA
    // 全局标签栏始终保留（否则用户在沉浸取景里会找不到出口）；
    // 相机自身的读数与工具行由相机页自行淡出。

    if (isExpandedDualPane) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding(),
        ) {
            SideRail(
                activeTab = activeTab,
                onSelect = { activeTab = it },
            )
            Box(Modifier.weight(1f).fillMaxSize()) {
                DestinationBody(
                    activeTab = activeTab,
                    container = container,
                    isExpandedDualPane = true,
                    onNavigateTab = { activeTab = it },
                    onCameraChromeInteraction = {},
                    onCameraChromeIdle = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg),
        ) {
            DestinationBody(
                activeTab = activeTab,
                container = container,
                isExpandedDualPane = false,
                onNavigateTab = { activeTab = it },
                onCameraChromeInteraction = {},
                onCameraChromeIdle = {},
                modifier = Modifier
                    .fillMaxSize()
                    // 相机/暗房：画面延伸到状态栏与导航栏之下（全屏画布）。
                    // 相机控件通过 bottomInset 抬高到标签栏之上；暗房自己留出 66dp。
                    .then(if (fullBleed) Modifier else Modifier.statusBarsPadding())
                    .padding(bottom = if (fullBleed) 0.dp else 74.dp),
                bottomInset = 70.dp,
            )

            BottomChrome(
                activeTab = activeTab,
                onSelect = { activeTab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 底部栏：半透明材质 + 抬起阴影 + 唯一选中态（金）。 */
@Composable
private fun BottomChrome(
    activeTab: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xE608090B)),
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialRegular)
                .border(0.7.dp, MaterialHairline, RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TopDestination.entries.forEach { dest ->
                NavTab(
                    dest = dest,
                    selected = dest == activeTab,
                    onClick = { onSelect(dest) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    dest: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(if (selected) 1.06f else 1f, label = "navIcon")
    Column(
        modifier = modifier
            .scale(if (pressed) 0.94f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = dest.icon,
            contentDescription = dest.label,
            tint = if (selected) BrassGold else TextMuted,
            modifier = Modifier.size(21.dp).scale(iconScale),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = dest.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (selected) BrassGold else TextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 折叠屏 / 平板：窄侧栏，图标 + 标签，同样以材质浮在内容之上。 */
@Composable
private fun SideRail(
    activeTab: TopDestination,
    onSelect: (TopDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .fillMaxSize()
            .background(Color(0xF00B0C0F))
            .border(0.7.dp, MaterialHairline, RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF16181D))
                .border(1.dp, BrassGold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "OP",
                style = MaterialTheme.typography.labelMedium,
                color = BrassGold,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        TopDestination.entries.forEach { dest ->
            val selected = dest == activeTab
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) BrassGold else Color.Transparent)
                    .clickable { onSelect(dest) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    dest.icon,
                    contentDescription = dest.label,
                    tint = if (selected) ObsidianBg else TextSecondary,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    dest.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = if (selected) ObsidianBg else TextMuted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun DestinationBody(
    activeTab: TopDestination,
    container: OphotoContainer,
    isExpandedDualPane: Boolean,
    onNavigateTab: (TopDestination) -> Unit,
    onCameraChromeInteraction: () -> Unit,
    onCameraChromeIdle: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    androidx.compose.animation.AnimatedContent(
        targetState = activeTab,
        transitionSpec = {
            // Depth：新页面「从下方 4% 浮起」并淡入，旧页面快速淡出。
            (
                fadeIn(androidx.compose.animation.core.tween(200)) +
                    androidx.compose.animation.slideInVertically(
                        animationSpec = androidx.compose.animation.core.tween(
                            260,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    ) { it / 24 }
                ) togetherWith fadeOut(androidx.compose.animation.core.tween(120))
        },
        label = "tabBody",
        modifier = modifier,
    ) { tab ->
        when (tab) {
            TopDestination.CAMERA -> CameraScreen(
                container = container,
                onJumpToEditor = { onNavigateTab(TopDestination.EDITOR) },
                onChromeInteraction = onCameraChromeInteraction,
                onChromeIdle = onCameraChromeIdle,
                bottomInset = bottomInset,
                modifier = Modifier.fillMaxSize(),
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
                modifier = Modifier.fillMaxSize(),
            )

            TopDestination.ASSISTANT -> AssistantScreen(
                container = container,
                isExpandedDualPane = isExpandedDualPane,
                onApplyToCamera = { onNavigateTab(TopDestination.CAMERA) },
                modifier = Modifier.fillMaxSize(),
            )

            TopDestination.EDITOR -> GalleryEditorScreen(
                container = container,
                modifier = Modifier.fillMaxSize(),
            )

            TopDestination.SETTINGS -> SettingsScreen(
                container = container,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

