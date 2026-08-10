package com.example.notesketch

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ThemePalette(
    val id: String,
    val label: String,
    val bg: Int,
    val surface: Int,
    val ink: Int,
    val muted: Int,
    val line: Int,
    val accent: Int,
    val due: Int,
    val custom: Boolean = false
) {
    fun surfaceWithAlpha(opacityPercent: Int): Int {
        val a = (opacityPercent.coerceIn(20, 100) * 255 / 100)
        return ColorUtils.setAlphaComponent(surface, a)
    }

    companion object {
        fun fromCustomBg(id: String, label: String, bg: Int): ThemePalette {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(bg, hsl)
            val dark = ColorUtils.calculateLuminance(bg) < 0.45
            val surface = if (dark) {
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        hsl[0],
                        (hsl[1] * 0.45f).coerceIn(0f, 1f),
                        (hsl[2] + 0.1f).coerceIn(0.16f, 0.38f)
                    )
                )
            } else {
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        hsl[0],
                        (hsl[1] * 0.75f).coerceIn(0f, 1f),
                        (hsl[2] + 0.05f).coerceIn(0.86f, 0.98f)
                    )
                )
            }
            val line = if (dark) {
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        hsl[0],
                        (hsl[1] * 0.3f).coerceIn(0f, 1f),
                        (hsl[2] + 0.18f).coerceIn(0.28f, 0.55f)
                    )
                )
            } else {
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        hsl[0],
                        (hsl[1] * 0.35f).coerceIn(0f, 1f),
                        (hsl[2] - 0.14f).coerceIn(0.55f, 0.9f)
                    )
                )
            }
            val accent = ColorUtils.HSLToColor(
                floatArrayOf(
                    hsl[0],
                    (hsl[1] + 0.22f).coerceIn(0.28f, 1f),
                    if (dark) 0.48f else 0.42f
                )
            )
            val ink = if (dark) Color.parseColor("#F4EFE6") else Color.parseColor("#3D3428")
            val muted = if (dark) Color.parseColor("#D2C6B8") else Color.parseColor("#7A6F62")
            return ThemePalette(
                id = id,
                label = label,
                bg = bg,
                surface = surface,
                ink = ink,
                muted = muted,
                line = line,
                accent = accent,
                due = Color.parseColor("#B03A32"),
                custom = true
            )
        }
    }
}

object UiPrefs {
    private const val NAME = "notesketch_ui_prefs"
    private const val KEY_OPACITY = "content_opacity"
    private const val KEY_SEA_HEIGHT = "sea_height"
    private const val KEY_SEA_AMP = "sea_amp"
    private const val KEY_SHELL_FREQ = "shell_freq"
    private const val KEY_THEME = "theme_id"
    private const val KEY_BRIGHTNESS = "page_brightness"
    private const val KEY_PAPER_TYPE = "paper_type"
    private const val KEY_CUSTOM_THEMES = "custom_themes_json"
    private const val KEY_HIDDEN_THEMES = "hidden_theme_ids"
    private const val MAX_CUSTOM = 12

    val themes = listOf(
        ThemePalette(
            id = "forest",
            label = "森林狐狸",
            bg = Color.parseColor("#F6F0E4"),
            surface = Color.parseColor("#F8F3E9"),
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
            bg = Color.parseColor("#E8F0DE"),
            surface = Color.parseColor("#E2EDD4"),
            ink = Color.parseColor("#3D3428"),
            muted = Color.parseColor("#6F7A62"),
            line = Color.parseColor("#C5D0B8"),
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
            bg = Color.parseColor("#EBE2D4"),
            surface = Color.parseColor("#E6D9C6"),
            ink = Color.parseColor("#3D2818"),
            muted = Color.parseColor("#7A6F62"),
            line = Color.parseColor("#D0C0A8"),
            accent = Color.parseColor("#5C4030"),
            due = Color.parseColor("#B03A32")
        )
    )

