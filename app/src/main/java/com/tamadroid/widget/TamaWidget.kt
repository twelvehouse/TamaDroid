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
            val theme = WidgetPrefs.theme(context)
            // Full-bleed background on the host view so the launcher rounds the corners itself.
            views.setInt(R.id.widget_root, "setBackgroundColor", WidgetRenderer.backgroundColor(theme))
            if (fb != null) {
                views.setImageViewBitmap(
                    R.id.widget_lcd,
                    WidgetRenderer.render(theme, WidgetPrefs.effect(context), fb, WidgetPrefs.packed(context))
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
 * Renders ONLY the 32x16 LCD dot matrix (transparent background) with a margin around it.
 * The widget's themeable, optionally translucent background is drawn full-bleed on the host
 * root view instead — so the launcher rounds the widget's corners itself (Android 12+) without
 * clipping any LCD content. The margin keeps the LCD clear of that corner rounding.
 */
object WidgetRenderer {
    private const val SCALE = 16
    private const val MARGIN = SCALE * 2       // LCD inset, so rounded corners never clip it

    fun render(theme: WidgetTheme, effect: LcdEffect, fb: ByteArray, packed: Boolean = true): Bitmap {
        val w = TamaCore.LCD_WIDTH * SCALE + MARGIN * 2
        val h = TamaCore.LCD_HEIGHT * SCALE + MARGIN * 2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        LcdFx.draw(c, fb, MARGIN.toFloat(), MARGIN.toFloat(), SCALE.toFloat(),
            theme.dot or (0xFF shl 24), effect, LcdFx.gap(packed))
        return bmp
    }

    /** The widget background colour (themeable, possibly translucent) for the host root view. */
    fun backgroundColor(theme: WidgetTheme): Int = (theme.alpha shl 24) or (theme.bg and 0x00FFFFFF)
}
