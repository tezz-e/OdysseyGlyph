package com.example.odysseyglyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.max

object QuickSignalRenderer {

    const val MATRIX_SIZE = 25
    const val TOTAL_PIXELS = MATRIX_SIZE * MATRIX_SIZE // 625
    const val MAX_CHARS = 30

    // Curated 25x25 bitmap patterns for symbols that remain crisp and legible on 25x25 LED diodes
    private val CURATED_SYMBOLS = mapOf(
        "♥" to listOf(
            ".........###...###.......",
            "........#####.#####......",
            ".......#############.....",
            ".......#############.....",
            ".......#############.....",
            "........###########......",
            ".........#########.......",
            "..........#######........",
            "...........#####.........",
            "............###..........",
            ".............#..........."
        ),
        "↑" to listOf(
            "............#............",
            "...........###...........",
            "..........#####..........",
            ".........#######.........",
            "........#########........",
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "...........###..........."
        ),
        "↓" to listOf(
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "........#########........",
            ".........#######.........",
            "..........#####..........",
            "...........###...........",
            "............#............"
        ),
        "←" to listOf(
            ".......#.................",
            "......##.................",
            ".....###.................",
            "....#################....",
            "....#################....",
            ".....###.................",
            "......##.................",
            ".......#................."
        ),
        "→" to listOf(
            ".................#.......",
            ".................##......",
            ".................###.....",
            "....#################....",
            "....#################....",
            ".................###.....",
            ".................##......",
            ".................#......."
        ),
        "✓" to listOf(
            "....................#....",
            "...................##....",
            "..................###....",
            "....#............####....",
            "....##..........####.....",
            "....###........####......",
            ".....###......####.......",
            "......###....####........",
            ".......###..####.........",
            "........#######..........",
            ".........#####...........",
            "..........###............"
        ),
        "✕" to listOf(
            "....###...........###....",
            ".....###.........###.....",
            "......###.......###......",
            ".......###.....###.......",
            "........###...###........",
            ".........#######.........",
            "..........#####..........",
            ".........#######.........",
            "........###...###........",
            ".......###.....###.......",
            "......###.......###......",
            ".....###.........###.....",
            "....###...........###...."
        ),
        "★" to listOf(
            "............#............",
            "...........###...........",
            "..........#####..........",
            "....#################....",
            ".....###############.....",
            "......#############......",
            ".......###########.......",
            "......#####.#####........",
            ".....#####...#####.......",
            "....####.......####......"
        ),
        "☺" to listOf(
            ".........#######.........",
            ".......###########.......",
            "......##.........##......",
            ".....##..##...##..##.....",
            "....##...##...##...##....",
            "....##.............##....",
            "....##...#.....#...##....",
            ".....##...#####...##.....",
            "......##.........##......",
            ".......###########.......",
            ".........#######........."
        ),
        "!" to listOf(
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "...........###...........",
            "............#............",
            ".........................",
            "...........###...........",
            "...........###..........."
        ),
        "?" to listOf(
            ".........#######.........",
            "........#########........",
            "........##.....##........",
            "...............##........",
            "..............###........",
            ".............###.........",
            "............###..........",
            "............###..........",
            ".........................",
            "............###..........",
            "............###.........."
        )
    )

    fun isCuratedSymbol(text: String): Boolean = CURATED_SYMBOLS.containsKey(text.trim())

    fun renderCuratedSymbol(symbol: String): ByteArray {
        val pattern = CURATED_SYMBOLS[symbol.trim()] ?: return ByteArray(TOTAL_PIXELS)
        val frame = ByteArray(TOTAL_PIXELS)

        val height = pattern.size
        val startY = max(0, (MATRIX_SIZE - height) / 2)

        for (r in pattern.indices) {
            val line = pattern[r]
            val y = startY + r
            if (y >= MATRIX_SIZE) break

            val width = line.length
            val startX = max(0, (MATRIX_SIZE - width) / 2)

            for (c in line.indices) {
                val x = startX + c
                if (x >= MATRIX_SIZE) break

                if (line[c] == '#') {
                    val idx = y * MATRIX_SIZE + x
                    frame[idx] = 255.toByte()
                }
            }
        }
        return applyCircularMask(frame)
    }

    /**
     * Renders a stationary or scrolling frame for the text using the live lyrics GlyphFontEngine styles:
     * - PIXEL_TINY ("Pixel")
     * - SMOOTH ("Smooth")
     * - BLOCK_BOLD ("Blocky")
     *
     * For 1-4 chars: stationary centered font on matrix.
     * For 5-30 chars: horizontal marquee at the specified scroll offset.
     */
    fun renderFrame(
        text: String,
        style: GlyphFontEngine.FontStyle = GlyphFontEngine.FontStyle.BLOCK_BOLD,
        scrollOffsetX: Float = 0f
    ): ByteArray {
        val trimmed = text.trim().take(MAX_CHARS)
        if (trimmed.isEmpty()) return ByteArray(TOTAL_PIXELS)

        if (isCuratedSymbol(trimmed)) {
            return renderCuratedSymbol(trimmed)
        }

        return if (trimmed.length <= 4) {
            val textWidth = GlyphFontEngine.measureTextWidth(trimmed, style, autoScale = true)
            val centeredX = (MATRIX_SIZE - textWidth) / 2f
            GlyphFontEngine.renderTextFrame(trimmed, style, centeredX, autoScale = true)
        } else {
            GlyphFontEngine.renderTextFrame(trimmed, style, scrollOffsetX, autoScale = false)
        }
    }

    fun measureMarqueeWidth(
        text: String,
        style: GlyphFontEngine.FontStyle = GlyphFontEngine.FontStyle.PIXEL_TINY
    ): Float {
        val trimmed = text.trim().take(MAX_CHARS)
        return GlyphFontEngine.measureTextWidth(trimmed, style, autoScale = false)
    }

    private fun applyCircularMask(frame: ByteArray): ByteArray {
        val center = 12f
        val radiusSq = 12f * 12f
        val masked = ByteArray(TOTAL_PIXELS)

        for (i in 0 until TOTAL_PIXELS) {
            val px = i % MATRIX_SIZE
            val py = i / MATRIX_SIZE
            val distSq = (px - center) * (px - center) + (py - center) * (py - center)
            if (distSq <= radiusSq) {
                masked[i] = frame[i]
            } else {
                masked[i] = 0
            }
        }
        return masked
    }
}
