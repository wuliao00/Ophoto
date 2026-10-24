package com.ophoto.domain.calculator

import com.ophoto.core.device.OemEcosystem
import com.ophoto.core.device.OppoDeviceInspector
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.engine.camera.ExposureConfig
import com.ophoto.engine.camera.ViewfinderAspectRatio
import com.ophoto.engine.filter.FilterEngineImpl
import com.ophoto.engine.image.CurvePoint
import com.ophoto.engine.image.FrameStyle
import com.ophoto.engine.image.ImageProcessEngine
import com.ophoto.engine.image.PhotoEditRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class FieldCalculatorsAndEngineTest {

    @Test
    fun `solar schedule in Hong Kong orders twilight sunrise noon sunset monotonically`() {
        val schedule = FieldCalculators.calculateSunSchedule(
            latitude = 22.3193,
            longitude = 114.1694,
            date = LocalDate.of(2026, 10, 23),
            zoneOffset = ZoneOffset.ofHours(8),
        )
        assertTrue(schedule.morningBlueStart < schedule.sunrise)
        assertTrue(schedule.sunrise < schedule.solarNoon)
        assertTrue(schedule.solarNoon < schedule.sunset)
        assertTrue(schedule.sunset < schedule.eveningBlueEnd)
        assertTrue(schedule.maxElevationDeg in 50.0..65.0)
    }

    @Test
    fun `moon calculator distinguishes new moon and full moon illumination`() {
        val newMoon = FieldCalculators.calculateMoonReport(LocalDate.of(2000, 1, 6))
        val fullMoon = FieldCalculators.calculateMoonReport(LocalDate.of(2000, 1, 21))
        assertTrue("Expected near 0% at reference new moon, got ${newMoon.illuminationPercent}", newMoon.illuminationPercent <= 3)
        assertTrue("Expected near 100% at full moon, got ${fullMoon.illuminationPercent}", fullMoon.illuminationPercent >= 96)
    }

    @Test
    fun `optical DoF ND filter and OEM device inspector produce accurate results`() {
        val dof = FieldCalculators.calculateDof(
            focalLengthMm = 23.0,
            apertureF = 1.8,
            subjectDistanceMeters = 2.0,
            sensor = SensorFormatSpec.PHONE_1_INCH,
        )
        assertTrue(dof.nearLimitMeters < 2.0)
        assertTrue(dof.farLimitMeters > 2.0)

        val nd = FieldCalculators.calculateNdExposure(1.0 / 125.0, NdFilterSpec.ND1000)
        assertEquals(1024, nd.stopMultiplier)
        assertEquals(8.192, nd.targetShutterSeconds, 0.01)
        assertTrue(nd.requiresTripodBulbTimer)

        assertEquals(OemEcosystem.OPPO_COLOROS, OppoDeviceInspector.detectEcosystem("OPPO", "OPPO"))
        assertEquals(OemEcosystem.ONEPLUS_COLOROS, OppoDeviceInspector.detectEcosystem("OnePlus", "OnePlus"))
        assertEquals(OemEcosystem.REALME_UI, OppoDeviceInspector.detectEcosystem("realme", "realme"))
    }

    @Test
    fun `pro mode exposure reciprocity prevents dark indoor preview and flags manual underexposure`() {
        // 1. Default Pro Mode starts in Auto ISO + Auto Shutter -> delegates to native Camera2 AE
        val defaultPro = ExposureConfig()
        assertFalse(defaultPro.isAnyManual)
        assertFalse(defaultPro.isFullManual)
        val autoPair = defaultPro.resolveManualParameters(ambientIso = 800, ambientExposureNs = 33_333_333L)
        assertNull(autoPair.first)
        assertNull(autoPair.second)

        // 2. Indoor scene (metered ISO 800 @ 1/30s = 33.33ms): locking ISO to 200 solves shutter to ~1/7.5s (133.3ms)
        val isoPriority = ExposureConfig(iso = 200, manualIso = true, manualShutter = false)
        val (solvedIso1, solvedNs1) = isoPriority.resolveManualParameters(
            ambientIso = 800,
            ambientExposureNs = 33_333_333L,
        )
        assertEquals(200, solvedIso1)
        assertEquals(133_333_332L, solvedNs1 ?: 0L)
        assertEquals(0f, isoPriority.estimateExposureDeltaStops(800, 33_333_333L), 0.001f)

        // 3. Indoor scene (metered ISO 800 @ 1/30s): locking shutter to 1/120s (8.33ms) boosts ISO to 3200
        val shutterPriority = ExposureConfig(
            shutterNs = 8_333_333L,
            manualIso = false,
            manualShutter = true,
        )
        val (solvedIso2, solvedNs2) = shutterPriority.resolveManualParameters(
            ambientIso = 800,
            ambientExposureNs = 33_333_333L,
        )
        assertEquals(3200, solvedIso2)
        assertEquals(8_333_333L, solvedNs2)

        // 4. Full manual dark lock (ISO 100 + 1/125s = 8ms vs ambient ISO 800 + 33.3ms) -> ~ -5.06 EV warning
        val darkManual = ExposureConfig(
            iso = 100,
            shutterNs = 8_000_000L,
            manualIso = true,
            manualShutter = true,
        )
        assertTrue(darkManual.isFullManual)
        val deltaEv = darkManual.estimateExposureDeltaStops(ambientIso = 800, ambientExposureNs = 33_333_333L)
        assertTrue("Expected severe negative EV delta (< -4.5 EV), got $deltaEv", deltaEv < -4.5f)
    }

    @Test
    fun `viewfinder portrait ratios and single-pass filter grading preserve brightness`() {
        assertEquals(0.75f, ViewfinderAspectRatio.RATIO_4_3.portraitRatio, 0.001f)
        assertEquals(9f / 16f, ViewfinderAspectRatio.RATIO_16_9.portraitRatio, 0.001f)
        assertEquals(1f, ViewfinderAspectRatio.RATIO_1_1.portraitRatio, 0.001f)

        val sample = BuiltInPresets.createSamplePreviewBitmap(80, 60)
        val preset = BuiltInPresets.ALL.first { it.id == "midnight_cyber" }
        val singlePassEngine = FilterEngineImpl().apply {
            loadFilterWithExplicitLut(preset, null)
            setStrength(preset.defaultStrength)
        }
        val singleGraded = singlePassEngine.applyToImage(sample)
        val singleHist = singlePassEngine.computeHistogram(singleGraded)
        assertTrue(
            "Single-pass grading should preserve shadow detail without crushing (>75% clip), got ${singleHist.shadowClipPercent}",
            singleHist.shadowClipPercent < 50f,
        )
    }

    @Test
    fun `image process engine renders curves preset and gallery signature frame on Robolectric bitmap`() {
        val sample = BuiltInPresets.createSamplePreviewBitmap(120, 90)
        val recipe = PhotoEditRecipe(
            preset = BuiltInPresets.ALL.first(),
            presetStrength = 0.9f,
            masterCurve = listOf(
                CurvePoint(0f, 0.06f),
                CurvePoint(0.5f, 0.54f),
                CurvePoint(1f, 0.96f),
            ),
            frameStyle = FrameStyle.OBSIDIAN_DARK,
            customWatermarkText = "OPPO Find X Series · Master",
        )
        val output = ImageProcessEngine.process(sample, recipe)
        assertTrue(output.width > sample.width)
        assertTrue(output.height > sample.height)
    }
}
