package com.example.notesketch

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils

data class ThemePalette(
    val id: String,
    val label: String,
    val bg: Int,
    val surface: Int,
    val ink: Int,
    val muted: Int,
    val line: Int,
    val accent: Int,
    val due: Int
) {
    fun surfaceWithAlpha(opacityPercent: Int): Int {
        val a = (opacityPercent.coerceIn(20, 100) * 255 / 100)
        return ColorUtils.setAlphaComponent(surface, a)
    }
}

object UiPrefs {
    private const val NAME = "notesketch_ui_prefs"
    private const val KEY_OPACITY = "content_opacity"
    private const val KEY_SEA_HEIGHT = "sea_height"
    private const val KEY_SEA_AMP = "sea_amp"
    private const val KEY_SHELL_FREQ = "shell_freq"
    private const val KEY_THEME = "theme_id"

    val themes = listOf(
        ThemePalette(
            id = "parchment",
            label = "羊皮纸",
            bg = Color.parseColor("#F3EDE2"),
            surface = Color.parseColor("#FFFBF5"),
            ink = Color.parseColor("#2C261E"),
            muted = Color.parseColor("#6F675C"),
            line = Color.parseColor("#C9BDB0"),
            accent = Color.parseColor("#4F5D45"),
            due = Color.parseColor("#8F3A32")
        ),
        ThemePalette(
            id = "sage",
            label = "鼠尾草",
            bg = Color.parseColor("#E8F0EA"),
            surface = Color.parseColor("#F7FBF8"),
            ink = Color.parseColor("#243028"),
            muted = Color.parseColor("#5F6E64"),
            line = Color.parseColor("#B7C7BB"),
            accent = Color.parseColor("#3E6B52"),
            due = Color.parseColor("#8F3A32")
        ),
        ThemePalette(
            id = "sky",
            label = "淡天蓝",
            bg = Color.parseColor("#E8EEF5"),
            surface = Color.parseColor("#F7FAFD"),
            ink = Color.parseColor("#1F2A38"),
            muted = Color.parseColor("#5C6B7A"),
            line = Color.parseColor("#B8C4D1"),
            accent = Color.parseColor("#3D5A73"),
            due = Color.parseColor("#8F3A32")
        ),
        ThemePalette(
            id = "rose",
            label = "浅玫瑰",
            bg = Color.parseColor("#F3E9EA"),
            surface = Color.parseColor("#FCF7F7"),
            ink = Color.parseColor("#322428"),
            muted = Color.parseColor("#746066"),
            line = Color.parseColor("#D0C0C3"),
            accent = Color.parseColor("#7A4E57"),
            due = Color.parseColor("#8F3A32")
        ),
        ThemePalette(
            id = "ink",
            label = "暖墨",
            bg = Color.parseColor("#EDE8E0"),
            surface = Color.parseColor("#F8F4ED"),
            ink = Color.parseColor("#1A1814"),
            muted = Color.parseColor("#6A6358"),
            line = Color.parseColor("#C4B9A8"),
            accent = Color.parseColor("#3A3630"),
            due = Color.parseColor("#8F3A32")
        )
    )

    fun theme(context: Context): ThemePalette {
        val id = prefs(context).getString(KEY_THEME, "parchment") ?: "parchment"
        return themes.firstOrNull { it.id == id } ?: themes.first()
    }

    fun setTheme(context: Context, id: String) {
        prefs(context).edit().putString(KEY_THEME, id).apply()
    }

    fun contentOpacity(context: Context) = prefs(context).getInt(KEY_OPACITY, 88)
    fun setContentOpacity(context: Context, v: Int) =
        prefs(context).edit().putInt(KEY_OPACITY, v.coerceIn(20, 100)).apply()

    fun seaHeight(context: Context) = prefs(context).getInt(KEY_SEA_HEIGHT, 50)
    fun setSeaHeight(context: Context, v: Int) =
        prefs(context).edit().putInt(KEY_SEA_HEIGHT, v.coerceIn(0, 100)).apply()

    fun seaAmp(context: Context) = prefs(context).getInt(KEY_SEA_AMP, 45)
    fun setSeaAmp(context: Context, v: Int) =
        prefs(context).edit().putInt(KEY_SEA_AMP, v.coerceIn(0, 100)).apply()

    fun shellFreq(context: Context) = prefs(context).getInt(KEY_SHELL_FREQ, 45)
    fun setShellFreq(context: Context, v: Int) =
        prefs(context).edit().putInt(KEY_SHELL_FREQ, v.coerceIn(0, 100)).apply()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
