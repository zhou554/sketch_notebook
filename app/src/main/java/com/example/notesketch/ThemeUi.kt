package com.example.notesketch

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton

object ThemeUi {

    fun applyWindow(activity: Activity, theme: ThemePalette) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = theme.bg
        window.navigationBarColor = theme.bg
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
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
        return TextView(activity).apply {
            text = theme.label
            setTextColor(if (selected) theme.surface else theme.ink)
            textSize = 12f
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(if (selected) theme.accent else theme.surface)
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
        return TextView(activity).apply {
            text = pattern.label
            setTextColor(if (selected) theme.surface else theme.ink)
            textSize = 13f
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(if (selected) theme.accent else theme.surface)
                setStroke((1 * d).toInt().coerceAtLeast(1), theme.line)
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
