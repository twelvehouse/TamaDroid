package com.tamadroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tamadroid.core.TamaCore
import kotlin.math.roundToInt

/*
 * In-app device screen: mimics the Pebble default-platform layout (144x168 canvas).
 * Background frame + 8 LCD icons via Compose; the LCD dot matrix via the shared
 * [LcdFx] (so glow/LCD/VHS look identical to the widget). The in-app view and the widget
 * both read the same coherent frame published once per frame by the native run loop.
 */
private const val CANVAS_W = 144f
private const val CANVAS_H = 168f
private const val LCD_X = 8f
private const val LCD_Y = 51f
private const val DOT_PITCH = 4f
private const val ICON_W = 22f
private const val ICON_H = 18f
private const val LCD_ON = 0xFF1B2410.toInt()
// Icon row Y positions on the 144x168 device canvas, measured against the real device.
private const val ICON_TOP_Y = 28f
private const val ICON_BOTTOM_Y = 122f

private fun iconSlotX(i: Int) = 12f + (i % 4) * 32f
private fun iconSlotY(i: Int) = if (i > 3) ICON_BOTTOM_Y else ICON_TOP_Y

/** Maps the device-frame setting to a drawable. */
fun backgroundDrawable(bg: com.tamadroid.data.AppPrefs.Background): Int = when (bg) {
    com.tamadroid.data.AppPrefs.Background.BASALT -> com.tamadroid.R.drawable.bg_basalt
    com.tamadroid.data.AppPrefs.Background.MONO -> com.tamadroid.R.drawable.bg_nocolor
}

@Composable
fun TamaScreen(
    fb: ByteArray,
    icons: ByteArray,
    bgRes: Int?,
    effect: LcdEffect,
    dotColor: Int = LCD_ON,
    iconColor: Int? = null,   // null = follow the dot color (icons are tinted via their alpha mask)
    allIcons: Boolean = false,
    guideColor: androidx.compose.ui.graphics.Color? = null,
    packed: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bg = if (bgRes != null) ImageBitmap.imageResource(bgRes) else null
    val iconBitmaps = listOf(
        ImageBitmap.imageResource(com.tamadroid.R.drawable.icon1),
        ImageBitmap.imageResource(com.tamadroid.R.drawable.icon2),
        ImageBitmap.imageResource(com.tamadroid.R.drawable.icon3),
        ImageBitmap.imageResource(com.tamadroid.R.drawable.icon4),
        ImageBitmap.imageResource(com.tamadroid.R.drawable.icon5),
        ImageBitmap.imageResource(com.tamadroid.R.drawable.icon6),
        ImageBitmap.imageResource(com.tamadroid.R.drawable.icon7),
        ImageBitmap.imageResource(com.tamadroid.R.drawable.icon8),
    )

    // matchHeightConstraintsFirst: when given a bounded height (e.g. settings preview)
    // the width is derived to fit; in the main screen height is unbounded (scroll) so it
    // falls back to width — no overflow either way.
    Canvas(modifier = modifier.aspectRatio(CANVAS_W / CANVAS_H, matchHeightConstraintsFirst = true)) {
        val s = size.width / CANVAS_W

        if (bg != null) {
            drawImage(
                image = bg,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low
            )
        }

        drawIntoCanvas { canvas ->
            LcdFx.draw(canvas.nativeCanvas, fb, LCD_X * s, LCD_Y * s, DOT_PITCH * s, dotColor or (0xFF shl 24), effect, LcdFx.gap(packed))
        }

        // Icons are transparent-background black silhouettes; SrcIn tint replaces the opaque
        // pixels' color while keeping their alpha, i.e. uses the PNG as a mask so the icon
        // color matches the LCD dots (or a separately chosen color).
        val iconTint = ColorFilter.tint(Color((iconColor ?: dotColor) or (0xFF shl 24)), BlendMode.SrcIn)
        for (i in 0 until TamaCore.ICON_NUM) {
            if (!allIcons && icons.getOrNull(i)?.toInt() == 0) continue
            drawImage(
                image = iconBitmaps[i],
                dstOffset = IntOffset((iconSlotX(i) * s).roundToInt(), (iconSlotY(i) * s).roundToInt()),
                dstSize = IntSize((ICON_W * s).roundToInt(), (ICON_H * s).roundToInt()),
                filterQuality = FilterQuality.None,
                colorFilter = iconTint
            )
        }

        // Alignment guide (bg-image editor): a SQUARE outline centered on the device, to
        // approximate the real Tamagotchi's roughly-square body/screen proportions (the
        // 144x168 canvas itself is taller than the original).
        if (guideColor != null) {
            val inset = 4f * s
            val side = size.width - 2 * inset
            val top = (size.height - side) / 2f
            drawRoundRect(
                color = guideColor,
                topLeft = Offset(inset, top),
                size = Size(side, side),
                cornerRadius = CornerRadius(16f * s, 16f * s),
                style = Stroke(width = 3f * s)
            )
        }
    }
}