    val paperTypes = listOf(
        PaperPattern.GRID,
        PaperPattern.BLANK,
        PaperPattern.LINED,
        PaperPattern.DOTS
    )

    fun customThemes(context: Context): List<ThemePalette> {
        val raw = prefs(context).getString(KEY_CUSTOM_THEMES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ThemePalette.fromCustomBg(
                            o.getString("id"),
                            o.getString("label"),
                            o.getInt("bg")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hiddenThemeIds(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_HIDDEN_THEMES, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun allThemes(context: Context): List<ThemePalette> {
        val hidden = hiddenThemeIds(context)
        return themes.filterNot { it.id in hidden } + customThemes(context)
    }

    fun theme(context: Context): ThemePalette {
        val id = prefs(context).getString(KEY_THEME, "forest") ?: "forest"
        return allThemes(context).firstOrNull { it.id == id }
            ?: allThemes(context).firstOrNull()
            ?: themes.first()
    }

    fun themeById(context: Context, id: String): ThemePalette =
        customThemes(context).firstOrNull { it.id == id }
            ?: themes.firstOrNull { it.id == id }
            ?: themes.firstOrNull { it.id == "parchment" }
            ?: themes.first()

    fun themeById(id: String): ThemePalette =
        themes.firstOrNull { it.id == id }
            ?: themes.firstOrNull { it.id == "parchment" }
            ?: themes.first()

    fun stickerColor(context: Context, colorId: String): Int =
        themeById(context, colorId).surface

    fun stickerColor(colorId: String): Int = themeById(colorId).surface

    fun setTheme(context: Context, id: String) {
        prefs(context).edit().putString(KEY_THEME, id).apply()
    }

    fun addCustomTheme(context: Context, label: String, bg: Int): ThemePalette? {
        val list = customThemes(context).toMutableList()
        if (list.size >= MAX_CUSTOM) return null
        val name = label.trim().ifEmpty { "自定义色" }.take(16)
        val palette = ThemePalette.fromCustomBg("custom_${UUID.randomUUID()}", name, bg)
        list.add(palette)
        saveCustomThemes(context, list)
        return palette
    }

    /** 删除任意背景色：自定义色移除；预设色隐藏。至少保留 1 个。 */
    fun removeTheme(context: Context, id: String): Boolean {
        val remaining = allThemes(context).filterNot { it.id == id }
        if (remaining.isEmpty()) return false
        val target = allThemes(context).firstOrNull { it.id == id } ?: return false
        if (target.custom) {
            saveCustomThemes(context, customThemes(context).filterNot { it.id == id })
        } else {
            val hidden = hiddenThemeIds(context).toMutableSet()
            hidden.add(id)
            prefs(context).edit().putString(KEY_HIDDEN_THEMES, hidden.joinToString(",")).apply()
        }
        if ((prefs(context).getString(KEY_THEME, "forest") ?: "forest") == id) {
            setTheme(context, remaining.first().id)
        }
        return true
    }

    fun removeCustomTheme(context: Context, id: String) {
        removeTheme(context, id)
    }

    private fun saveCustomThemes(context: Context, list: List<ThemePalette>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("label", t.label)
                    .put("bg", t.bg)
            )
        }
        prefs(context).edit().putString(KEY_CUSTOM_THEMES, arr.toString()).apply()
    }

    fun brightness(context: Context) = prefs(context).getInt(KEY_BRIGHTNESS, 100).coerceIn(30, 100)

    fun setBrightness(context: Context, v: Int) =
        prefs(context).edit().putInt(KEY_BRIGHTNESS, v.coerceIn(30, 100)).apply()

    fun paperType(context: Context): PaperPattern =
        PaperPattern.fromId(prefs(context).getString(KEY_PAPER_TYPE, "grid"))

    fun setPaperType(context: Context, type: PaperPattern) {
        prefs(context).edit().putString(KEY_PAPER_TYPE, type.id).apply()
    }

    fun exportToJson(context: Context): JSONObject {
        val arr = JSONArray()
        customThemes(context).forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("label", t.label)
                    .put("bg", t.bg)
            )
        }
        return JSONObject()
            .put("brightness", brightness(context))
            .put("themeId", prefs(context).getString(KEY_THEME, "forest") ?: "forest")
            .put("paperType", paperType(context).id)
            .put("contentOpacity", contentOpacity(context))
            .put("seaHeight", seaHeight(context))
            .put("seaAmp", seaAmp(context))
            .put("shellFreq", shellFreq(context))
            .put("customThemes", arr)
            .put("hiddenThemes", JSONArray(hiddenThemeIds(context).toList()))
    }

