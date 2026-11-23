package com.ophoto.engine.filter

import android.graphics.Bitmap
import com.ophoto.engine.image.OpticalSoftEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalSoftAndGrainTest {

    @Test
    fun `grContrastLut produces smooth monotonic S-curve preserving endpoints`() {
        val lutPos = OpticalSoftEngine.grContrastLut(50)
        assertEquals(256, lutPos.size)
        assertEquals(0, lutPos[0])
        assertEquals(255, lutPos[255])
        // Midpoint should remain anchored around midtones
        assertTrue(lutPos[128] in 120..140)

        // Monotonic check
        for (i in 1..255) {
            assertTrue("LUT should be non-decreasing at $i", lutPos[i] >= lutPos[i - 1])
        }

        val lutNeg = OpticalSoftEngine.grContrastLut(-50)
        assertEquals(0, lutNeg[0])
        assertEquals(255, lutNeg[255])
    }

    @Test
    fun `soft filter specifications contain valid physical parameters`() {
        assertTrue("Must provide rich filter specifications", OpticalSoftEngine.SOFT_FILTERS.size >= 8)
        val hdf = OpticalSoftEngine.findSoftFilter("hdf")
        assertNotNull("HDF filter must exist", hdf)
        assertEquals("高光衍射（HDF）", hdf?.displayName)
        assertTrue("Core transmission must be between 0 and 1", hdf!!.core in 0f..1f)
        assertTrue("Scatter ratio must be positive", hdf.scatterRatio > 0f)

        val blackMist = OpticalSoftEngine.findSoftFilter("mist_1_4")
        assertNotNull("1/4 Black Mist must exist", blackMist)
        assertTrue("Black mist scatter should be moderate", blackMist!!.scatterRatio in 0.02f..0.08f)
    }

    @Test
    fun `filter params default includes two-scale grain and clean optical extensions`() {
        val params = FilterParams(
            grainStrength = 20,
            grainSize = 50,
            softFilterId = "hdf",
            softFilterAmount = 80,
            halation = 30,
            filmAcutance = 40,
            grContrast = 10,
        )
        assertEquals(20, params.grainStrength)
        assertEquals(50, params.grainSize)
        assertEquals("hdf", params.softFilterId)
        assertEquals(80, params.softFilterAmount)
        assertEquals(30, params.halation)
        assertEquals(40, params.filmAcutance)
        assertEquals(10, params.grContrast)
    }
}
