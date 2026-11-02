package com.ophoto.engine.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureCompensationTest {
    @Test fun convertsAndClampsDeviceSteps() {
        assertEquals(2, exposureCompensationIndex(0.7f, 1f / 3f, -6, 6))
        assertEquals(-3, exposureCompensationIndex(-1f, 1f / 3f, -6, 6))
        assertEquals(3, exposureCompensationIndex(2f, 0.5f, -3, 3))
        assertEquals(7, exposureCompensationIndex(0.7f, 0.1f, -10, 10))
    }

    @Test fun invalidStepsDoNotCrash() {
        assertEquals(0, exposureCompensationIndex(1f, 0f, -3, 3))
        assertEquals(0, exposureCompensationIndex(Float.NaN, 0.5f, -3, 3))
    }
}
