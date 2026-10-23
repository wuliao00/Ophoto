package com.ophoto.engine.filter

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Standard 3D LUT representation compliant with Adobe Cube LUT Spec 1.0 & Doc §4 constraints:
 * - Enforces 2 <= size <= 64
 * - Strictly 3 RGB floating-point channels (size^3 * 3 values)
 * - Rejects unknown script directives for security safety
 */
data class CubeLut3D(
    val title: String,
    val size: Int,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
    val table: FloatArray, // Interleaved RGB in Blue-major, Green-middle, Red-fastest order per Cube spec
) {
    init {
        require(size in 2..CubeLutParser.MAX_LUT_DIMENSION) {
            "LUT_3D_SIZE $size out of range 2..${CubeLutParser.MAX_LUT_DIMENSION}"
        }
        require(table.size == size * size * size * 3) {
            "Expected ${size * size * size * 3} floats, got ${table.size}"
        }
    }

    /**
     * Hardware-equivalent trilinear interpolation in 3D color space.
     * Writes mapped [r, g, b] in 0..1 to [outRgb].
     */
    fun sampleTrilinear(r: Float, g: Float, b: Float, outRgb: FloatArray) {
        val spanR = (domainMax[0] - domainMin[0]).coerceAtLeast(1e-5f)
        val spanG = (domainMax[1] - domainMin[1]).coerceAtLeast(1e-5f)
        val spanB = (domainMax[2] - domainMin[2]).coerceAtLeast(1e-5f)

        val nr = ((r - domainMin[0]) / spanR).coerceIn(0f, 1f) * (size - 1)
        val ng = ((g - domainMin[1]) / spanG).coerceIn(0f, 1f) * (size - 1)
        val nb = ((b - domainMin[2]) / spanB).coerceIn(0f, 1f) * (size - 1)

        val r0 = floor(nr).toInt().coerceIn(0, size - 1)
        val g0 = floor(ng).toInt().coerceIn(0, size - 1)
        val b0 = floor(nb).toInt().coerceIn(0, size - 1)
        val r1 = (r0 + 1).coerceAtMost(size - 1)
        val g1 = (g0 + 1).coerceAtMost(size - 1)
        val b1 = (b0 + 1).coerceAtMost(size - 1)

        val fr = nr - r0
        val fg = ng - g0
        val fb = nb - b0

        val plane = size * size
        val i000 = (b0 * plane + g0 * size + r0) * 3
        val i100 = (b0 * plane + g0 * size + r1) * 3
        val i010 = (b0 * plane + g1 * size + r0) * 3
        val i110 = (b0 * plane + g1 * size + r1) * 3
        val i001 = (b1 * plane + g0 * size + r0) * 3
        val i101 = (b1 * plane + g0 * size + r1) * 3
        val i011 = (b1 * plane + g1 * size + r0) * 3
        val i111 = (b1 * plane + g1 * size + r1) * 3

        for (c in 0..2) {
            val c00 = table[i000 + c] + (table[i100 + c] - table[i000 + c]) * fr
            val c10 = table[i010 + c] + (table[i110 + c] - table[i010 + c]) * fr
            val c01 = table[i001 + c] + (table[i101 + c] - table[i001 + c]) * fr
            val c11 = table[i011 + c] + (table[i111 + c] - table[i011 + c]) * fr
            val c0 = c00 + (c10 - c00) * fg
            val c1 = c01 + (c11 - c01) * fg
            outRgb[c] = (c0 + (c1 - c0) * fb).coerceIn(0f, 1f)
        }
    }

    /**
     * Converts this 3D LUT into a tightly packed RGBA8 direct [ByteBuffer] suitable for universal
     * hardware linear filtering (`GL_RGBA8` + `GL_LINEAR`) across all Adreno / Mali mobile GPUs.
     */
    fun toRgbaByteBuffer(): ByteBuffer {
        val voxelCount = size * size * size
        val buffer = ByteBuffer.allocateDirect(voxelCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until voxelCount) {
            val base = i * 3
            buffer.put((table[base].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
            buffer.put((table[base + 1].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
            buffer.put((table[base + 2].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
            buffer.put(0xFF.toByte())
        }
        buffer.position(0)
        return buffer
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CubeLut3D) return false
        return title == other.title && size == other.size && table.contentEquals(other.table)
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + size
        result = 31 * result + table.contentHashCode()
        return result
    }
}

class InvalidCubeLutException(val isSecurityViolation: Boolean, message: String) : IllegalArgumentException(message)

object CubeLutParser {

    const val MAX_LUT_DIMENSION = 64

    private val ALLOWED_KEYWORDS = setOf(
        "TITLE",
        "LUT_3D_SIZE",
        "DOMAIN_MIN",
        "DOMAIN_MAX",
        "LUT_3D_INPUT_RANGE",
    )

    private val SUSPICIOUS_TOKENS = setOf(
        "<script",
        "javascript:",
        "eval(",
        "import ",
        "runtime.getruntime",
    )

    fun parse(inputStream: InputStream, fallbackTitle: String = "Imported LUT"): CubeLut3D {
        var title = fallbackTitle
        var lutSize = -1
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        var table: FloatArray? = null
        var writeCursor = 0

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                val lower = trimmed.lowercase()
                if (SUSPICIOUS_TOKENS.any { lower.contains(it) }) {
                    throw InvalidCubeLutException(true, "Script payload prohibited inside .cube file")
                }

                val firstChar = trimmed[0]
                val isNumericLine = firstChar.isDigit() || firstChar == '-' || firstChar == '+' || firstChar == '.'
                if (!isNumericLine) {
                    val spaceIdx = trimmed.indexOfFirst { it.isWhitespace() }
                    val keyword = (if (spaceIdx > 0) trimmed.substring(0, spaceIdx) else trimmed).uppercase()
                    val rest = if (spaceIdx > 0) trimmed.substring(spaceIdx + 1).trim() else ""

                    if (keyword == "LUT_1D_SIZE") {
                        throw InvalidCubeLutException(false, "1D LUT is not supported; expected 3D .cube")
                    }
                    if (keyword !in ALLOWED_KEYWORDS) {
                        throw InvalidCubeLutException(true, "Unsupported or unsafe directive in .cube: $keyword")
                    }

                    when (keyword) {
                        "TITLE" -> title = rest.removeSurrounding("\"").trim().ifEmpty { fallbackTitle }
                        "LUT_3D_SIZE" -> {
                            val parsedSize = rest.toIntOrNull()
                                ?: throw InvalidCubeLutException(false, "Malformed LUT_3D_SIZE: $rest")
                            if (parsedSize !in 2..MAX_LUT_DIMENSION) {
                                throw InvalidCubeLutException(
                                    false,
                                    "LUT_3D_SIZE $parsedSize exceeds 2..$MAX_LUT_DIMENSION limit",
                                )
                            }
                            lutSize = parsedSize
                            table = FloatArray(parsedSize * parsedSize * parsedSize * 3)
                        }
                        "DOMAIN_MIN" -> domainMin = parseFloatTriplet(rest, "DOMAIN_MIN")
                        "DOMAIN_MAX" -> domainMax = parseFloatTriplet(rest, "DOMAIN_MAX")
                        "LUT_3D_INPUT_RANGE" -> {
                            val parts = rest.split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
                            if (parts.size == 2) {
                                domainMin = floatArrayOf(parts[0], parts[0], parts[0])
                                domainMax = floatArrayOf(parts[1], parts[1], parts[1])
                            }
                        }
                    }
                } else {
                    val dest = table ?: throw InvalidCubeLutException(
                        false,
                        "Numeric RGB row encountered before LUT_3D_SIZE header",
                    )
                    val triplet = parseFloatTriplet(trimmed, "RGB voxel")
                    if (writeCursor + 3 > dest.size) {
                        throw InvalidCubeLutException(false, "Extra RGB rows exceed LUT_3D_SIZE^3")
                    }
                    dest[writeCursor++] = triplet[0]
                    dest[writeCursor++] = triplet[1]
                    dest[writeCursor++] = triplet[2]
                }
            }
        }

        val finalTable = table ?: throw InvalidCubeLutException(false, "Missing LUT_3D_SIZE in .cube file")
        if (writeCursor != finalTable.size) {
            throw InvalidCubeLutException(
                false,
                "Incomplete LUT data: expected ${finalTable.size / 3} triplets, read ${writeCursor / 3}",
            )
        }

        return CubeLut3D(
            title = title,
            size = lutSize,
            domainMin = domainMin,
            domainMax = domainMax,
            table = finalTable,
        )
    }

    fun serialize(lut: CubeLut3D): String {
        val sb = StringBuilder(lut.table.size * 10 + 128)
        sb.appendLine("# Generated by Ophoto LUT Engine")
        sb.appendLine("TITLE \"${lut.title.replace("\"", "")}\"")
        sb.appendLine("LUT_3D_SIZE ${lut.size}")
        sb.appendLine(
            "DOMAIN_MIN %.4f %.4f %.4f".format(
                java.util.Locale.US,
                lut.domainMin[0],
                lut.domainMin[1],
                lut.domainMin[2],
            ),
        )
        sb.appendLine(
            "DOMAIN_MAX %.4f %.4f %.4f".format(
                java.util.Locale.US,
                lut.domainMax[0],
                lut.domainMax[1],
                lut.domainMax[2],
            ),
        )
        var idx = 0
        while (idx < lut.table.size) {
            sb.appendLine(
                "%.6f %.6f %.6f".format(
                    java.util.Locale.US,
                    lut.table[idx],
                    lut.table[idx + 1],
                    lut.table[idx + 2],
                ),
            )
            idx += 3
        }
        return sb.toString()
    }

    fun buildAnalyticalLut(
        title: String,
        size: Int = 17,
        transform: (r: Float, g: Float, b: Float, out: FloatArray) -> Unit,
    ): CubeLut3D {
        val data = FloatArray(size * size * size * 3)
        val tmp = FloatArray(3)
        var cursor = 0
        val denom = (size - 1).toFloat()
        for (bi in 0 until size) {
            val b = bi / denom
            for (gi in 0 until size) {
                val g = gi / denom
                for (ri in 0 until size) {
                    val r = ri / denom
                    transform(r, g, b, tmp)
                    data[cursor++] = tmp[0].coerceIn(0f, 1f)
                    data[cursor++] = tmp[1].coerceIn(0f, 1f)
                    data[cursor++] = tmp[2].coerceIn(0f, 1f)
                }
            }
        }
        return CubeLut3D(title = title, size = size, table = data)
    }

    private fun parseFloatTriplet(raw: String, label: String): FloatArray {
        val tokens = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size != 3) {
            throw InvalidCubeLutException(false, "Expected 3 RGB channels for $label, found ${tokens.size}")
        }
        val values = FloatArray(3)
        for (i in 0..2) {
            val v = tokens[i].toFloatOrNull()
                ?: throw InvalidCubeLutException(false, "Non-numeric float in $label: ${tokens[i]}")
            if (!v.isFinite()) {
                throw InvalidCubeLutException(false, "Non-finite value in $label")
            }
            values[i] = v
        }
        return values
    }
}
