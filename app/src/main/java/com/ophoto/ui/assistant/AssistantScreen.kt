package com.ophoto.ui.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ophoto.FieldToCameraSetupCommand
import com.ophoto.OphotoContainer
import com.ophoto.core.permission.CameraPermissionHelper
import com.ophoto.data.local.CompositionGuideType
import com.ophoto.data.local.UserSettings
import com.ophoto.data.repository.FieldAssistantCatalog
import com.ophoto.data.repository.LightingSetupGuide
import com.ophoto.data.repository.SceneCategory
import com.ophoto.data.repository.ShotInspirationCard
import com.ophoto.domain.calculator.CityLocationPreset
import com.ophoto.domain.calculator.FieldCalculators
import com.ophoto.domain.calculator.NdFilterSpec
import com.ophoto.domain.calculator.SensorFormatSpec
import com.ophoto.service.MasterOverlayService
import com.ophoto.ui.camera.CompositionOverlayCanvas
import com.ophoto.ui.camera.TopHudChip
import com.ophoto.ui.theme.BorderSubtle
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.CyanTelemetry
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OphotoCard
import com.ophoto.ui.theme.OphotoChip
import com.ophoto.ui.theme.OphotoDivider
import com.ophoto.ui.theme.OphotoMetricCell
import com.ophoto.ui.theme.OphotoSection
import com.ophoto.ui.theme.OphotoSegmentedBar
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.ShutterOrange
import com.ophoto.ui.theme.SurfaceRecessed
import com.ophoto.ui.theme.TextMuted
import com.ophoto.ui.theme.TextOnAccent
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class AssistantSubSection(val label: String) {
    SHOT_CARDS("镜头卡"),
    LIGHTING_STUDIO("布光雷达"),
    CALCULATORS("光学计算"),
    CHECKLIST_GLOSSARY("清单词典"),
}

