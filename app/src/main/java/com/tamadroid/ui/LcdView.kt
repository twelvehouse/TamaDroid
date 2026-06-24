package com.tamadroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.tamadroid.core.TamaCore

private val LCD_BG = Color(0xFF8C9B73)   // greenish LCD背景
private val LCD_ON = Color(0xFF1B2410)   // ドット点灯

/**
 * Raw 32x16 LCD matrix renderer (M1). Icons & background frame come in M3.
 * [fb] is row-major, length [TamaCore.FRAME_BYTES] (y * 32 + x), each 0/1.
 */
@Composable
fun LcdView(fb: ByteArray, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.aspectRatio(TamaCore.LCD_WIDTH.toFloat() / TamaCore.LCD_HEIGHT)
    ) {
        val cw = size.width / TamaCore.LCD_WIDTH
        val ch = size.height / TamaCore.LCD_HEIGHT
        drawRect(LCD_BG, Offset.Zero, size)
        val gap = 0.08f
        for (y in 0 until TamaCore.LCD_HEIGHT) {
            for (x in 0 until TamaCore.LCD_WIDTH) {
                if (fb[y * TamaCore.LCD_WIDTH + x].toInt() != 0) {
                    drawRect(
                        color = LCD_ON,
                        topLeft = Offset(x * cw + cw * gap, y * ch + ch * gap),
                        size = Size(cw * (1 - 2 * gap), ch * (1 - 2 * gap))
                    )
                }
            }
        }
    }
}
