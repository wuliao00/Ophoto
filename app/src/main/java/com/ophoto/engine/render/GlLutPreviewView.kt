package com.ophoto.engine.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import com.ophoto.engine.filter.CubeLut3D
import com.ophoto.engine.filter.FilterParams
import com.ophoto.engine.filter.HistogramData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ln

/**
 * Hardware-accelerated OpenGL ES 3.0 Camera Preview View:
 * Connects directly to CameraX [Preview.SurfaceProvider] via [SurfaceTexture] and renders
 * real-time `.cube` 3D LUTs, optical Kelvin WB, distortion-free aspect-ratio center-crop,
 * focus peaking, zebra stripes, and live 64x48 GPU-sampled RGB/Luma histogram at 60fps.
 */
class GlLutPreviewView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val shader = GlFilterShaderProgram()
    private val texMatrix = FloatArray(16)
    private val frameAvailable = AtomicBoolean(false)
    private var hasReceivedCameraFrame = false

    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var activeSurface: Surface? = null
    private var viewportW = 1
    private var viewportH = 1
    private var frameCounter = 0

    // Offscreen 64x48 miniature FBO for zero-stall real-time camera histogram sampling
    private var histFboId = 0
    private var histTexId = 0
    private val histReadBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(HIST_W * HIST_H * 4)
        .order(ByteOrder.nativeOrder())

    private val startNano = System.nanoTime()

    @Volatile
    private var streamUprightAspect = 3f / 4f

    @Volatile
    private var currentParams = FilterParams()

    @Volatile
    private var currentStrength = 0f

    @Volatile
    private var currentProWarmth = 0f

    @Volatile
    private var peakingColor = FocusPeakingColor.OFF

    @Volatile
    private var peakingSensitivity = 0.5f

    @Volatile
    private var enableZebra = false

    @Volatile
    private var pendingLut: CubeLut3D? = null

    @Volatile
    private var lutDirty = false

    private var onSurfaceReadyCallback: ((Preview.SurfaceProvider) -> Unit)? = null
    private var onHistogramCallback: ((HistogramData) -> Unit)? = null

    init {
        Matrix.setIdentityM(texMatrix, 0)
        setEGLContextClientVersion(3)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setSurfaceProviderListener(listener: (Preview.SurfaceProvider) -> Unit) {
        onSurfaceReadyCallback = listener
    }

    fun setHistogramListener(listener: ((HistogramData) -> Unit)?) {
        onHistogramCallback = listener
    }

    fun updateRenderState(
        params: FilterParams,
        strength: Float,
        lut: CubeLut3D?,
        peaking: FocusPeakingColor,
        peakingLevel: Float,
        zebra: Boolean,
        proKelvinWarmth: Float = 0f,
    ) {
        currentParams = params
        currentStrength = strength
        currentProWarmth = proKelvinWarmth
        peakingColor = peaking
        peakingSensitivity = peakingLevel
        enableZebra = zebra
        if (pendingLut !== lut) {
            pendingLut = lut
            lutDirty = true
        }
        requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        hasReceivedCameraFrame = false
        frameAvailable.set(false)
        Matrix.setIdentityM(texMatrix, 0)

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        shader.initialize()
        if (pendingLut != null) {
            shader.uploadLut3D(pendingLut)
            lutDirty = false
        }
        initHistogramFbo()

        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener(
            {
                frameAvailable.set(true)
                requestRender()
            },
            mainHandler,
        )
        surfaceTexture = st

        val mainExecutor = ContextCompat.getMainExecutor(context)
        val provider = Preview.SurfaceProvider { request ->
            val resolution = request.resolution
            val rw = resolution.width.coerceAtLeast(1)
            val rh = resolution.height.coerceAtLeast(1)
            streamUprightAspect = minOf(rw, rh).toFloat() / maxOf(rw, rh).toFloat()

            request.setTransformationInfoListener(mainExecutor) { info ->
                val rotated90 = info.rotationDegrees % 180 != 0
                val upW = if (rotated90) rh else rw
                val upH = if (rotated90) rw else rh
                streamUprightAspect = upW.toFloat() / upH.coerceAtLeast(1).toFloat()
                requestRender()
            }

            st.setDefaultBufferSize(rw, rh)
            activeSurface?.release()
            val surface = Surface(st)
            activeSurface = surface
            request.provideSurface(surface, mainExecutor) {
                if (activeSurface === surface) {
                    activeSurface = null
                }
                surface.release()
            }
        }
        mainExecutor.execute {
            onSurfaceReadyCallback?.invoke(provider)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClearColor(0.03f, 0.035f, 0.05f, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val st = surfaceTexture ?: return
        if (frameAvailable.compareAndSet(true, false)) {
            runCatching {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
                hasReceivedCameraFrame = true
            }
        }
        if (!hasReceivedCameraFrame) return

        if (lutDirty) {
            shader.uploadLut3D(pendingLut)
            lutDirty = false
        }

        // Compute distortion-free center-crop scale for upright camera frame vs viewport
        val dstAspect = viewportW.toFloat() / viewportH.coerceAtLeast(1).toFloat()
        val srcAspect = streamUprightAspect.coerceIn(0.25f, 4.0f)
        val (cropX, cropY) = if (dstAspect > srcAspect) {
            1f to (srcAspect / dstAspect).coerceIn(0.15f, 1f)
        } else {
            (dstAspect / srcAspect).coerceIn(0.15f, 1f) to 1f
        }

        val elapsedSec = ((System.nanoTime() - startNano) / 1_000_000_000.0).toFloat()
        val params = currentParams
        val strength = currentStrength
        val proWarmth = currentProWarmth

        shader.drawFrame(
            oesTextureId = oesTextureId,
            texMatrix = texMatrix,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
            params = params,
            strength = strength,
            peakingColor = peakingColor,
            peakingSensitivity = peakingSensitivity,
            enableZebra = enableZebra,
            frameTimeSec = elapsedSec,
            cropScaleX = cropX,
            cropScaleY = cropY,
            proKelvinWarmth = proWarmth,
        )

        // Lightweight 64x48 offscreen histogram sampling (~4 Hz)
        val histCb = onHistogramCallback
        frameCounter++
        if (histCb != null && histFboId != 0 && frameCounter % 16 == 0) {
            sampleLiveHistogram(
                cropX = cropX,
                cropY = cropY,
                params = params,
                strength = strength,
                proWarmth = proWarmth,
                elapsedSec = elapsedSec,
                callback = histCb,
            )
        }
    }

    private fun initHistogramFbo() {
        releaseHistogramFbo()
        val texArr = IntArray(1)
        val fboArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        histTexId = texArr[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, histTexId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            HIST_W,
            HIST_H,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        GLES30.glGenFramebuffers(1, fboArr, 0)
        histFboId = fboArr[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, histFboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            histTexId,
            0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            releaseHistogramFbo()
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun sampleLiveHistogram(
        cropX: Float,
        cropY: Float,
        params: FilterParams,
        strength: Float,
        proWarmth: Float,
        elapsedSec: Float,
        callback: (HistogramData) -> Unit,
    ) {
        val prevFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, prevFbo, 0)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, histFboId)
        shader.drawFrame(
            oesTextureId = oesTextureId,
            texMatrix = texMatrix,
            viewportWidth = HIST_W,
            viewportHeight = HIST_H,
            params = params,
            strength = strength,
            peakingColor = FocusPeakingColor.OFF,
            peakingSensitivity = 0.5f,
            enableZebra = false,
            frameTimeSec = elapsedSec,
            cropScaleX = cropX,
            cropScaleY = cropY,
            proKelvinWarmth = proWarmth,
        )

        histReadBuffer.position(0)
        GLES30.glReadPixels(
            0,
            0,
            HIST_W,
            HIST_H,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            histReadBuffer,
        )

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, prevFbo[0])
        GLES30.glViewport(0, 0, viewportW, viewportH)

        val red = IntArray(64)
        val green = IntArray(64)
        val blue = IntArray(64)
        val luma = IntArray(64)
        val count = HIST_W * HIST_H
        var overCount = 0
        var underCount = 0
        var sumLuma = 0f

        histReadBuffer.position(0)
        for (i in 0 until count) {
            val r = histReadBuffer.get().toInt() and 0xFF
            val g = histReadBuffer.get().toInt() and 0xFF
            val b = histReadBuffer.get().toInt() and 0xFF
            histReadBuffer.get() // alpha
            val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
            red[r ushr 2]++
            green[g ushr 2]++
            blue[b ushr 2]++
            luma[lum ushr 2]++
            if (r >= 250 || g >= 250 || b >= 250) overCount++
            if (lum <= 6) underCount++
            sumLuma += lum / 255f
        }

        val total = count.toFloat()
        val meanLuma = (sumLuma / total).coerceIn(0.01f, 0.99f)
        val evOffset = (ln(meanLuma / 0.42f) / ln(2.0f)).coerceIn(-3f, 3f)
        val hist = HistogramData(
            redBins = red,
            greenBins = green,
            blueBins = blue,
            lumaBins = luma,
            highlightClipPercent = (overCount / total) * 100f,
            shadowClipPercent = (underCount / total) * 100f,
            averageEvOffset = evOffset,
        )
        mainHandler.post { callback(hist) }
    }

    private fun releaseHistogramFbo() {
        if (histFboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(histFboId), 0)
            histFboId = 0
        }
        if (histTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(histTexId), 0)
            histTexId = 0
        }
    }

    override fun onDetachedFromWindow() {
        queueEvent {
            releaseHistogramFbo()
            shader.release()
            if (oesTextureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                oesTextureId = 0
            }
        }
        activeSurface?.release()
        activeSurface = null
        surfaceTexture?.release()
        surfaceTexture = null
        super.onDetachedFromWindow()
    }

    companion object {
        private const val HIST_W = 64
        private const val HIST_H = 48
    }
}