@Composable
fun AssistantScreen(
    container: OphotoContainer,
    isExpandedDualPane: Boolean,
    onApplyToCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeSection by remember { mutableStateOf(AssistantSubSection.SHOT_CARDS) }
    var selectedScene by remember { mutableStateOf(SceneCategory.ALL) }
    var selectedShotCard by remember { mutableStateOf(FieldAssistantCatalog.SHOT_CARDS.first()) }
    var selectedLighting by remember { mutableStateOf(FieldAssistantCatalog.LIGHTING_GUIDES.first()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        OphotoSection(
            eyebrow = "ON-SITE FIELD HUD",
            title = "摄影现场助手",
            subtitle = "机位构图蓝图 · 极坐标灯位图 · 日出蓝调与景深计算",
        )

        Spacer(Modifier.height(10.dp))

        OphotoSegmentedBar(
            items = AssistantSubSection.entries,
            selectedItem = activeSection,
            labelSelector = { it.label },
            onSelect = { activeSection = it },
        )

        Spacer(Modifier.height(10.dp))

        when (activeSection) {
            AssistantSubSection.SHOT_CARDS -> ShotCardsWorkspace(
                container = container,
                isExpandedDualPane = isExpandedDualPane,
                selectedScene = selectedScene,
                onSelectScene = { selectedScene = it },
                selectedCard = selectedShotCard,
                onSelectCard = { selectedShotCard = it },
                onApplyToCamera = onApplyToCamera,
                modifier = Modifier.weight(1f),
            )

            AssistantSubSection.LIGHTING_STUDIO -> LightingDiagramsWorkspace(
                container = container,
                isExpandedDualPane = isExpandedDualPane,
                selectedGuide = selectedLighting,
                onSelectGuide = { selectedLighting = it },
                onApplyToCamera = onApplyToCamera,
                modifier = Modifier.weight(1f),
            )

            AssistantSubSection.CALCULATORS -> FieldCalculatorsWorkspace(
                container = container,
                modifier = Modifier.weight(1f),
            )

            AssistantSubSection.CHECKLIST_GLOSSARY -> ChecklistAndGlossaryWorkspace(
                container = container,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ShotCardsWorkspace(
    container: OphotoContainer,
    isExpandedDualPane: Boolean,
    selectedScene: SceneCategory,
    onSelectScene: (SceneCategory) -> Unit,
    selectedCard: ShotInspirationCard,
    onSelectCard: (ShotInspirationCard) -> Unit,
    onApplyToCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = remember(selectedScene) {
        if (selectedScene == SceneCategory.ALL) {
            FieldAssistantCatalog.SHOT_CARDS
        } else {
            FieldAssistantCatalog.SHOT_CARDS.filter { it.category == selectedScene }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SceneCategory.entries) { cat ->
                OphotoChip(
                    label = cat.label,
                    selected = selectedScene == cat,
                    onClick = { onSelectScene(cat) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (isExpandedDualPane) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                ) {
                    items(cards, key = { it.id }) { card ->
                        ShotCardCompactItem(
                            card = card,
                            selected = card.id == selectedCard.id,
                            onClick = { onSelectCard(card) },
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                ) {
                    ShotCardDetailPanel(
                        card = selectedCard,
                        container = container,
                        onApplyToCamera = onApplyToCamera,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    ShotCardDetailPanel(
                        card = selectedCard,
                        container = container,
                        onApplyToCamera = onApplyToCamera,
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "场景机位列表",
                            style = MaterialTheme.typography.titleSmall,
                            color = BrassGold,
                        )
                        Text(
                            text = "共 ${cards.size} 张灵感卡",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }
                items(cards, key = { it.id }) { card ->
                    ShotCardCompactItem(
                        card = card,
                        selected = card.id == selectedCard.id,
                        onClick = { onSelectCard(card) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShotCardCompactItem(
    card: ShotInspirationCard,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) BrassGold.copy(alpha = 0.14f) else InstrumentDeck, shape)
            .border(
                1.dp,
                if (selected) BrassGold else HairlineBorder,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) BrassGold else TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${card.category.label} · ${card.distanceHint} · ${card.recommendedExposure}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${card.focalEquivalentMm}mm",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextOnAccent else OpticalEmerald,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) BrassGold else SurfaceRecessed)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ShotCardDetailPanel(
    card: ShotInspirationCard,
    container: OphotoContainer,
    onApplyToCamera: () -> Unit,
) {
    val context = LocalContext.current
    OphotoCard(
        borderColor = BrassGold.copy(alpha = 0.45f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = OpticalEmerald,
                )
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            }
            Text(
                text = "${card.focalEquivalentMm}mm · ${card.recommendedKelvin}K",
                style = MaterialTheme.typography.labelMedium,
                color = CyanTelemetry,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceRecessed)
                    .border(0.7.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Parametric Composition Viewfinder Blueprint Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceRecessed)
                .border(0.9.dp, HairlineBorder, RoundedCornerShape(12.dp)),
        ) {
            CompositionOverlayCanvas(
                guideType = card.compositionGuide,
                showLeveler = false,
                rollDegrees = 0f,
                focusTapNormalized = null,
                showCornerBrackets = true,
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sx = card.subjectNormalizedX * size.width
                val sy = card.subjectNormalizedY * size.height
                val rad = Math.toRadians(card.lightDirectionDeg.toDouble())
                val lx = sx + (cos(rad) * 60.dp.toPx()).toFloat()
                val ly = sy - (sin(rad) * 60.dp.toPx()).toFloat()

                drawLine(
                    color = ShutterOrange,
                    start = Offset(lx, ly),
                    end = Offset(sx, sy),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                )
                drawCircle(ShutterOrange.copy(alpha = 0.28f), radius = 11.dp.toPx(), center = Offset(lx, ly))
                drawCircle(ShutterOrange, radius = 4.5.dp.toPx(), center = Offset(lx, ly))

                drawCircle(OpticalEmerald.copy(alpha = 0.22f), radius = 20.dp.toPx(), center = Offset(sx, sy))
                drawCircle(OpticalEmerald, radius = 6.5.dp.toPx(), center = Offset(sx, sy))
            }
            Text(
                text = "● 绿标：主体落位   ● 橙标：主逆光方向 (${card.cameraHeightHint})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xCC07080B))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Text(
            text = card.effectSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            card.stepByStepTips.forEachIndexed { idx, step ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(BrassGold.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrassGold,
                        )
                    }
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            text = "现场变通锦囊：${card.fieldWorkaround}",
            style = MaterialTheme.typography.bodySmall,
            color = OpticalEmerald,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF10221C))
                .border(0.7.dp, OpticalEmerald.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = {
                    container.applyFieldCardToCamera(
                        FieldToCameraSetupCommand(
                            presetId = card.linkedPresetId,
                            compositionGuide = card.compositionGuide,
                            evOffset = card.recommendedEv,
                            kelvin = card.recommendedKelvin,
                            focalMmHint = card.focalEquivalentMm,
                            tipBanner = "${card.title} (${card.recommendedExposure})",
                            applyManualWb = true,
                        ),
                    )
                    onApplyToCamera()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = TextOnAccent),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("一键装载进相机开拍", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    if (CameraPermissionHelper.canDrawFloatingOverlay(context)) {
                        MasterOverlayService.startWithPreset(context, card.linkedPresetId, launchSystemCamera = true)
                    } else {
                        Toast.makeText(context, "请先在调色库开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfaceRecessed,
                    contentColor = BrassGold,
                ),
                shape = RoundedCornerShape(11.dp),
            ) {
                Text("同步悬浮窗", color = BrassGold)
            }
        }
    }
}

@Composable
private fun LightingDiagramsWorkspace(
    container: OphotoContainer,
    isExpandedDualPane: Boolean,
    selectedGuide: LightingSetupGuide,
    onSelectGuide: (LightingSetupGuide) -> Unit,
    onApplyToCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            if (isExpandedDualPane) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FieldAssistantCatalog.LIGHTING_GUIDES.forEach { guide ->
                        OphotoChip(
                            label = guide.title,
                            selected = guide.id == selectedGuide.id,
                            onClick = { onSelectGuide(guide) },
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FieldAssistantCatalog.LIGHTING_GUIDES, key = { it.id }) { guide ->
                        OphotoChip(
                            label = guide.title,
                            selected = guide.id == selectedGuide.id,
                            onClick = { onSelectGuide(guide) },
                        )
                    }
                }
            }
        }

        item {
            OphotoCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedGuide.tag, style = MaterialTheme.typography.labelSmall, color = OpticalEmerald)
                        Text(selectedGuide.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    }
                    Text(
                        "${selectedGuide.lightRatio} · ${selectedGuide.recommendedFocal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrassGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceRecessed)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // 2D Top-Down Polar Studio Lighting Radar Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(206.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(SurfaceRecessed)
                        .border(0.9.dp, HairlineBorder, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.48f
                        val maxR = minOf(size.width, size.height) * 0.41f

                        for (ring in 1..3) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = maxR * ring / 3f,
                                center = Offset(cx, cy),
                                style = Stroke(1.dp.toPx()),
                            )
                        }
                        // Polar crosshairs
                        drawLine(
                            Color.White.copy(alpha = 0.06f),
                            Offset(cx - maxR, cy),
                            Offset(cx + maxR, cy),
                            1.dp.toPx(),
                        )
                        drawLine(
                            Color.White.copy(alpha = 0.06f),
                            Offset(cx, cy - maxR),
                            Offset(cx, cy + maxR),
                            1.dp.toPx(),
                        )

                        // Camera optical axis at 6 o'clock
                        val camY = cy + maxR * 0.92f
                        drawLine(
                            color = BrassGold.copy(alpha = 0.45f),
                            start = Offset(cx, camY),
                            end = Offset(cx, cy),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        )
                        drawCircle(BrassGold, radius = 6.dp.toPx(), center = Offset(cx, camY))

                        // Light cones & nodes
                        selectedGuide.nodes.forEach { node ->
                            val rad = Math.toRadians(node.angleDegFromCamera.toDouble())
                            val lx = cx + (sin(rad) * maxR * node.radiusRatio).toFloat()
                            val ly = cy + (cos(rad) * maxR * node.radiusRatio).toFloat()
                            val col = Color(node.role.colorHex)

                            val perpX = -cos(rad).toFloat() * 14.dp.toPx()
                            val perpY = sin(rad).toFloat() * 14.dp.toPx()
                            val cone = Path().apply {
                                moveTo(lx, ly)
                                lineTo(cx + perpX, cy + perpY)
                                lineTo(cx - perpX, cy - perpY)
                                close()
                            }
                            drawPath(cone, col.copy(alpha = 0.14f))
                            drawLine(col.copy(alpha = 0.65f), Offset(lx, ly), Offset(cx, cy), strokeWidth = 2.dp.toPx())
                            drawCircle(col, radius = 7.dp.toPx(), center = Offset(lx, ly))
                        }

                        // Subject at center
                        drawCircle(Color.White.copy(alpha = 0.22f), radius = 14.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy))
                    }
                    Text(
                        text = "俯视灯位雷达（底部黄铜点 = 相机机位 · 中心白点 = 主体）",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(6.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedGuide.nodes.forEach { node ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(SurfaceRecessed)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(node.role.colorHex)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${node.role.label} (${node.angleDegFromCamera.roundToInt()}°) · ${node.powerNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                            )
                        }
                    }
                }

                selectedGuide.keyPoints.forEach { pt ->
                    Text("• $pt", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                Button(
                    onClick = {
                        container.applyFieldCardToCamera(
                            FieldToCameraSetupCommand(
                                presetId = selectedGuide.linkedPresetId,
                                compositionGuide = CompositionGuideType.RULE_OF_THIRDS,
                                evOffset = -0.2f,
                                kelvin = 5400,
                                focalMmHint = 50,
                                tipBanner = "${selectedGuide.title} · ${selectedGuide.lightRatio}",
                                applyManualWb = false,
                            ),
                        )
                        onApplyToCamera()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = TextOnAccent),
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("匹配该布光预设进入拍摄", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FieldCalculatorsWorkspace(
    container: OphotoContainer,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by container.preferences.settingsFlow.collectAsState(initial = UserSettings())

    val activeCity = remember(settings.selectedCityId) {
        CityLocationPreset.PRESETS.firstOrNull { it.id == settings.selectedCityId }
            ?: CityLocationPreset.PRESETS.first()
    }
    var dayOffset by remember { mutableIntStateOf(0) }
    val targetDate = remember(dayOffset) { LocalDate.now().plusDays(dayOffset.toLong()) }

    val sunSchedule = remember(activeCity, targetDate) {
        FieldCalculators.calculateSunSchedule(
            latitude = activeCity.latitude,
            longitude = activeCity.longitude,
            date = targetDate,
            zoneOffset = ZoneOffset.ofHours(activeCity.utcOffsetHours),
        )
    }
    val moonReport = remember(targetDate) { FieldCalculators.calculateMoonReport(targetDate) }

    var sensorSpec by remember { mutableStateOf(SensorFormatSpec.PHONE_1_14) }
    var focalMm by remember { mutableFloatStateOf(23f) }
    var apertureF by remember { mutableFloatStateOf(1.8f) }
    var subjectDistM by remember { mutableFloatStateOf(2.2f) }
    val dofResult = remember(sensorSpec, focalMm, apertureF, subjectDistM) {
        FieldCalculators.calculateDof(focalMm.toDouble(), apertureF.toDouble(), subjectDistM.toDouble(), sensorSpec)
    }
    val eqResult = remember(sensorSpec, focalMm) {
        FieldCalculators.calculateEquivalentLens((focalMm / sensorSpec.cropFactor), sensorSpec)
    }

    var baseShutterIdx by remember { mutableIntStateOf(4) }
    val baseShutterChoices = listOf(1.0 / 1000, 1.0 / 500, 1.0 / 250, 1.0 / 125, 1.0 / 60, 1.0 / 15, 0.25, 1.0)
    var selectedNd by remember { mutableStateOf(NdFilterSpec.ND1000) }
    val ndResult = remember(baseShutterIdx, selectedNd) {
        FieldCalculators.calculateNdExposure(baseShutterChoices[baseShutterIdx], selectedNd)
    }
    var bulbRemainingSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(bulbRemainingSec) {
        if (bulbRemainingSec > 0) {
            delay(1000L)
            bulbRemainingSec -= 1
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            OphotoCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("黄金时刻 · 蓝调时刻 · 月相 (${activeCity.name})", style = MaterialTheme.typography.titleMedium, color = BrassGold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CityLocationPreset.PRESETS) { city ->
                        OphotoChip(
                            label = city.name.substringBefore(" ·"),
                            selected = city.id == activeCity.id,
                            onClick = {
                                scope.launch { container.preferences.update { it.copy(selectedCityId = city.id) } }
                            },
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TopHudChip("◀ 前一天", false) { dayOffset -= 1 }
                    Text(
                        "${sunSchedule.date} · 正午高度角 ${sunSchedule.maxElevationDeg}°",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                    )
                    TopHudChip("后一天 ▶", false) { dayOffset += 1 }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OphotoMetricCell("晨间蓝调", sunSchedule.morningBlueStart, CyanTelemetry, Modifier.weight(1f))
                    OphotoMetricCell("日出时刻", sunSchedule.sunrise, BrassGold, Modifier.weight(1f))
                    OphotoMetricCell("暮色黄金", sunSchedule.eveningGoldenStart, ShutterOrange, Modifier.weight(1f))
                    OphotoMetricCell("晚间蓝调", sunSchedule.eveningBlueEnd, CyanTelemetry, Modifier.weight(1f))
                }
                Text(sunSchedule.lightingAdvice, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                OphotoDivider()
                Text(
                    text = "月相：${moonReport.phaseName}（照明 ${moonReport.illuminationPercent}% · 月龄 ${moonReport.ageDays}天） ${moonReport.astroRating}\n${moonReport.nightShootAdvice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpticalEmerald,
                )
            }
        }

        item {
            OphotoCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("景深 DoF · 超焦距 · 等效视场角", style = MaterialTheme.typography.titleMedium, color = BrassGold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SensorFormatSpec.entries) { spec ->
                        OphotoChip(
                            label = spec.label,
                            selected = sensorSpec == spec,
                            onClick = { sensorSpec = spec },
                        )
                    }
                }
                Text(
                    String.format(Locale.US, "等效焦距 %dmm · 光圈 f/%.1f · 物距 %.1fm", focalMm.roundToInt(), apertureF, subjectDistM),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                )
                Slider(
                    value = focalMm,
                    onValueChange = { focalMm = it },
                    valueRange = 14f..135f,
                    colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                )
                Slider(
                    value = subjectDistM,
                    onValueChange = { subjectDistM = it },
                    valueRange = 0.4f..20f,
                    colors = SliderDefaults.colors(thumbColor = OpticalEmerald, activeTrackColor = OpticalEmerald),
                )
                val farText = if (dofResult.farLimitMeters.isInfinite()) {
                    "∞"
                } else {
                    String.format(Locale.US, "%.2fm", dofResult.farLimitMeters)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OphotoMetricCell(
                        "超焦距",
                        String.format(Locale.US, "%.2fm", dofResult.hyperfocalMeters),
                        BrassGold,
                        Modifier.weight(1f),
                    )
                    OphotoMetricCell(
                        "前景清晰",
                        String.format(Locale.US, "%.2fm", dofResult.nearLimitMeters),
                        OpticalEmerald,
                        Modifier.weight(1f),
                    )
                    OphotoMetricCell("远景清晰", farText, OpticalEmerald, Modifier.weight(1f))
                    OphotoMetricCell("对角视角", "${eqResult.diagonalFovDeg}°", CyanTelemetry, Modifier.weight(1f))
                }
                Text(dofResult.summaryTip, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        item {
            OphotoCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ND 减光镜慢门换算 & B 门倒计时", style = MaterialTheme.typography.titleMedium, color = BrassGold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(NdFilterSpec.entries) { nd ->
                        OphotoChip(
                            label = nd.label,
                            selected = selectedNd == nd,
                            onClick = { selectedNd = nd },
                        )
                    }
                }
                Text(
                    "未装滤镜基准快门：${ndResult.baseShutterText}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Slider(
                    value = baseShutterIdx.toFloat(),
                    onValueChange = { baseShutterIdx = it.roundToInt().coerceIn(0, baseShutterChoices.lastIndex) },
                    valueRange = 0f..baseShutterChoices.lastIndex.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(SurfaceRecessed)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Column {
                        Text("加装后目标曝光", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            text = "${ndResult.targetShutterText}  (×${ndResult.stopMultiplier})",
                            style = MaterialTheme.typography.titleMedium,
                            color = BrassGold,
                        )
                    }
                    if (ndResult.targetShutterSeconds >= 1.0) {
                        Button(
                            onClick = {
                                bulbRemainingSec = if (bulbRemainingSec > 0) 0 else ndResult.targetShutterSeconds.roundToInt()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShutterOrange, contentColor = TextPrimary),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = if (bulbRemainingSec > 0) "倒计时 ${bulbRemainingSec}s" else "启动 B 门计时",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistAndGlossaryWorkspace(
    container: OphotoContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val completedKeys by container.mediaRepository.completedKeysFlow.collectAsState(initial = emptySet())
    var activeChallenge by remember { mutableStateOf(FieldAssistantCatalog.DAILY_CHALLENGES.first()) }
    var termDemoSlider by remember { mutableFloatStateOf(0.5f) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            OphotoCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("每日街头光影挑战", style = MaterialTheme.typography.titleMedium, color = BrassGold)
                Text(activeChallenge, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                OutlinedButton(
                    onClick = { activeChallenge = FieldAssistantCatalog.DAILY_CHALLENGES.random() },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceRecessed, contentColor = BrassGold),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("换一题现场挑战", color = BrassGold)
                }
            }
        }

        items(FieldAssistantCatalog.CHECKLIST_TEMPLATES, key = { it.id }) { tpl ->
            OphotoCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tpl.sceneName, style = MaterialTheme.typography.titleMedium, color = BrassGold)
                Text(tpl.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                (tpl.gearItems + tpl.cameraSetupItems).forEachIndexed { idx, line ->
                    val key = "${tpl.id}_$idx"
                    val checked = key in completedKeys
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch {
                                    container.mediaRepository.toggleFieldItem(key, "checklist", checked)
                                }
                            },
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                scope.launch {
                                    container.mediaRepository.toggleFieldItem(key, "checklist", checked)
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrassGold,
                                checkmarkColor = TextOnAccent,
                            ),
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (checked) TextMuted else TextPrimary,
                        )
                    }
                }
                OphotoDivider()
                Text("拍摄现场引导话术（点击复制）", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                tpl.icebreakerTalkLines.forEach { talk ->
                    Text(
                        text = talk,
                        style = MaterialTheme.typography.bodySmall,
                        color = OpticalEmerald,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(SurfaceRecessed)
                            .border(0.6.dp, BorderSubtle, RoundedCornerShape(9.dp))
                            .clickable {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText("Talk Line", talk))
                                Toast.makeText(context, "已复制引导话术", Toast.LENGTH_SHORT).show()
                            }
                            .padding(10.dp),
                    )
                }
            }
        }

        item {
            OphotoCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("交互式摄影光学词典", style = MaterialTheme.typography.titleMedium, color = BrassGold)
                Slider(
                    value = termDemoSlider,
                    onValueChange = { termDemoSlider = it },
                    colors = SliderDefaults.colors(
                        thumbColor = BrassGold,
                        activeTrackColor = BrassGold,
                        inactiveTrackColor = ControlPillBg,
                    ),
                )
                FieldAssistantCatalog.VISUAL_GLOSSARY.forEach { entry ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceRecessed)
                            .border(0.6.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Text("${entry.term} · ${entry.englishTag}", style = MaterialTheme.typography.titleSmall, color = BrassGold)
                        Text(entry.coreFormulaOrRule, style = MaterialTheme.typography.labelSmall, color = CyanTelemetry)
                        Text(entry.interactiveHint, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            text = if (termDemoSlider < 0.5f) "◀ 当前倾向：${entry.sliderMinLabel}" else "当前倾向：${entry.sliderMaxLabel} ▶",
                            style = MaterialTheme.typography.labelSmall,
                            color = OpticalEmerald,
                        )
                    }
                }
            }
        }
    }
}