    fun applyImportedPrefs(context: Context, incoming: JSONObject, replace: Boolean) {
        val current = if (replace) JSONObject() else exportToJson(context)
        val out = mergePrefsJson(current, incoming)
        setBrightness(context, out.optInt("brightness", 100))
        out.optString("themeId").takeIf { it.isNotBlank() }?.let { setTheme(context, it) }
        out.optString("paperType").takeIf { it.isNotBlank() }?.let { id ->
            PaperPattern.fromId(id).let { setPaperType(context, it) }
        }
        val customArr = out.optJSONArray("customThemes") ?: JSONArray()
        val customList = buildList {
            for (i in 0 until customArr.length()) {
                val o = customArr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                add(
                    ThemePalette.fromCustomBg(
                        id,
                        o.optString("label", "自定义色"),
                        o.optInt("bg", Color.parseColor("#F6F0E4"))
                    )
                )
            }
        }
        saveCustomThemes(context, customList)
        val hiddenArr = out.optJSONArray("hiddenThemes") ?: JSONArray()
        val hidden = buildSet {
            for (i in 0 until hiddenArr.length()) {
                hiddenArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
        prefs(context).edit().putString(KEY_HIDDEN_THEMES, hidden.joinToString(",")).apply()
        setContentOpacity(context, out.optInt("contentOpacity", 100))
        setSeaHeight(context, out.optInt("seaHeight", 50))
        setSeaAmp(context, out.optInt("seaAmp", 45))
        setShellFreq(context, out.optInt("shellFreq", 45))
    }

    private fun mergePrefsJson(local: JSONObject, incoming: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("brightness", if (incoming.has("brightness")) incoming.optInt("brightness") else local.optInt("brightness", 100))
        out.put("themeId", incoming.optString("themeId").ifBlank { local.optString("themeId", "forest") })
        out.put("paperType", incoming.optString("paperType").ifBlank { local.optString("paperType", "grid") })
        out.put(
            "contentOpacity",
            if (incoming.has("contentOpacity")) incoming.optInt("contentOpacity")
            else local.optInt("contentOpacity", 100)
        )
        out.put(
            "seaHeight",
            if (incoming.has("seaHeight")) incoming.optInt("seaHeight")
            else local.optInt("seaHeight", 50)
        )
        out.put(
            "seaAmp",
            if (incoming.has("seaAmp")) incoming.optInt("seaAmp")
            else local.optInt("seaAmp", 45)
        )
        out.put(
            "shellFreq",
            if (incoming.has("shellFreq")) incoming.optInt("shellFreq")
            else local.optInt("shellFreq", 45)
        )
        val byId = linkedMapOf<String, JSONObject>()
        local.optJSONArray("customThemes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                byId[o.optString("id")] = o
            }
        }
        incoming.optJSONArray("customThemes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isNotBlank()) byId[id] = o
            }
        }
        val customOut = JSONArray()
        byId.values.forEach { customOut.put(it) }
        out.put("customThemes", customOut)
        val hidden = linkedSetOf<String>()
        local.optJSONArray("hiddenThemes")?.let { arr ->
            for (i in 0 until arr.length()) hidden.add(arr.optString(i))
        }
        incoming.optJSONArray("hiddenThemes")?.let { arr ->
            for (i in 0 until arr.length()) hidden.add(arr.optString(i))
        }
        out.put("hiddenThemes", JSONArray(hidden.toList()))
        return out
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
