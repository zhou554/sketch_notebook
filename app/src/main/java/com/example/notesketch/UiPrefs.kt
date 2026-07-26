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

    /** 拼贴手账森林狐狸主题（对齐 scrapbook-forest-fox-ui.html） */
    val themes = listOf(
        ThemePalette(
            id = "forest",
            label = "森林狐狸",
            bg = Color.parseColor("#F6F0E4"),
            surface = Color.parseColor("#FFFEF8"),
            ink = Color.parseColor("#3D3428"),
            muted = Color.parseColor("#7A6F62"),
            line = Color.parseColor("#DDD5C8"),
            accent = Color.parseColor("#7AAB9E"),
            due = Color.parseColor("#B03A32")
        ),
        ThemePalette(
            id = "parchment",
            label = "羊皮纸",
            bg = Color.parseColor("#F6F0E4"),
            surface = Color.parseColor("#FFF3C4"),
            ink = Color.parseColor("#3D3428"),
            muted = Color.parseColor("#7A6F62"),
            line = Color.parseColor("#DDD5C8"),
            accent = Color.parseColor("#E8A56A"),
            due = Color.parseColor("#B03A32")
        ),
        ThemePalette(
            id = "sage",
            label = "苔绿",
            bg = Color.parseColor("#F0F3E8"),
            surface = Color.parseColor("#FFFEF8"),
            ink = Color.parseColor("#3D3428"),
            muted = Color.parseColor("#6F7A62"),
            line = Color.parseColor("#D0D5C8"),
            accent = Color.parseColor("#5C7A54"),
            due = Color.parseColor("#B03A32")
        ),
        ThemePalette(
            id = "rose",
            label = "蜜桃",
            bg = Color.parseColor("#F6EDE8"),
            surface = Color.parseColor("#FFE4EC"),
            ink = Color.parseColor("#3D3428"),
            muted = Color.parseColor("#7A6F62"),
            line = Color.parseColor("#E0D5C8"),
            accent = Color.parseColor("#C45C2A"),
            due = Color.parseColor("#B03A32")
        ),
        ThemePalette(
            id = "ink",
            label = "木纹",
            bg = Color.parseColor("#F3EBE0"),
            surface = Color.parseColor("#FFFEF8"),
            ink = Color.parseColor("#3D2818"),
            muted = Color.parseColor("#7A6F62"),
            line = Color.parseColor("#D5C8B8"),
            accent = Color.parseColor("#5C4030"),
            due = Color.parseColor("#B03A32")
        )
    )

    fun theme(context: Context): ThemePalette {
        val id = prefs(context).getString(KEY_THEME, "forest") ?: "forest"
        return themes.firstOrNull { it.id == id }
            ?: themes.firstOrNull { it.id == "forest" }
            ?: themes.first()
    }

    fun setTheme(context: Context, id: String) {
        prefs(context).edit().putString(KEY_THEME, id).apply()
    }

    fun contentOpacity(context: Context) = prefs(context).getInt(KEY_OPACITY, 100)
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
