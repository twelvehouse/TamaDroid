package com.tamadroid.data

import android.content.Context
import com.tamadroid.ui.LcdEffect
import com.tamadroid.widget.ThemePresets
import com.tamadroid.widget.WidgetTheme
import com.tamadroid.widget.WidgetThemes

/** In-app settings (game + display). Widget settings live in WidgetPrefs. */
object AppPrefs {
    private const val FILE = "app_prefs"
    private const val K_SPEED = "game_speed"
    private const val K_BG = "inapp_bg"
    private const val K_FX = "inapp_fx"
    private const val K_VSYNC = "inapp_vsync"
    private const val K_LCD_DOT = "lcd_dot"
    private const val K_LCD_CUSTOM = "lcd_custom"
    private const val K_PLAY_BG = "play_bg_color"
    private const val K_PLAY_BTN = "play_btn_color"
    private const val K_PLAY_IMG = "play_img"          // 1 if a bg image is set
    private const val K_PLAY_OFFX = "play_off_x"
    private const val K_PLAY_OFFY = "play_off_y"
    private const val K_PLAY_SCALE = "play_scale"

    /** Default in-app LCD dot color (classic dark LCD). */
    const val DEFAULT_LCD_DOT = 0xFF1B2410.toInt()
    const val DEFAULT_PLAY_BG = 0xFF101418.toInt()
    const val DEFAULT_PLAY_BTN = 0xFF6750A4.toInt()
    const val PLAY_BG_IMAGE_FILE = "play_bg.png"

    /** Play-screen background image transform (px offset + scale), or null if no image. */
    data class PlayImage(val offsetX: Float, val offsetY: Float, val scale: Float)

    /** Device-frame background (Pebble frame). The frame is auto-hidden when a custom
     * play background image is set. BASALT/MONO share the 144x168 geometry. */
    enum class Background { BASALT, MONO }

    const val MIN_SPEED = 1
    const val MAX_SPEED = 10   // multiplier (1 = real time)

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun gameSpeed(ctx: Context): Int = p(ctx).getInt(K_SPEED, 1).coerceIn(MIN_SPEED, MAX_SPEED)
    fun setGameSpeed(ctx: Context, mult: Int) { p(ctx).edit().putInt(K_SPEED, mult.coerceIn(MIN_SPEED, MAX_SPEED)).apply() }

    fun background(ctx: Context): Background =
        runCatching { Background.valueOf(p(ctx).getString(K_BG, Background.BASALT.name)!!) }
            .getOrDefault(Background.BASALT)
    fun setBackground(ctx: Context, bg: Background) { p(ctx).edit().putString(K_BG, bg.name).apply() }

    fun effect(ctx: Context): LcdEffect =
        runCatching { LcdEffect.valueOf(p(ctx).getString(K_FX, LcdEffect.NONE.name)!!) }
            .getOrDefault(LcdEffect.NONE)
    fun setEffect(ctx: Context, fx: LcdEffect) { p(ctx).edit().putString(K_FX, fx.name).apply() }

    fun inAppVsync(ctx: Context): Boolean = p(ctx).getBoolean(K_VSYNC, false)
    fun setInAppVsync(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean(K_VSYNC, on).apply() }

    // ---- In-app LCD theme: only the dot color is used (the frame is the bg image). ----
    fun lcdDot(ctx: Context): Int = p(ctx).getInt(K_LCD_DOT, DEFAULT_LCD_DOT)
    fun setLcdDot(ctx: Context, dot: Int) { p(ctx).edit().putInt(K_LCD_DOT, dot).apply() }

    /** Active LCD theme wrapped as a [WidgetTheme] for the shared theme editor (bg/alpha unused). */
    fun lcdTheme(ctx: Context): WidgetTheme =
        WidgetTheme("Active", WidgetThemes.LCD_GREEN.bg, lcdDot(ctx), 255)

    fun lcdCustomPresets(ctx: Context): List<WidgetTheme> =
        ThemePresets.decode(p(ctx).getString(K_LCD_CUSTOM, ""))

    fun addLcdCustomPreset(ctx: Context, t: WidgetTheme) {
        val list = lcdCustomPresets(ctx).filter { it.name != t.name } + t
        p(ctx).edit().putString(K_LCD_CUSTOM, ThemePresets.encode(list)).apply()
    }

    fun removeLcdCustomPreset(ctx: Context, name: String) {
        p(ctx).edit().putString(K_LCD_CUSTOM, ThemePresets.encode(lcdCustomPresets(ctx).filter { it.name != name })).apply()
    }

    // ---- Play screen ----
    fun playBgColor(ctx: Context): Int = p(ctx).getInt(K_PLAY_BG, DEFAULT_PLAY_BG)
    fun setPlayBgColor(ctx: Context, c: Int) { p(ctx).edit().putInt(K_PLAY_BG, c).apply() }

    fun playButtonColor(ctx: Context): Int = p(ctx).getInt(K_PLAY_BTN, DEFAULT_PLAY_BTN)
    fun setPlayButtonColor(ctx: Context, c: Int) { p(ctx).edit().putInt(K_PLAY_BTN, c).apply() }

    fun hasPlayImage(ctx: Context): Boolean =
        p(ctx).getBoolean(K_PLAY_IMG, false) && java.io.File(ctx.filesDir, PLAY_BG_IMAGE_FILE).exists()

    fun playImage(ctx: Context): PlayImage? {
        if (!hasPlayImage(ctx)) return null
        val sp = p(ctx)
        return PlayImage(sp.getFloat(K_PLAY_OFFX, 0f), sp.getFloat(K_PLAY_OFFY, 0f), sp.getFloat(K_PLAY_SCALE, 1f))
    }

    fun setPlayImage(ctx: Context, img: PlayImage) {
        p(ctx).edit()
            .putBoolean(K_PLAY_IMG, true)
            .putFloat(K_PLAY_OFFX, img.offsetX)
            .putFloat(K_PLAY_OFFY, img.offsetY)
            .putFloat(K_PLAY_SCALE, img.scale)
            .apply()
    }

    fun clearPlayImage(ctx: Context) {
        p(ctx).edit().putBoolean(K_PLAY_IMG, false).apply()
        java.io.File(ctx.filesDir, PLAY_BG_IMAGE_FILE).delete()
    }
}
