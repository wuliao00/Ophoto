package com.ophoto.domain.calculator

import com.ophoto.core.device.OemEcosystem
import com.ophoto.core.device.OppoDeviceInspector
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.engine.image.CurvePoint
import com.ophoto.engine.image.FrameStyle
import com.ophoto.engine.image.ImageProcessEngine
import com.ophoto.engine.image.PhotoEditRecipe
import org.junit.Assert.assertEquals
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
