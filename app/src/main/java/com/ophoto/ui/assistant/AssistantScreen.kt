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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ophoto.FieldToCameraSetupCommand
import com.ophoto.OphotoContainer
import com.ophoto.core.permission.CameraPermissionHelper
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
import com.ophoto.ui.theme.BrassGold
import com.ophoto.ui.theme.ControlPillBg
import com.ophoto.ui.theme.CyanTelemetry
import com.ophoto.ui.theme.HairlineBorder
import com.ophoto.ui.theme.InstrumentDeck
import com.ophoto.ui.theme.ObsidianBg
import com.ophoto.ui.theme.OpticalEmerald
import com.ophoto.ui.theme.ShutterOrange
import com.ophoto.ui.theme.TextPrimary
import com.ophoto.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class AssistantSubSection(val label: String) {
    SHOT_CARDS("灵感镜头卡"),
    LIGHTING_STUDIO("布光灯位图"),
    CALCULATORS("现场计算器"),
    CHECKLIST_GLOSSARY("清单与词典"),
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "摄影现场助手 · Field HUD",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Text(
            text = "镜头灵感 · 俯视布光图 · 黄金时刻/景深/ND计算 · 出发清单与话术（全程离线）",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AssistantSubSection.entries) { sec ->
                TopHudChip(
                    label = sec.label,
                    active = activeSection == sec,
                    onClick = { activeSection = sec },
                )
            }
        }

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
                TopHudChip(
                    label = cat.label,
                    active = selectedScene == cat,
                    onClick = { onSelectScene(cat) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (isExpandedDualPane) {
            // Foldable unfolded dual-pane master-detail view!
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
                Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
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
                    Text(
                        text = "切换场景镜头卡（共 ${cards.size} 张）",
                        style = MaterialTheme.typography.titleSmall,
                        color = BrassGold,
                    )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BrassGold.copy(alpha = 0.16f) else InstrumentDeck)
            .border(
                1.dp,
                if (selected) BrassGold else HairlineBorder,
                RoundedCornerShape(12.dp),
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
            Text(
                text = "${card.category.label} · ${card.distanceHint} · ${card.recommendedExposure}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Text(
            text = "${card.focalEquivalentMm}mm",
            style = MaterialTheme.typography.labelMedium,
            color = OpticalEmerald,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InstrumentDeck, RoundedCornerShape(16.dp))
            .border(1.dp, BrassGold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                color = BrassGold,
            )
            Text(
                text = "${card.focalEquivalentMm}mm · ${card.recommendedKelvin}K",
                style = MaterialTheme.typography.labelMedium,
                color = CyanTelemetry,
            )
        }

        // Parametric Composition Viewfinder Blueprint Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0F18))
                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp)),
        ) {
            CompositionOverlayCanvas(
                guideType = card.compositionGuide,
                showLeveler = false,
                rollDegrees = 0f,
                focusTapNormalized = null,
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sx = card.subjectNormalizedX * size.width
                val sy = card.subjectNormalizedY * size.height
                // Light direction arrow
                val rad = Math.toRadians(card.lightDirectionDeg.toDouble())
                val lx = sx + (cos(rad) * 58.dp.toPx()).toFloat()
                val ly = sy - (sin(rad) * 58.dp.toPx()).toFloat()
                drawLine(
                    color = ShutterOrange,
                    start = Offset(lx, ly),
                    end = Offset(sx, sy),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                )
                drawCircle(ShutterOrange, radius = 5.dp.toPx(), center = Offset(lx, ly))
                // Subject visual anchor silhouette
                drawCircle(OpticalEmerald.copy(alpha = 0.25f), radius = 20.dp.toPx(), center = Offset(sx, sy))
                drawCircle(OpticalEmerald, radius = 7.dp.toPx(), center = Offset(sx, sy))
            }
            Text(
                text = "● 绿点：主体锚点   ● 橙虚线：主逆光方向 (${card.cameraHeightHint})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xAD090A0D))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Text(text = card.effectSummary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)

        card.stepByStepTips.forEachIndexed { idx, step ->
            Text(
                text = "${idx + 1}. $step",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        Text(
            text = "💡 现场应急变通：${card.fieldWorkaround}",
            style = MaterialTheme.typography.bodySmall,
            color = OpticalEmerald,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12231E), RoundedCornerShape(10.dp))
                .padding(8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                        ),
                    )
                    onApplyToCamera()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = ObsidianBg),
                shape = RoundedCornerShape(10.dp),
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
                shape = RoundedCornerShape(10.dp),
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
                        TopHudChip(
                            label = guide.title,
                            active = guide.id == selectedGuide.id,
                            onClick = { onSelectGuide(guide) },
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FieldAssistantCatalog.LIGHTING_GUIDES, key = { it.id }) { guide ->
                        TopHudChip(
                            label = guide.title,
                            active = guide.id == selectedGuide.id,
                            onClick = { onSelectGuide(guide) },
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InstrumentDeck, RoundedCornerShape(16.dp))
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(selectedGuide.title, style = MaterialTheme.typography.titleMedium, color = BrassGold)
                    Text(
                        "${selectedGuide.lightRatio} · ${selectedGuide.recommendedFocal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OpticalEmerald,
                    )
                }

                // 2D Top-Down Polar Studio Lighting Radar Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A0D14)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val maxR = (minOf(size.width, size.height) * 0.42f)

                        // Concentric optical distance rings
                        for (ring in 1..3) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = maxR * ring / 3f,
                                center = Offset(cx, cy),
                                style = Stroke(1.dp.toPx()),
                            )
                        }
                        // Camera optical axis (bottom 6 o'clock pointing up to subject)
                        val camY = cy + maxR * 0.92f
                        drawLine(
                            color = BrassGold.copy(alpha = 0.45f),
                            start = Offset(cx, camY),
                            end = Offset(cx, cy),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        )
                        drawCircle(BrassGold, radius = 6.dp.toPx(), center = Offset(cx, camY))

                        // Subject head/shoulders at center
                        drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy))

                        // Light nodes
                        selectedGuide.nodes.forEach { node ->
                            val rad = Math.toRadians((90.0 - node.angleDegFromCamera))
                            val lx = cx + (sin(Math.toRadians(node.angleDegFromCamera.toDouble())) * maxR * node.radiusRatio).toFloat()
                            val ly = cy + (cos(Math.toRadians(node.angleDegFromCamera.toDouble())) * maxR * node.radiusRatio).toFloat()
                            val col = Color(node.role.colorHex)
                            drawLine(col.copy(alpha = 0.55f), Offset(lx, ly), Offset(cx, cy), strokeWidth = 2.2.dp.toPx())
                            drawCircle(col, radius = 7.dp.toPx(), center = Offset(lx, ly))
                            val _unused = rad
                        }
                    }
                    Text(
                        text = "俯视灯位图（底部金点 = 相机机位，中心白点 = 被摄主体）",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
                    )
                }

                selectedGuide.nodes.forEach { node ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(node.role.colorHex)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${node.role.label}（${node.angleDegFromCamera.roundToInt()}°）：${node.powerNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                        )
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
                                compositionGuide = com.ophoto.data.local.CompositionGuideType.RULE_OF_THIRDS,
                                evOffset = -0.2f,
                                kelvin = 5400,
                                focalMmHint = 50,
                                tipBanner = "${selectedGuide.title} · ${selectedGuide.lightRatio}",
                            ),
                        )
                        onApplyToCamera()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = ObsidianBg),
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
    val settings by container.preferences.settingsFlow.collectAsState(initial = com.ophoto.data.local.UserSettings())

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

    // DoF & Equivalent Lens states
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

    // ND Long Exposure & Bulb Timer states
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
        // 1. Golden Hour / Blue Hour & Lunar Astro Card
        item {
            CalculatorCard("① 黄金时刻 · 蓝调时刻 · 月相星野（${activeCity.name}）") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CityLocationPreset.PRESETS) { city ->
                        TopHudChip(
                            label = city.name.substringBefore(" ·"),
                            active = city.id == activeCity.id,
                            onClick = {
                                scope.launch { container.preferences.update { it.copy(selectedCityId = city.id) } }
                            },
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    TopHudChip("◀ 前一天", false) { dayOffset -= 1 }
                    Text("${sunSchedule.date} · 正午高度角 ${sunSchedule.maxElevationDeg}°", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                    TopHudChip("后一天 ▶", false) { dayOffset += 1 }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TimeSlotBadge("晨间蓝调", sunSchedule.morningBlueStart, CyanTelemetry)
                    TimeSlotBadge("日出时刻", sunSchedule.sunrise, BrassGold)
                    TimeSlotBadge("傍晚黄金光", "${sunSchedule.eveningGoldenStart}-${sunSchedule.sunset}", ShutterOrange)
                    TimeSlotBadge("晚间蓝调", "${sunSchedule.sunset}-${sunSchedule.eveningBlueEnd}", CyanTelemetry)
                }
                Spacer(Modifier.height(6.dp))
                Text(sunSchedule.lightingAdvice, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "🌙 月相：${moonReport.phaseName}（照明 ${moonReport.illuminationPercent}% · 月龄 ${moonReport.ageDays}天） ${moonReport.astroRating}\n${moonReport.nightShootAdvice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpticalEmerald,
                )
            }
        }

        // 2. Optical Depth of Field + Sensor Crop Equivalent Calculator
        item {
            CalculatorCard("② 景深 DoF · 超焦距 · 画幅等效视场角") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SensorFormatSpec.entries) { spec ->
                        TopHudChip(
                            label = spec.label,
                            active = sensorSpec == spec,
                            onClick = { sensorSpec = spec },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("物理/模拟焦距 ${focalMm.roundToInt()}mm · 光圈 f/${"%.1f".format(apertureF)} · 物距 ${"%.1f".format(subjectDistM)}m", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                Slider(
                    value = focalMm,
                    onValueChange = { focalMm = it },
                    valueRange = 5f..135f,
                    colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                )
                Slider(
                    value = subjectDistM,
                    onValueChange = { subjectDistM = it },
                    valueRange = 0.4f..20f,
                    colors = SliderDefaults.colors(thumbColor = OpticalEmerald, activeTrackColor = OpticalEmerald),
                )
                val farText = if (dofResult.farLimitMeters.isInfinite()) "∞ 无穷远" else "${"%.2f".format(dofResult.farLimitMeters)}m"
                Text(
                    text = "超焦距：${"%.2f".format(dofResult.hyperfocalMeters)}m  |  清晰范围：${"%.2f".format(dofResult.nearLimitMeters)}m ~ $farText\n视角 FOV：对角 ${eqResult.diagonalFovDeg}° · 水平 ${eqResult.horizontalFovDeg}°（转换系数 ${eqResult.cropFactor}x）",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                )
                Text(dofResult.summaryTip, style = MaterialTheme.typography.bodySmall, color = OpticalEmerald)
            }
        }

        // 3. ND Filter Long Exposure + Bulb Countdown Timer
        item {
            CalculatorCard("③ ND 减光镜长曝光换算 & 现场 B 门计时器") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(NdFilterSpec.entries) { nd ->
                        TopHudChip(
                            label = nd.label,
                            active = selectedNd == nd,
                            onClick = { selectedNd = nd },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("未装滤镜基准快门：${ndResult.baseShutterText}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = baseShutterIdx.toFloat(),
                    onValueChange = { baseShutterIdx = it.roundToInt().coerceIn(0, baseShutterChoices.lastIndex) },
                    valueRange = 0f..baseShutterChoices.lastIndex.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "加装后目标曝光：${ndResult.targetShutterText} (×${ndResult.stopMultiplier})",
                        style = MaterialTheme.typography.titleSmall,
                        color = BrassGold,
                    )
                    if (ndResult.targetShutterSeconds >= 1.0) {
                        Button(
                            onClick = {
                                bulbRemainingSec = if (bulbRemainingSec > 0) 0 else ndResult.targetShutterSeconds.roundToInt()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShutterOrange),
                        ) {
                            Text(if (bulbRemainingSec > 0) "倒计时 ${bulbRemainingSec}s" else "启动 B 门计时")
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
        // Daily Practice Challenge Card
        item {
            CalculatorCard("🎯 每日拍摄灵感抽题") {
                Text(activeChallenge, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { activeChallenge = FieldAssistantCatalog.DAILY_CHALLENGES.random() },
                ) {
                    Text("换一题现场挑战", color = BrassGold)
                }
            }
        }

        // Pre-shoot Checklists & Model Icebreaker Talk Lines
        items(FieldAssistantCatalog.CHECKLIST_TEMPLATES, key = { it.id }) { tpl ->
            CalculatorCard("📋 ${tpl.sceneName}") {
                Text(tpl.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                (tpl.gearItems + tpl.cameraSetupItems).forEachIndexed { idx, line ->
                    val key = "${tpl.id}_$idx"
                    val checked = key in completedKeys
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
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
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (checked) TextSecondary else TextPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("🗣 拍摄现场引导话术（点击复制）：", style = MaterialTheme.typography.labelMedium, color = BrassGold)
                tpl.icebreakerTalkLines.forEach { talk ->
                    Text(
                        text = talk,
                        style = MaterialTheme.typography.bodySmall,
                        color = OpticalEmerald,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ControlPillBg)
                            .clickable {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText("Talk Line", talk))
                                Toast.makeText(context, "已复制引导话术", Toast.LENGTH_SHORT).show()
                            }
                            .padding(8.dp),
                    )
                }
            }
        }

        // Interactive Photography Visual Glossary
        item {
            CalculatorCard("📖 交互式摄影光学词典") {
                Slider(
                    value = termDemoSlider,
                    onValueChange = { termDemoSlider = it },
                    colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold),
                )
                FieldAssistantCatalog.VISUAL_GLOSSARY.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .background(ControlPillBg, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Text("${entry.term} (${entry.englishTag})", style = MaterialTheme.typography.titleSmall, color = BrassGold)
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

@Composable
private fun CalculatorCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InstrumentDeck, RoundedCornerShape(16.dp))
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = BrassGold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun TimeSlotBadge(label: String, timeText: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(ControlPillBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(timeText, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
