package com.ophoto.engine.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.view.Surface
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import com.ophoto.engine.filter.CubeLut3D
import com.ophoto.engine.filter.FilterParams
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Hardware-accelerated OpenGL ES 3.0 Camera Preview View:
 * Connects directly to CameraX [Preview.SurfaceProvider] via [SurfaceTexture] and renders
 * real-time `.cube` 3D LUTs, color grading, focus peaking, and zebra stripes on the GPU.
 */
class GlLutPreviewView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {

    private val shader = GlFilterShaderProgram()
    private val texMatrix = FloatArray(16)
    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var activeSurface: Surface? = null
    private var viewportW = 1
    private var viewportH = 1
    private val startNano = System.nanoTime()

    @Volatile
    private var currentParams = FilterParams()

    @Volatile
    private var currentStrength = 0.85f

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

    init {
        setEGLContextClientVersion(3)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setSurfaceProviderListener(listener: (Preview.SurfaceProvider) -> Unit) {
        onSurfaceReadyCallback = listener
    }

    fun updateRenderState(
        params: FilterParams,
        strength: Float,
        lut: CubeLut3D?,
        peaking: FocusPeakingColor,
        peakingLevel: Float,
        zebra: Boolean,
    ) {
        currentParams = params
        currentStrength = strength
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
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        shader.initialize()

        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener { requestRender() }
        surfaceTexture = st

        val mainExecutor = ContextCompat.getMainExecutor(context)
        val provider = Preview.SurfaceProvider { request ->
            val resolution = request.resolution
            st.setDefaultBufferSize(resolution.width, resolution.height)
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
        GLES30.glClearColor(0.035f, 0.04f, 0.05f, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val st = surfaceTexture ?: return
        runCatching {
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
        }

        if (lutDirty) {
            shader.uploadLut3D(pendingLut)
            lutDirty = false
        }

        val elapsedSec = ((System.nanoTime() - startNano) / 1_000_000_000.0).toFloat()
        shader.drawFrame(
            oesTextureId = oesTextureId,
            texMatrix = texMatrix,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
            params = currentParams,
            strength = currentStrength,
            peakingColor = peakingColor,
            peakingSensitivity = peakingSensitivity,
            enableZebra = enableZebra,
            frameTimeSec = elapsedSec,
        )
    }

    override fun onDetachedFromWindow() {
        queueEvent {
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
}
