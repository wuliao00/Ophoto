package com.ophoto.ui.filter

import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Compact Offline Recipe Optical Matrix Generator:
 * Encodes an `OPHOTO1:` preset share code into a deterministic high-contrast 25x25 optical matrix
 * with standard finder corners + CRC32 verification bits for instant face-to-face preset sharing.
 */
object QrRecipeCodec {

    const val MATRIX_SIZE = 25

    fun encodeVisualMatrix(shareCode: String): Array<BooleanArray> {
        val bytes = shareCode.toByteArray(StandardCharsets.UTF_8)
        val crc = CRC32().apply { update(bytes) }.value
        val grid = Array(MATRIX_SIZE) { BooleanArray(MATRIX_SIZE) }

        drawFinderPattern(grid, 0, 0)
        drawFinderPattern(grid, MATRIX_SIZE - 7, 0)
        drawFinderPattern(grid, 0, MATRIX_SIZE - 7)

        // Timing pattern rows/columns
        for (i in 8 until MATRIX_SIZE - 8) {
            val bit = i % 2 == 0
            grid[6][i] = bit
            grid[i][6] = bit
        }

        var bitCursor = 0
        val totalBits = bytes.size * 8
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                if (isReservedModule(x, y)) continue
                val bit = if (bitCursor < totalBits) {
                    val b = bytes[bitCursor / 8].toInt() and 0xFF
                    ((b ushr (7 - (bitCursor % 8))) and 1) == 1
                } else {
                    (((crc ushr (bitCursor % 32)) xor (x * 13L + y * 7L)) and 1L) == 1L
                }
                grid[y][x] = bit
                bitCursor++
            }
        }
        return grid
    }

    private fun isReservedModule(x: Int, y: Int): Boolean {
        if (x < 8 && y < 8) return true
        if (x >= MATRIX_SIZE - 8 && y < 8) return true
        if (x < 8 && y >= MATRIX_SIZE - 8) return true
        if (x == 6 || y == 6) return true
        return false
    }

    private fun drawFinderPattern(grid: Array<BooleanArray>, startX: Int, startY: Int) {
        for (dy in 0..6) {
            for (dx in 0..6) {
                val outer = dx == 0 || dx == 6 || dy == 0 || dy == 6
                val inner = dx in 2..4 && dy in 2..4
                grid[startY + dy][startX + dx] = outer || inner
            }
        }
    }
}
