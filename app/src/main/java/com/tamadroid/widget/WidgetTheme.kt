package com.tamadroid.widget

/**
 * Widget color theme (widget-only; does not affect the in-app screen).
 * [bg]/[dot] are opaque ARGB ints; [alpha] (0..255) is the background opacity applied
 * on top of [bg] (lets the home wallpaper show through). [name] identifies presets.
 */
data class WidgetTheme(
    val name: String,
    val bg: Int,
    val dot: Int,
    val alpha: Int = 255,
)

/** Shared (de)serialization for custom preset lists: `name|bg|dot|alpha;...`. */
object ThemePresets {
    fun encode(list: List<WidgetTheme>): String =
        list.joinToString(";") { "${it.name}|${it.bg}|${it.dot}|${it.alpha}" }

    fun decode(raw: String?): List<WidgetTheme> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { rec ->
            val f = rec.split('|')
            if (f.size != 4) return@mapNotNull null
            runCatching { WidgetTheme(f[0], f[1].toInt(), f[2].toInt(), f[3].toInt()) }.getOrNull()
        }
    }
}

object WidgetThemes {
    val LCD_GREEN = WidgetTheme("LCD Green", 0xFF8C9B73.toInt(), 0xFF1B2410.toInt())
    val presets: List<WidgetTheme> = listOf(
        LCD_GREEN,
        WidgetTheme("Light", 0xFFE9E9E9.toInt(), 0xFF1A1A1A.toInt()),
        WidgetTheme("Dark", 0xFF11151A.toInt(), 0xFFE6E6E6.toInt()),
        WidgetTheme("Amber CRT", 0xFF180F00.toInt(), 0xFFFFB000.toInt()),
        WidgetTheme("Green CRT", 0xFF001400.toInt(), 0xFF33FF66.toInt()),
        WidgetTheme("Ice", 0xFF0E2230.toInt(), 0xFF8FE7FF.toInt()),
    )
}
