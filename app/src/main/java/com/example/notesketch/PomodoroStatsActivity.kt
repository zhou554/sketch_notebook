package com.example.notesketch

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.notesketch.databinding.ActivityPomodoroStatsBinding
import java.util.Calendar
import java.util.Locale

class PomodoroStatsActivity : AppCompatActivity() {

    private enum class Range { DAY, MONTH, YEAR }

    private lateinit var binding: ActivityPomodoroStatsBinding
    private var range = Range.DAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPomodoroStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnRangeDay.setOnClickListener { switchRange(Range.DAY) }
        binding.btnRangeMonth.setOnClickListener { switchRange(Range.MONTH) }
        binding.btnRangeYear.setOnClickListener { switchRange(Range.YEAR) }

        applyUi()
        switchRange(Range.DAY, force = true)
    }

    override fun onResume() {
        super.onResume()
        applyUi()
        renderStats()
    }

    private fun switchRange(next: Range, force: Boolean = false) {
        if (!force && range == next) return
        range = next
        renderRangeUi()
        renderStats()
    }

    private fun renderStats() {
        val cal = Calendar.getInstance()
        val summary = when (range) {
            Range.DAY -> PomodoroStatsStore.summaryForDay(this)
            Range.MONTH -> PomodoroStatsStore.summaryForMonth(
                this,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1
            )
            Range.YEAR -> PomodoroStatsStore.summaryForYear(this, cal.get(Calendar.YEAR))
        }
        binding.tvSummaryTitle.text = when (range) {
            Range.DAY -> "今日"
            Range.MONTH -> "本月"
            Range.YEAR -> "今年"
        }
        binding.tvSummaryValue.text = formatSummary(summary.minutes, summary.count)

        val rows = when (range) {
            Range.DAY -> PomodoroStatsStore.dayRecords(this, 30).map {
                RowItem(it.date, PomodoroStatsStore.formatDayLabel(it.date), it.minutes, it.count)
            }
            Range.MONTH -> PomodoroStatsStore.monthRecords(this).map {
                RowItem(it.label, it.label, it.minutes, it.count)
            }
            Range.YEAR -> PomodoroStatsStore.yearRecords(this).map {
                RowItem(it.label, it.label, it.minutes, it.count)
            }
        }
        binding.tvListTitle.text = when (range) {
            Range.DAY -> "最近 30 天"
            Range.MONTH -> "按月统计"
            Range.YEAR -> "按年统计"
        }
        renderRows(rows)
        val hasData = summary.minutes > 0 || summary.count > 0 || rows.any { it.minutes > 0 || it.count > 0 }
        binding.emptyView.visibility = if (rows.isEmpty() && !hasData) View.VISIBLE else View.GONE
    }

    private data class RowItem(
        val key: String,
        val label: String,
        val minutes: Int,
        val count: Int
    )

    private fun renderRows(rows: List<RowItem>) {
        binding.statsList.removeAllViews()
        if (rows.isEmpty()) return
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val card = ThemeUi.stickerPanelColor(theme)
        val ink = ThemeUi.contrastText(card)
        val muted = ThemeUi.contrastMuted(card)
        rows.forEachIndexed { index, row ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FFFEF8"))
                    cornerRadius = 10 * d
                    setStroke((1.5f * d).toInt().coerceAtLeast(1), Color.parseColor("#337A6F62"))
                }
            }
            val title = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = row.label
                textSize = 15f
                typeface = ResourcesCompat.getFont(this@PomodoroStatsActivity, R.font.patrick_hand)
                setTextColor(ink)
            }
            val value = TextView(this).apply {
                text = formatSummary(row.minutes, row.count)
                textSize = 14f
                typeface = ResourcesCompat.getFont(this@PomodoroStatsActivity, R.font.patrick_hand)
                setTextColor(muted)
            }
            rowView.addView(title)
            rowView.addView(value)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) topMargin = (8 * d).toInt()
            }
            binding.statsList.addView(rowView, lp)
        }
    }

    private fun formatSummary(minutes: Int, count: Int): String =
        "${minutes} 分钟 · ${count} 个番茄"

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        binding.contentPanel.setBackgroundColor(Color.TRANSPARENT)
        ThemeUi.colorTexts(theme.ink, binding.tvHeader, binding.tvListTitle)
        ThemeUi.colorTexts(theme.muted, binding.btnBack, binding.emptyView)
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
        val d = resources.displayMetrics.density
        val card = ThemeUi.stickerPanelColor(theme)
        binding.summaryCard.background = GradientDrawable().apply {
            setColor(card)
            setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
        }
        ThemeUi.colorTexts(ThemeUi.contrastText(card), binding.tvSummaryTitle, binding.tvSummaryValue)
        renderRangeUi()
    }

    private fun renderRangeUi() {
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val fill = Color.parseColor("#FFFEF8")
        val inkGreen = Color.parseColor("#3D5C4A")
        binding.rangeSegment.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 999 * d
            setStroke((1.5f * d).toInt().coerceAtLeast(2), inkGreen)
        }
        binding.rangeDivider1.setBackgroundColor(inkGreen)
        binding.rangeDivider2.setBackgroundColor(inkGreen)
        styleRangeHalf(binding.btnRangeDay, range == Range.DAY, theme, d, left = true, right = false)
        styleRangeHalf(binding.btnRangeMonth, range == Range.MONTH, theme, d, left = false, right = false)
        styleRangeHalf(binding.btnRangeYear, range == Range.YEAR, theme, d, left = false, right = true)
    }

    private fun styleRangeHalf(
        tv: TextView,
        on: Boolean,
        theme: ThemePalette,
        d: Float,
        left: Boolean,
        right: Boolean
    ) {
        tv.setTextColor(if (on) Color.parseColor("#FFFEF8") else ThemeUi.contrastText(Color.parseColor("#FFFEF8")))
        val r = 999 * d
        val radii = when {
            left -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            right -> floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            else -> FloatArray(8) { 0f }
        }
        tv.background = if (on) {
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#8BB8AB"),
                    theme.accent,
                    Color.parseColor("#5F9084")
                )
            ).apply { cornerRadii = radii }
        } else {
            GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadii = radii
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, PomodoroStatsActivity::class.java)
    }
}
