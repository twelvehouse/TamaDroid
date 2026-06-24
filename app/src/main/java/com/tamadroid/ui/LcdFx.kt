package com.tamadroid.ui

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tamadroid.core.TamaCore

/** Visual effect applied to the LCD dot matrix (shared by the in-app screen and widget). */
enum class LcdEffect { NONE, GLOW, LCD, VHS }

/**
 * Draws the 32x16 lit dots of [fb] into the rect starting at ([left],[top]) with cell
 * size [pitch], in [dotColor], applying [effect]. Pure android.graphics so the exact
 * same look is used in-app (via drawIntoCanvas) and in the widget bitmap (the cached
 * coherent frame — no shader baking needed, applied per cached frame).
 */
object LcdFx {
    /** Visible inter-dot gap (fraction of cell pitch, each side). */
    const val GAP_SPACED = 0.10f   // distinct dots
    const val GAP_PACKED = 0.0f    // dots fill the cell (close, like the real LCD)
    fun gap(packed: Boolean) = if (packed) GAP_PACKED else GAP_SPACED

    fun draw(
        canvas: Canvas,
        fb: ByteArray,
        left: Float,
        top: Float,
        pitch: Float,
        dotColor: Int,
        effect: LcdEffect,
        gap: Float = GAP_SPACED,
    ) {
        val inset = pitch * gap
        val size = pitch * (1 - 2 * gap)
        val w = TamaCore.LCD_WIDTH * pitch
        val h = TamaCore.LCD_HEIGHT * pitch

        fun pass(color: Int, alpha: Int, dx: Float, blur: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = color
            p.alpha = alpha
            if (blur > 0f) p.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            for (y in 0 until TamaCore.LCD_HEIGHT) {
                for (x in 0 until TamaCore.LCD_WIDTH) {
                    if (fb[y * TamaCore.LCD_WIDTH + x].toInt() == 0) continue
                    val fx = left + x * pitch + inset + dx
                    val fy = top + y * pitch + inset
                    canvas.drawRect(fx, fy, fx + size, fy + size, p)
                }
            }
        }

        when (effect) {
            LcdEffect.NONE -> pass(dotColor, 255, 0f, 0f)

            LcdEffect.GLOW -> {
                pass(dotColor, 170, 0f, pitch * 0.7f)   // halo
                pass(dotColor, 255, 0f, 0f)             // crisp core
            }

            LcdEffect.LCD -> {
                pass(dotColor, 255, 0f, 0f)
                scanlines(canvas, left, top, w, h, pitch, alpha = 42)
            }

            LcdEffect.VHS -> {
                val dx = pitch * 0.16f
                pass(Color.rgb(255, 40, 40), 150, -dx, pitch * 0.25f)   // red ghost
                pass(Color.rgb(40, 120, 255), 150, dx, pitch * 0.25f)   // blue ghost
                pass(dotColor, 230, 0f, 0f)                             // core
                scanlines(canvas, left, top, w, h, pitch, alpha = 70)
            }
        }
    }

    private fun scanlines(c: Canvas, left: Float, top: Float, w: Float, h: Float, pitch: Float, alpha: Int) {
        val p = Paint().apply { color = Color.BLACK; this.alpha = alpha }
        val step = (pitch / 6f).coerceAtLeast(2f)
        var y = top
        while (y < top + h) {
            c.drawRect(left, y, left + w, y + step * 0.5f, p)
            y += step
        }
    }
}
