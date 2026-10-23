package com.ophoto.engine.render

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.ophoto.engine.filter.CubeLut3D
import com.ophoto.engine.filter.FilterParams
import com.ophoto.engine.filter.FilterEngineImpl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

enum class FocusPeakingColor(val label: String, val rgb: FloatArray) {
    OFF("关闭", floatArrayOf(0f, 0f, 0f)),
    RED("峰值红", floatArrayOf(1.0f, 0.22f, 0.18f)),
    GREEN("峰值绿", floatArrayOf(0.20f, 0.95f, 0.48f)),
    BLUE("峰值蓝", floatArrayOf(0.26f, 0.65f, 1.0f)),
    YELLOW("峰值黄", floatArrayOf(1.0f, 0.86f, 0.16f)),
}

/**
 * OpenGL ES 3.0 GPU Shader Pipeline (Doc §6):
 * Executes zero-copy external OES camera sampling, 3D LUT hardware trilinear lookup (`sampler3D`),
 * split-toning, vignette, grain, Sobel focus peaking, and diagonal zebra stripes at 60fps.
 */
class GlFilterShaderProgram {

    private var programId = 0
    private var lutTextureId = 0
    private var hasValidLut = false
    private var lutSize = 17

    private val quadCoords: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_VERTICES)
            position(0)
        }

    private val texCoords: FloatBuffer = ByteBuffer
        .allocateDirect(TEX_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(TEX_VERTICES)
            position(0)
        }

    fun initialize() {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_GLSL)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_GLSL)
        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)

        val texHandles = IntArray(1)
        GLES30.glGenTextures(1, texHandles, 0)
        lutTextureId = texHandles[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
    }

    fun uploadLut3D(lut: CubeLut3D?) {
        if (lutTextureId == 0) return
        if (lut == null) {
            hasValidLut = false
            return
        }
        val rgbaBuffer = lut.toRgbaByteBuffer()
        lutSize = lut.size
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGBA8,
            lut.size,
            lut.size,
            lut.size,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            rgbaBuffer,
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        hasValidLut = true
    }

    fun drawFrame(
        oesTextureId: Int,
        texMatrix: FloatArray,
        viewportWidth: Int,
        viewportHeight: Int,
        params: FilterParams,
        strength: Float,
        peakingColor: FocusPeakingColor,
        peakingSensitivity: Float,
        enableZebra: Boolean,
        frameTimeSec: Float,
    ) {
        if (programId == 0) return
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES30.glUseProgram(programId)

        val posLoc = GLES30.glGetAttribLocation(programId, "aPosition")
        val texLoc = GLES30.glGetAttribLocation(programId, "aTexCoord")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 0, quadCoords)
        GLES30.glEnableVertexAttribArray(texLoc)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 0, texCoords)

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(programId, "uTexMatrix"), 1, false, texMatrix, 0)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(programId, "uTexelSize"),
            1f / viewportWidth.coerceAtLeast(1),
            1f / viewportHeight.coerceAtLeast(1),
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uStrength"), strength.coerceIn(0f, 1f))
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uExposureEv"), params.exposureEv)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uContrast"), 1f + params.contrast / 100f * 0.65f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uSaturation"), (1f + params.saturation / 100f).coerceAtLeast(0f))
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(programId, "uWbShift"),
            params.temperature / 100f * 0.14f,
            params.tint / 100f * 0.10f,
        )

        val hiRgb = FilterEngineImpl.parseHexRgb(params.highlightTintHex)
        val shRgb = FilterEngineImpl.parseHexRgb(params.shadowTintHex)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(programId, "uHighlightTint"), 1, hiRgb, 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(programId, "uShadowTint"), 1, shRgb, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uCastMix"), params.colorCastStrength / 100f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uVignette"), params.vignette / 100f * 0.62f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uGrain"), params.grainStrength / 100f * 0.08f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uFade"), params.fade / 100f * 0.11f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uMonochrome"), if (params.monochrome) 1 else 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uHasLut"), if (hasValidLut) 1 else 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uLutSize"), lutSize.toFloat())

        val peakingEnabled = peakingColor != FocusPeakingColor.OFF
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uEnablePeaking"), if (peakingEnabled) 1 else 0)
        GLES30.glUniform3fv(GLES30.glGetUniformLocation(programId, "uPeakingColor"), 1, peakingColor.rgb, 0)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(programId, "uPeakingThreshold"),
            (0.32f - peakingSensitivity.coerceIn(0f, 1f) * 0.22f),
        )
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uEnableZebra"), if (enableZebra) 1 else 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uTime"), frameTimeSec)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uCameraTex"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uLutTex"), 1)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)
    }

    fun release() {
        if (lutTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            lutTextureId = 0
        }
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }

    companion object {
        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )

        private val TEX_VERTICES = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
        )

        const val VERTEX_SHADER_GLSL = """#version 300 es
            in vec4 aPosition;
            in vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            out vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        const val FRAGMENT_SHADER_GLSL = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            precision mediump sampler3D;

            in vec2 vTexCoord;
            out vec4 fragColor;

            uniform samplerExternalOES uCameraTex;
            uniform sampler3D uLutTex;
            uniform vec2 uTexelSize;
            uniform float uStrength;
            uniform float uExposureEv;
            uniform float uContrast;
            uniform float uSaturation;
            uniform vec2 uWbShift;
            uniform vec3 uHighlightTint;
            uniform vec3 uShadowTint;
            uniform float uCastMix;
            uniform float uVignette;
            uniform float uGrain;
            uniform float uFade;
            uniform int uMonochrome;
            uniform int uHasLut;
            uniform float uLutSize;
            uniform int uEnablePeaking;
            uniform vec3 uPeakingColor;
            uniform float uPeakingThreshold;
            uniform int uEnableZebra;
            uniform float uTime;

            float lumaOf(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            void main() {
                vec4 src = texture(uCameraTex, vTexCoord);
                vec3 orig = src.rgb;
                vec3 col = orig * exp2(uExposureEv);

                col.r += uWbShift.x + uWbShift.y * 0.35;
                col.g -= uWbShift.y * 0.70;
                col.b -= uWbShift.x - uWbShift.y * 0.35;

                col = clamp((col - 0.5) * uContrast + 0.5, 0.0, 1.0);
                float lum = lumaOf(col);
                if (uMonochrome == 1) {
                    col = vec3(lum);
                } else {
                    col = clamp(vec3(lum) + (col - vec3(lum)) * uSaturation, 0.0, 1.0);
                }

                if (uHasLut == 1) {
                    float scale = (uLutSize - 1.0) / uLutSize;
                    float offset = 0.5 / uLutSize;
                    col = texture(uLutTex, col * scale + offset).rgb;
                }

                if (uCastMix > 0.001) {
                    float l2 = lumaOf(col);
                    col = mix(col, uShadowTint, (1.0 - l2) * uCastMix * 0.35);
                    col = mix(col, uHighlightTint, l2 * uCastMix * 0.28);
                }

                col = col * (1.0 - uFade) + uFade;

                if (uGrain > 0.001) {
                    float n = fract(sin(dot(vTexCoord * (1000.0 + uTime), vec2(12.9898, 78.233))) * 43758.5453);
                    col += (n - 0.5) * uGrain;
                }

                if (uVignette > 0.001) {
                    vec2 d = (vTexCoord - 0.5) * 2.0;
                    float v = 1.0 - uVignette * dot(d, d) * 0.5;
                    col *= clamp(v, 0.2, 1.0);
                }

                col = mix(orig, clamp(col, 0.0, 1.0), uStrength);

                // Focus Peaking (GPU Sobel gradient magnitude)
                if (uEnablePeaking == 1) {
                    float tl = lumaOf(texture(uCameraTex, vTexCoord + vec2(-uTexelSize.x, uTexelSize.y)).rgb);
                    float tc = lumaOf(texture(uCameraTex, vTexCoord + vec2(0.0, uTexelSize.y)).rgb);
                    float tr = lumaOf(texture(uCameraTex, vTexCoord + vec2(uTexelSize.x, uTexelSize.y)).rgb);
                    float ml = lumaOf(texture(uCameraTex, vTexCoord + vec2(-uTexelSize.x, 0.0)).rgb);
                    float mr = lumaOf(texture(uCameraTex, vTexCoord + vec2(uTexelSize.x, 0.0)).rgb);
                    float bl = lumaOf(texture(uCameraTex, vTexCoord + vec2(-uTexelSize.x, -uTexelSize.y)).rgb);
                    float bc = lumaOf(texture(uCameraTex, vTexCoord + vec2(0.0, -uTexelSize.y)).rgb);
                    float br = lumaOf(texture(uCameraTex, vTexCoord + vec2(uTexelSize.x, -uTexelSize.y)).rgb);
                    float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
                    float gy = -tl - 2.0 * tc - tr + bl + 2.0 * bc + br;
                    float edge = length(vec2(gx, gy));
                    if (edge > uPeakingThreshold) {
                        col = mix(col, uPeakingColor, 0.85);
                    }
                }

                // Zebra Highlight Warning (> 96% luminance)
                if (uEnableZebra == 1 && max(max(col.r, col.g), col.b) > 0.96) {
                    float stripe = mod(gl_FragCoord.x + gl_FragCoord.y + uTime * 24.0, 12.0);
                    if (stripe < 5.0) {
                        col = vec3(0.08, 0.08, 0.08);
                    }
                }

                fragColor = vec4(col, 1.0);
            }
        """
    }
}
