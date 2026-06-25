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
    private const val K_LCD_PACKED = "lcd_packed"
    private const val K_LCD_DOT = "lcd_dot"
    private const val K_LCD_CUSTOM = "lcd_custom"
    private const val K_ICON_SYNC = "icon_color_sync"   // icons follow the dot color
    private const val K_ICON_COLOR = "icon_color"         // custom icon color (when not synced)
    private const val K_PLAY_BG = "play_bg_color"
    private const val K_PLAY_BTN = "play_btn_color"
    private const val K_PLAY_BTN_ALPHA = "play_btn_alpha"
    private const val K_PLAY_BTN_POS = "play_btn_pos"  // "x,y;x,y;x,y" normalized centers
    private const val K_PLAY_IMG = "play_img"          // 1 if a bg image is set
    private const val K_PLAY_OFFX = "play_off_x"
    private const val K_PLAY_OFFY = "play_off_y"
    private const val K_PLAY_SCALE = "play_scale"
    private const val K_VIBRATE = "vibrate"
    private const val K_CLOCK_OVERWRITE = "clock_overwrite"  // stamp in-game clock with phone time

    /** Default in-app LCD dot color (classic dark LCD). */
    const val DEFAULT_LCD_DOT = 0xFF1B2410.toInt()
    const val PLAY_BG_IMAGE_FILE = "play_bg.png"

    // Default play-screen colors track the system theme until the user picks their own.
    const val DEFAULT_PLAY_BG_DARK = 0xFF101418.toInt()
    const val DEFAULT_PLAY_BG_LIGHT = 0xFFF2F3F5.toInt()
    const val DEFAULT_PLAY_BTN_DARK = 0xFFB69DF8.toInt()
    const val DEFAULT_PLAY_BTN_LIGHT = 0xFF6750A4.toInt()
    fun defaultPlayBg(dark: Boolean) = if (dark) DEFAULT_PLAY_BG_DARK else DEFAULT_PLAY_BG_LIGHT
    fun defaultPlayButton(dark: Boolean) = if (dark) DEFAULT_PLAY_BTN_DARK else DEFAULT_PLAY_BTN_LIGHT

    /** Play-screen background image transform (px offset + scale), or null if no image. */
    data class PlayImage(val offsetX: Float, val offsetY: Float, val scale: Float)

    const val DEFAULT_PLAY_BTN_ALPHA = 255

    /** Normalized button centers (fraction of the play area) for L/M/R: a centered row,
     *  low on screen, matching the classic layout. Customizable via the layout editor. */
    val DEFAULT_BTN_POS: List<Pair<Float, Float>> = listOf(
        0.27f to 0.80f,
        0.50f to 0.80f,
        0.73f to 0.80f,
    )

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

    /** Packed dots (no gap, close like the real LCD) vs spaced dots. Default packed. */
    fun lcdPacked(ctx: Context): Boolean = p(ctx).getBoolean(K_LCD_PACKED, true)
    fun setLcdPacked(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean(K_LCD_PACKED, on).apply() }

    // ---- In-app LCD theme: only the dot color is used (the frame is the bg image). ----
    fun lcdDot(ctx: Context): Int = p(ctx).getInt(K_LCD_DOT, DEFAULT_LCD_DOT)
    fun setLcdDot(ctx: Context, dot: Int) { p(ctx).edit().putInt(K_LCD_DOT, dot).apply() }

    // ---- Icon color: by default the LCD icons follow the dot color (tinted via their alpha
    // mask). Turning sync off reveals a dedicated icon color. ----
    fun iconColorSync(ctx: Context): Boolean = p(ctx).getBoolean(K_ICON_SYNC, true)
    fun setIconColorSync(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean(K_ICON_SYNC, on).apply() }

    /** The user's custom icon color (used when sync is off); defaults to the current dot color. */
    fun iconColorCustom(ctx: Context): Int = p(ctx).getInt(K_ICON_COLOR, lcdDot(ctx))
    fun setIconColorCustom(ctx: Context, c: Int) { p(ctx).edit().putInt(K_ICON_COLOR, c).apply() }

    /** Effective icon color to render with: the dot color when synced, else the custom color. */
    fun effectiveIconColor(ctx: Context): Int =
        if (iconColorSync(ctx)) lcdDot(ctx) else iconColorCustom(ctx)

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

    // ---- Vibration (button haptic feedback) ----
    fun vibrate(ctx: Context): Boolean = p(ctx).getBoolean(K_VIBRATE, true)
    fun setVibrate(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean(K_VIBRATE, on).apply() }

    // ---- Clock overwrite: forcibly stamp the in-game clock with the phone's time (on launch and
    // periodically). Just writes the clock RAM — it does not advance the simulation. Default OFF. ----
    fun clockOverwrite(ctx: Context): Boolean = p(ctx).getBoolean(K_CLOCK_OVERWRITE, false)
    fun setClockOverwrite(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean(K_CLOCK_OVERWRITE, on).apply() }

    // ---- Play screen ----
    // Colors fall back to a theme-appropriate default until the user picks one explicitly.
    fun playBgColor(ctx: Context, dark: Boolean): Int =
        p(ctx).let { if (it.contains(K_PLAY_BG)) it.getInt(K_PLAY_BG, 0) else defaultPlayBg(dark) }
    fun setPlayBgColor(ctx: Context, c: Int) { p(ctx).edit().putInt(K_PLAY_BG, c).apply() }

    fun playButtonColor(ctx: Context, dark: Boolean): Int =
        p(ctx).let { if (it.contains(K_PLAY_BTN)) it.getInt(K_PLAY_BTN, 0) else defaultPlayButton(dark) }
    fun setPlayButtonColor(ctx: Context, c: Int) { p(ctx).edit().putInt(K_PLAY_BTN, c).apply() }

    fun playButtonAlpha(ctx: Context): Int =
        p(ctx).getInt(K_PLAY_BTN_ALPHA, DEFAULT_PLAY_BTN_ALPHA).coerceIn(0, 255)
    fun setPlayButtonAlpha(ctx: Context, a: Int) { p(ctx).edit().putInt(K_PLAY_BTN_ALPHA, a.coerceIn(0, 255)).apply() }

    /** Normalized button centers for L/M/R, or the defaults if never customized. */
    fun playButtonPositions(ctx: Context): List<Pair<Float, Float>> {
        val raw = p(ctx).getString(K_PLAY_BTN_POS, null) ?: return DEFAULT_BTN_POS
        return runCatching {
            raw.split(';').map { rec ->
                val (x, y) = rec.split(',')
                x.toFloat() to y.toFloat()
            }.also { require(it.size == 3) }
        }.getOrDefault(DEFAULT_BTN_POS)
    }
    fun setPlayButtonPositions(ctx: Context, list: List<Pair<Float, Float>>) {
        p(ctx).edit().putString(K_PLAY_BTN_POS, list.joinToString(";") { "${it.first},${it.second}" }).apply()
    }

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

    /** Restores ALL in-app settings to defaults. Does NOT touch the ROM or the pet save. */
    fun resetSettings(ctx: Context) {
        p(ctx).edit().clear().apply()
        java.io.File(ctx.filesDir, PLAY_BG_IMAGE_FILE).delete()
    }
}
