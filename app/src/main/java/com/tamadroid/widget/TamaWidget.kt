package com.tamadroid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import com.tamadroid.MainActivity
import com.tamadroid.R
import com.tamadroid.core.TamaCore
import com.tamadroid.ui.LcdEffect
import com.tamadroid.ui.LcdFx
import java.io.File

/**
 * LCD-only home-screen widget (no buttons). Renders just the 32x16 dot matrix with a
 * themeable, optionally translucent rounded background. Tapping it opens the app.
 *
 * Updated live (~configurable) by [com.tamadroid.service.TamaService], and from the
 * last saved frame on system [onUpdate].
 */
class TamaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val fb = WidgetFrameStore.read(context)
        for (id in ids) pushView(context, mgr, id, fb)
    }

    companion object {
        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        /** Live update from the running emulator frame. */
        fun pushFrame(context: Context, fb: ByteArray) {
            val mgr = AppWidgetManager.getInstance(context)
            for (id in ids(context)) pushView(context, mgr, id, fb)
        }

        /** Re-render from the last saved frame (e.g. after a theme change). */
        fun refreshFromStore(context: Context) {
            val fb = WidgetFrameStore.read(context)
            val mgr = AppWidgetManager.getInstance(context)
            for (id in ids(context)) pushView(context, mgr, id, fb)
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TamaWidgetProvider::class.java))

        private fun pushView(context: Context, mgr: AppWidgetManager, id: Int, fb: ByteArray?) {
            val views = RemoteViews(context.packageName, R.layout.widget_tama)
            if (fb != null) {
                views.setImageViewBitmap(
                    R.id.widget_lcd,
                    WidgetRenderer.render(WidgetPrefs.theme(context), WidgetPrefs.effect(context), fb, WidgetPrefs.packed(context))
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            mgr.updateAppWidget(id, views)
        }
    }
}

/** Persists the latest frame so the widget has something to show before the service runs. */
object WidgetFrameStore {
    private const val FILE = "widget_frame.bin"

    fun write(context: Context, fb: ByteArray, icons: ByteArray) {
        File(context.filesDir, FILE).outputStream().use { it.write(fb); it.write(icons) }
        TamaWidgetProvider.pushFrame(context, fb)
    }

    fun read(context: Context): ByteArray? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        val b = f.readBytes()
        return if (b.size >= TamaCore.FRAME_BYTES) b.copyOfRange(0, TamaCore.FRAME_BYTES) else null
    }
}

/**
 * Renders the 32x16 LCD matrix to a themed bitmap with rounded corners and an optional
 * effect. Vertical margin = corner radius so the rounding never clips the (full-width)
 * LCD area. The effect is applied to the cached coherent (Vsync) frame — no shader
 * baking into a separate pass needed.
 */
object WidgetRenderer {
    private const val SCALE = 16
    private const val RADIUS = SCALE * 2f      // corner radius == vertical margin

    fun render(theme: WidgetTheme, effect: LcdEffect, fb: ByteArray, packed: Boolean = true): Bitmap {
        val w = TamaCore.LCD_WIDTH * SCALE
        val lcdH = TamaCore.LCD_HEIGHT * SCALE
        val vMargin = RADIUS.toInt()
        val h = lcdH + vMargin * 2

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (theme.alpha shl 24) or (theme.bg and 0x00FFFFFF)
        }
        c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), RADIUS, RADIUS, bgPaint)

        LcdFx.draw(c, fb, 0f, vMargin.toFloat(), SCALE.toFloat(), theme.dot or (0xFF shl 24), effect, LcdFx.gap(packed))
        return bmp
    }
}
