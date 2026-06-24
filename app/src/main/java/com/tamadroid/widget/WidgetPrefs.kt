package com.tamadroid.widget

import android.content.Context
import com.tamadroid.ui.LcdEffect

/**
 * Persists widget appearance (SharedPreferences). Custom presets are stored as
 * `name|bg|dot|alpha` records joined by ';'. Refresh is fixed (1 s) since Vsync keeps
 * frames coherent, so no refresh-rate setting is needed.
 */
object WidgetPrefs {
    private const val FILE = "widget_prefs"
    private const val K_BG = "bg"
    private const val K_DOT = "dot"
    private const val K_ALPHA = "alpha"
    private const val K_FX = "fx"
    private const val K_CUSTOM = "custom_presets"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun effect(ctx: Context): LcdEffect =
        runCatching { LcdEffect.valueOf(p(ctx).getString(K_FX, LcdEffect.NONE.name)!!) }
            .getOrDefault(LcdEffect.NONE)

    fun setEffect(ctx: Context, fx: LcdEffect) {
        p(ctx).edit().putString(K_FX, fx.name).apply()
        TamaWidgetProvider.refreshFromStore(ctx)
    }

    fun theme(ctx: Context): WidgetTheme {
        val sp = p(ctx)
        val d = WidgetThemes.LCD_GREEN
        return WidgetTheme(
            name = "Active",
            bg = sp.getInt(K_BG, d.bg),
            dot = sp.getInt(K_DOT, d.dot),
            alpha = sp.getInt(K_ALPHA, d.alpha),
        )
    }

    fun setTheme(ctx: Context, t: WidgetTheme) {
        p(ctx).edit().putInt(K_BG, t.bg).putInt(K_DOT, t.dot).putInt(K_ALPHA, t.alpha).apply()
        TamaWidgetProvider.refreshFromStore(ctx)
    }

    fun customPresets(ctx: Context): List<WidgetTheme> =
        ThemePresets.decode(p(ctx).getString(K_CUSTOM, ""))

    fun addCustomPreset(ctx: Context, t: WidgetTheme) {
        val list = customPresets(ctx).filter { it.name != t.name } + t
        p(ctx).edit().putString(K_CUSTOM, ThemePresets.encode(list)).apply()
    }

    fun removeCustomPreset(ctx: Context, name: String) {
        p(ctx).edit().putString(K_CUSTOM, ThemePresets.encode(customPresets(ctx).filter { it.name != name })).apply()
    }
}
