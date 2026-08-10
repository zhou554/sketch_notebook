package com.example.notesketch

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton

object ThemeUi {

    private val inkDark = Color.parseColor("#3D3428")
    private val inkLight = Color.parseColor("#F4EFE6")
    private val mutedDark = Color.parseColor("#7A6F62")
    private val mutedLight = Color.parseColor("#D2C6B8")

    /** 按底色亮度选可读字色，避免深色主题 ink 画在浅色芯片上发虚。 */
    fun contrastText(background: Int): Int =
        if (ColorUtils.calculateLuminance(background) < 0.45) inkLight else inkDark

    fun contrastMuted(background: Int): Int =
        if (ColorUtils.calculateLuminance(background) < 0.45) mutedLight else mutedDark

    /** 深色纸页上的浅色贴纸底，保证便签/卡片始终可读。 */
    fun stickerPanelColor(theme: ThemePalette): Int =
        if (ColorUtils.calculateLuminance(theme.bg) < 0.45) Color.parseColor("#FFFEF8")
        else theme.surface

    val lightPanelFill = Color.parseColor("#FFFEF8")

    /** 从 accent 降亮度，用于按钮描边与分段分隔线。 */
    fun accentBorder(theme: ThemePalette): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(theme.accent, hsl)
        hsl[2] = (hsl[2] - 0.15f).coerceIn(0.15f, 0.55f)
        return ColorUtils.HSLToColor(hsl)
    }

    fun strokeWidth(density: Float, dp: Float = 1.5f): Int =
        (dp * density).toInt().coerceAtLeast(2)

    fun primaryButtonDrawable(theme: ThemePalette, cornerRadius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(theme.accent)
            this.cornerRadius = cornerRadius
        }

    fun segmentSelectedDrawable(theme: ThemePalette, cornerRadii: FloatArray): GradientDrawable =
        GradientDrawable().apply {
            setColor(theme.accent)
            this.cornerRadii = cornerRadii
        }

    fun outlinePanelDrawable(
        theme: ThemePalette,
        cornerRadius: Float,
        density: Float,
        fill: Int = lightPanelFill
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            this.cornerRadius = cornerRadius
            setStroke(strokeWidth(density), accentBorder(theme))
        }

    fun chipDrawable(
        theme: ThemePalette,
        selected: Boolean,
        cornerRadius: Float,
        density: Float
    ): GradientDrawable {
        val fill = if (selected) theme.accent else lightPanelFill
        return GradientDrawable().apply {
            setColor(fill)
            this.cornerRadius = cornerRadius
            setStroke(
                strokeWidth(density),
                if (selected) theme.accent else accentBorder(theme)
            )
        }
    }

    fun primaryOnAccentText(theme: ThemePalette): Int = contrastText(theme.accent)

    fun applyWindow(activity: Activity, theme: ThemePalette) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = theme.bg
        window.navigationBarColor = theme.bg
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            ColorUtils.calculateLuminance(theme.bg) >= 0.45
    }

    fun setPanel(panel: View, theme: ThemePalette, opacity: Int) {
        panel.setBackgroundColor(theme.surfaceWithAlpha(opacity))
    }

    fun colorTexts(color: Int, vararg views: TextView?) {
        views.forEach { it?.setTextColor(color) }
    }

    fun colorLines(color: Int, vararg views: View?) {
        views.forEach { it?.setBackgroundColor(color) }
    }

    fun styleEdit(et: EditText, theme: ThemePalette) {
        et.setTextColor(theme.ink)
        et.setHintTextColor(theme.muted)
        et.background = GradientDrawable().apply {
            setColor(theme.surface)
            setStroke(
                (1 * et.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                theme.line
            )
        }
    }

    /** 浅色贴纸卡片（#FFFEF8）上的输入框，字色不随深色主题变亮。 */
    fun styleLightPanelEdit(et: EditText, fill: Int = Color.parseColor("#FFFEF8")) {
        et.setTextColor(contrastText(fill))
        et.setHintTextColor(contrastMuted(fill))
        et.background = GradientDrawable().apply {
            setColor(fill)
            setStroke(
                (1 * et.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                Color.parseColor("#483D3428")
            )
        }
    }

    fun styleLightPanelButton(btn: MaterialButton, fill: Int = Color.parseColor("#FFFEF8")) {
        val ink = contrastText(fill)
        btn.setTextColor(ink)
        btn.strokeColor = ColorStateList.valueOf(ink)
    }

    fun styleButton(btn: MaterialButton, theme: ThemePalette) {
        btn.setTextColor(theme.ink)
        btn.strokeColor = ColorStateList.valueOf(theme.ink)
        btn.rippleColor = ColorStateList.valueOf(theme.accent)
    }

    fun styleFab(fab: TextView, theme: ThemePalette) {
        fab.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.accent)
        }
        fab.setTextColor(theme.surface)
    }

    fun themeChip(activity: Activity, theme: ThemePalette, selected: Boolean): TextView {
        val d = activity.resources.displayMetrics.density
        val fill = if (selected) theme.accent else theme.surface
        return TextView(activity).apply {
            text = theme.label
            setTextColor(contrastText(fill))
            textSize = 12f
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(fill)
                setStroke((1 * d).toInt().coerceAtLeast(1), theme.line)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (6 * d).toInt() }
            minHeight = (40 * d).toInt()
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
        }
    }

    fun patternChip(activity: Activity, pattern: PaperPattern, selected: Boolean, theme: ThemePalette): TextView {
        val d = activity.resources.displayMetrics.density
        // 未选中用浅色纸片底，避免深色主题 surface 把芯片字色带偏
        val fill = if (selected) theme.accent else Color.parseColor("#FFFEF8")
        return TextView(activity).apply {
            text = pattern.label
            setTextColor(contrastText(fill))
            textSize = 13f
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(fill)
                setStroke(
                    (1 * d).toInt().coerceAtLeast(1),
                    if (selected) theme.accent else Color.parseColor("#483D3428")
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (8 * d).toInt() }
            minHeight = (40 * d).toInt()
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
        }
    }

    /** 应用窗口亮度 + 纸张色与背景类型 */
    fun applyScrapbook(
        activity: Activity,
        paperBg: ScrapbookPaperView,
        paperColorOverride: Int? = null
    ) {
        val theme = UiPrefs.theme(activity)
        applyWindow(activity, theme)
        applyBrightness(activity)
        paperBg.paperColor = paperColorOverride ?: theme.bg
        paperBg.gridColor = theme.line
        paperBg.pattern = UiPrefs.paperType(activity)
    }

    fun applyBrightness(activity: Activity) {
        val b = UiPrefs.brightness(activity) / 100f
        val lp = activity.window.attributes
        lp.screenBrightness = b.coerceIn(0.3f, 1f)
        activity.window.attributes = lp
    }
}
