package com.ophoto.engine.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeLutParserTest {

    @Test
    fun `identity 3D LUT trilinear interpolation matches input RGB across cube interior`() {
        val identityLut = CubeLutParser.buildAnalyticalLut("Identity Test", size = 5) { r, g, b, out ->
            out[0] = r
            out[1] = g * 0.5f
            out[2] = 1f - b
        }
        val serialized = CubeLutParser.serialize(identityLut)
        val reparsed = CubeLutParser.parse(serialized.byteInputStream(), "Fallback")

        assertEquals("Identity Test", reparsed.title)
        assertEquals(5, reparsed.size)

        val sampled = FloatArray(3)
        reparsed.sampleTrilinear(0.37f, 0.64f, 0.22f, sampled)
        assertEquals(0.37f, sampled[0], 0.002f)
        assertEquals(0.32f, sampled[1], 0.002f)
        assertEquals(0.78f, sampled[2], 0.002f)
    }

    @Test
    fun `rejects LUT_3D_SIZE exceeding 64 safety threshold`() {
        val oversized = """
            TITLE "Too Big"
            LUT_3D_SIZE 65
            0.0 0.0 0.0
        """.trimIndent()

        var caught: InvalidCubeLutException? = null
        try {
            CubeLutParser.parse(oversized.byteInputStream())
        } catch (ex: InvalidCubeLutException) {
            caught = ex
        }
        assertTrue(caught != null)
        assertEquals(false, caught?.isSecurityViolation)
    }

    @Test
    fun `flags embedded script payload inside cube file as security violation`() {
        val malicious = """
            TITLE "Evil LUT"
            <script>alert(1)</script>
            LUT_3D_SIZE 2
        """.trimIndent()

        var caught: InvalidCubeLutException? = null
        try {
            CubeLutParser.parse(malicious.byteInputStream())
        } catch (ex: InvalidCubeLutException) {
            caught = ex
        }
        assertTrue(caught?.isSecurityViolation == true)
    }
}
