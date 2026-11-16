package com.ophoto.ui.camera

import android.graphics.Bitmap
import com.ophoto.engine.camera.clampZoomRatio
import com.ophoto.engine.camera.describeCameraBindingFailure
import com.ophoto.engine.camera.formatZoomLabel
import com.ophoto.engine.camera.isTransientCameraError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 取景亮度自适应、帧采样与缩放/错误文案的回归测试（v1.5.0 取景亮度重建）。 */
@RunWith(RobolectricTestRunner::class)
class ViewfinderBrightnessTest {

    private fun solidBitmap(luma: Int, w: Int = 32, h: Int = 32): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(luma, luma, luma))
        return bmp
    }

    @Test
    fun `black frame is detected as black and needs a large lift`() {
        val sampler = PreviewSampler()
        val luma = sampler.sample(solidBitmap(6))!!
        assertTrue(luma.isBlack)
        assertFalse(luma.isDim)
        assertTrue(luma.meanPercent <= 3)
        val lift = PreviewSampler.requiredLift(luma)
        assertTrue("黑场需要明显提亮", lift >= 0.3f)
        assertTrue("提亮上限不超过 0.42", lift <= 0.42f)
    }

    @Test
    fun `dim frame is dim but not black`() {
        val sampler = PreviewSampler()
        val luma = sampler.sample(solidBitmap(40))!!
        assertFalse(luma.isBlack)
        assertTrue(luma.isDim)
        assertTrue(PreviewSampler.requiredLift(luma) in 0.05f..0.35f)
    }

    @Test
    fun `bright frame requires no lift`() {
        val sampler = PreviewSampler()
        val luma = sampler.sample(solidBitmap(150))!!
        assertFalse(luma.isBlack)
        assertFalse(luma.isDim)
        assertEquals(0f, PreviewSampler.requiredLift(luma), 0.0001f)
    }

    @Test
    fun `null or recycled bitmap is handled safely`() {
        val sampler = PreviewSampler()
        assertNull(sampler.sample(null))
        val bmp = solidBitmap(120)
        bmp.recycle()
        assertNull(sampler.sample(bmp))
        assertNull(sampler.histogram(null))
    }

    @Test
    fun `histogram from real frame reports bins and clip percentages`() {
        val sampler = PreviewSampler()
        val hist = sampler.histogram(solidBitmap(255, w = 120, h = 120))!!
        assertTrue("纯白画面应报告高光裁切", hist.highlightClipPercent > 99f)
        assertEquals(0f, hist.shadowClipPercent, 0.01f)
        assertEquals(64, hist.lumaBins.size)
        // 纯白亮度落在最后一个 bin
        assertTrue(hist.lumaBins.last() > 0)
    }

    @Test
    fun `zoom clamping respects lens limits and rejects NaN`() {
        assertEquals(1f, clampZoomRatio(1f, 1f, 8f), 0.0001f)
        assertEquals(1f, clampZoomRatio(0.2f, 1f, 8f), 0.0001f)
        assertEquals(8f, clampZoomRatio(42f, 1f, 8f), 0.0001f)
        assertEquals(1f, clampZoomRatio(Float.NaN, 1f, 8f), 0.0001f)
        assertEquals(0.6f, clampZoomRatio(0.6f, 0.6f, 10f), 0.0001f)
    }

    @Test
    fun `zoom label formats consistently`() {
        assertEquals("0.6x", formatZoomLabel(0.6f))
        assertEquals("1.0x", formatZoomLabel(1f))
        assertEquals("2.5x", formatZoomLabel(2.48f))
        assertEquals("10x", formatZoomLabel(10f))
    }

    @Test
    fun `camera in use error is translated to actionable chinese text`() {
        val text = describeCameraBindingFailure(
            IllegalStateException("Camera 0 is already in use by another client"),
        )
        assertTrue(text.contains("占用"))
    }

    @Test
    fun `transient capture errors are retryable, fatal ones are not`() {
        assertTrue(isTransientCameraError(androidx.camera.core.ImageCapture.ERROR_CAMERA_CLOSED))
        assertTrue(isTransientCameraError(androidx.camera.core.ImageCapture.ERROR_CAPTURE_FAILED))
        assertFalse(isTransientCameraError(androidx.camera.core.ImageCapture.ERROR_FILE_IO))
        assertFalse(isTransientCameraError(androidx.camera.core.ImageCapture.ERROR_INVALID_CAMERA))
    }
}
