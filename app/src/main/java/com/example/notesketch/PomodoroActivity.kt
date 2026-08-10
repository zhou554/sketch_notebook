package com.example.notesketch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.notesketch.databinding.ActivityPomodoroBinding
import java.util.Locale

class PomodoroActivity : AppCompatActivity() {

    private enum class Mode { STOPWATCH, COUNTDOWN, POMODORO }
    private enum class PomoPhase { WORK, SHORT_BREAK, LONG_BREAK }

    private lateinit var binding: ActivityPomodoroBinding

    private var mode = Mode.POMODORO
    private var running = false
    private var elapsedMs = 0L
    private var remainingMs = WORK_MS
    private var workDurationMs = WORK_MS
    private var countdownTotalMs = 5 * 60_000L
    private var phase = PomoPhase.WORK
    private var completedToday = 0
    private var focusMinutesToday = 0
    private var cycleInSet = 0
    private var handlingFinish = false
    /** 当前轮次是否已完成专注（完成后才允许休息计时）。 */
    private var workCompletedInSet = false

    private var tickBaseElapsedRealtime = 0L
    private var tickBaseElapsedMs = 0L
    private var tickBaseRemainingMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            when (mode) {
                Mode.STOPWATCH -> {
                    elapsedMs = tickBaseElapsedMs + (now - tickBaseElapsedRealtime)
                    renderTimer()
                }
                Mode.COUNTDOWN, Mode.POMODORO -> {
                    remainingMs = (tickBaseRemainingMs - (now - tickBaseElapsedRealtime)).coerceAtLeast(0L)
                    renderTimer()
                    if (remainingMs <= 0L) {
                        onTimerFinished()
                        return
                    }
                }
            }
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPomodoroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadStats()

        binding.btnBackRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnModeStopwatch.setOnClickListener { switchMode(Mode.STOPWATCH) }
        binding.btnModeCountdown.setOnClickListener { switchMode(Mode.COUNTDOWN) }
        binding.btnModePomodoro.setOnClickListener { switchMode(Mode.POMODORO) }
        binding.btnPrimary.setOnClickListener { toggleRun() }
        binding.btnReset.setOnClickListener { resetTimer() }
        binding.btnSkip.setOnClickListener { skipPhase() }

        applyUi()
        switchMode(Mode.POMODORO, force = true)
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        reloadStatsFromPrefs()
        applyUi()
        renderModeUi()
        renderPresets()
        renderTimer()
        refreshStats()
        refreshControls()
    }

    override fun onPause() {
        super.onPause()
        // keep ticker if running so returning mid-session stays accurate via elapsedRealtime
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        binding.contentPanel.setBackgroundColor(Color.TRANSPARENT)
        ThemeUi.colorTexts(theme.ink, binding.tvHeader)
        ThemeUi.colorTexts(theme.muted, binding.btnBack, binding.btnSkip)
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
        val d = resources.displayMetrics.density
        val card = ThemeUi.stickerPanelColor(theme)
        val panelInk = Color.parseColor("#3D3428")
        val panelMuted = Color.parseColor("#6F6256")
        binding.timerCard.background = GradientDrawable().apply {
            setColor(Color.parseColor("#FFFEF8"))
            cornerRadius = 12 * d
            setStroke((2 * d).toInt().coerceAtLeast(1), Color.parseColor("#2E3D3428"))
        }
        binding.tvPhase.setTextColor(panelMuted)
        binding.tvTimer.setTextColor(panelInk)
        binding.statsCard.background = GradientDrawable().apply {
            setColor(card)
            setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
        }
        ThemeUi.colorTexts(ThemeUi.contrastText(card), binding.tvStatsTitle, binding.tvStats)
        stylePrimaryButtons()
        renderModeUi()
    }

    private fun stylePrimaryButtons() {
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val r = 999 * d
        binding.btnPrimary.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#8BB8AB"),
                theme.accent,
                Color.parseColor("#5F9084")
            )
        ).apply { cornerRadius = r }
        binding.btnPrimary.setTextColor(Color.parseColor("#FFFEF8"))
        binding.btnReset.background = GradientDrawable().apply {
            setColor(Color.parseColor("#FFFEF8"))
            cornerRadius = r
            setStroke((1.5f * d).toInt().coerceAtLeast(2), Color.parseColor("#3D5C4A"))
        }
        binding.btnReset.setTextColor(ThemeUi.contrastText(Color.parseColor("#FFFEF8")))
    }

    private fun renderModeUi() {
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val fill = Color.parseColor("#FFFEF8")
        val inkGreen = Color.parseColor("#3D5C4A")
        binding.modeSegment.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 999 * d
            setStroke((1.5f * d).toInt().coerceAtLeast(2), inkGreen)
        }
        binding.modeDivider1.setBackgroundColor(inkGreen)
        binding.modeDivider2.setBackgroundColor(inkGreen)
        styleModeHalf(binding.btnModeStopwatch, mode == Mode.STOPWATCH, theme, d, left = true, right = false)
        styleModeHalf(binding.btnModeCountdown, mode == Mode.COUNTDOWN, theme, d, left = false, right = false)
        styleModeHalf(binding.btnModePomodoro, mode == Mode.POMODORO, theme, d, left = false, right = true)
    }

    private fun styleModeHalf(
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

    private fun switchMode(next: Mode, force: Boolean = false) {
        if (!force && mode == next) return
        stopTicker(keepProgress = false)
        mode = next
        when (mode) {
            Mode.STOPWATCH -> {
                elapsedMs = 0L
                binding.tvPhase.text = "正计时"
                binding.btnSkip.visibility = View.GONE
            }
            Mode.COUNTDOWN -> {
                remainingMs = countdownTotalMs
                binding.tvPhase.text = "倒计时"
                binding.btnSkip.visibility = View.GONE
            }
            Mode.POMODORO -> {
                phase = PomoPhase.WORK
                workDurationMs = WORK_MS
                remainingMs = workDurationMs
                binding.tvPhase.text = phaseLabel()
                binding.btnSkip.visibility = View.VISIBLE
            }
        }
        renderModeUi()
        renderPresets()
        renderTimer()
        refreshControls()
    }

    private fun renderPresets() {
        binding.presetRow.removeAllViews()
        val show = mode == Mode.COUNTDOWN || mode == Mode.POMODORO
        binding.presetScroll.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return

        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val items = if (mode == Mode.COUNTDOWN) {
            listOf(1 to "1 分", 5 to "5 分", 10 to "10 分", 15 to "15 分", 25 to "25 分", 45 to "45 分")
        } else {
            listOf(
                1 to "专注 1",
                25 to "专注 25",
                5 to "短休 5",
                15 to "长休 15"
            )
        }

        items.forEachIndexed { index, (minutes, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                typeface = ResourcesCompat.getFont(this@PomodoroActivity, R.font.patrick_hand)
                gravity = android.view.Gravity.CENTER
                setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                val targetWorkMs = minutes * 60_000L
                val selected = when (mode) {
                    Mode.COUNTDOWN -> countdownTotalMs == targetWorkMs
                    Mode.POMODORO -> when (index) {
                        0, 1 -> phase == PomoPhase.WORK && workDurationMs == targetWorkMs && !running
                        2 -> phase == PomoPhase.SHORT_BREAK
                        else -> phase == PomoPhase.LONG_BREAK
                    }
                    else -> false
                }
                background = GradientDrawable().apply {
                    setColor(if (selected) theme.accent else Color.parseColor("#FFFEF8"))
                    cornerRadius = 999 * d
                    setStroke((1.5f * d).toInt().coerceAtLeast(2), Color.parseColor("#3D5C4A"))
                }
                setTextColor(
                    if (selected) Color.parseColor("#FFFEF8")
                    else ThemeUi.contrastText(Color.parseColor("#FFFEF8"))
                )
                setOnClickListener {
                    if (running) {
                        Toast.makeText(this@PomodoroActivity, "计时中，请先暂停或重置", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (mode == Mode.COUNTDOWN) {
                        countdownTotalMs = minutes * 60_000L
                        remainingMs = countdownTotalMs
                    } else {
                        if (index >= 2 && !workCompletedInSet) {
                            Toast.makeText(
                                this@PomodoroActivity,
                                "请先完成专注阶段，休息不计入番茄数",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }
                        when (index) {
                            0, 1 -> {
                                phase = PomoPhase.WORK
                                workDurationMs = targetWorkMs
                                remainingMs = workDurationMs
                            }
                            2 -> {
                                phase = PomoPhase.SHORT_BREAK
                                remainingMs = SHORT_BREAK_MS
                            }
                            else -> {
                                phase = PomoPhase.LONG_BREAK
                                remainingMs = LONG_BREAK_MS
                            }
                        }
                        binding.tvPhase.text = phaseLabel()
                    }
                    renderPresets()
                    renderTimer()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * d).toInt() }
            binding.presetRow.addView(chip, lp)
        }
    }

    private fun toggleRun() {
        if (running) pauseTimer() else startTimer()
    }

    private fun startTimer() {
        if (mode != Mode.STOPWATCH && remainingMs <= 0L) {
            if (mode == Mode.COUNTDOWN) remainingMs = countdownTotalMs
            if (mode == Mode.POMODORO) remainingMs = phaseDuration(phase)
        }
        running = true
        tickBaseElapsedRealtime = SystemClock.elapsedRealtime()
        tickBaseElapsedMs = elapsedMs
        tickBaseRemainingMs = remainingMs
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        refreshControls()
    }

    private fun pauseTimer() {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        when (mode) {
            Mode.STOPWATCH -> elapsedMs = tickBaseElapsedMs + (now - tickBaseElapsedRealtime)
            Mode.COUNTDOWN, Mode.POMODORO ->
                remainingMs = (tickBaseRemainingMs - (now - tickBaseElapsedRealtime)).coerceAtLeast(0L)
        }
        if (mode != Mode.STOPWATCH && remainingMs <= 0L) {
            onTimerFinished()
            return
        }
        stopTicker(keepProgress = true)
        renderTimer()
        refreshControls()
    }

    private fun stopTicker(keepProgress: Boolean) {
        handler.removeCallbacks(ticker)
        running = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!keepProgress) {
            // no-op; caller may reset values
        }
    }

    private fun resetTimer() {
        stopTicker(keepProgress = false)
        when (mode) {
            Mode.STOPWATCH -> elapsedMs = 0L
            Mode.COUNTDOWN -> remainingMs = countdownTotalMs
            Mode.POMODORO -> {
                phase = PomoPhase.WORK
                workDurationMs = WORK_MS
                remainingMs = workDurationMs
                binding.tvPhase.text = phaseLabel()
            }
        }
        renderPresets()
        renderTimer()
        refreshControls()
    }

    private fun syncRemainingTimeIfRunning() {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        when (mode) {
            Mode.STOPWATCH -> elapsedMs = tickBaseElapsedMs + (now - tickBaseElapsedRealtime)
            Mode.COUNTDOWN, Mode.POMODORO ->
                remainingMs = (tickBaseRemainingMs - (now - tickBaseElapsedRealtime)).coerceAtLeast(0L)
            else -> Unit
        }
    }

    private fun skipPhase() {
        if (mode != Mode.POMODORO) return
        syncRemainingTimeIfRunning()
        stopTicker(keepProgress = false)
        when (phase) {
            PomoPhase.WORK -> {
                if (remainingMs <= 0L) {
                    onTimerFinished()
                } else if (remainingMs < workDurationMs - 2_000L) {
                    recordWorkComplete()
                    Toast.makeText(this, "番茄完成！休息一下", Toast.LENGTH_SHORT).show()
                    goToBreak(countAsCompleted = true)
                } else {
                    Toast.makeText(this, "请先开始专注计时，完成后才会计入番茄", Toast.LENGTH_SHORT).show()
                }
            }
            PomoPhase.SHORT_BREAK, PomoPhase.LONG_BREAK -> goToWork()
        }
    }

    private fun recordWorkComplete() {
        val elapsedWorkMs = (workDurationMs - remainingMs).coerceIn(0L, workDurationMs)
        val minutes = (elapsedWorkMs / 60_000L).toInt().coerceAtLeast(1)
        completedToday += 1
        focusMinutesToday += minutes
        cycleInSet += 1
        workCompletedInSet = true
        saveStats()
        refreshStats()
    }

    private fun onTimerFinished() {
        if (handlingFinish) return
        handlingFinish = true
        try {
            stopTicker(keepProgress = false)
            when (mode) {
            Mode.STOPWATCH -> Unit
            Mode.COUNTDOWN -> {
                remainingMs = 0L
                binding.tvPhase.text = "时间到"
                Toast.makeText(this, "倒计时结束", Toast.LENGTH_SHORT).show()
            }
            Mode.POMODORO -> {
                if (phase == PomoPhase.WORK) {
                    remainingMs = 0L
                    recordWorkComplete()
                    Toast.makeText(this, "番茄完成！休息一下", Toast.LENGTH_SHORT).show()
                    goToBreak(countAsCompleted = true)
                } else {
                    Toast.makeText(this, "休息结束，继续专注", Toast.LENGTH_SHORT).show()
                    goToWork()
                }
                return
            }
        }
        renderTimer()
        refreshControls()
        } finally {
            handlingFinish = false
        }
    }

    private fun goToBreak(countAsCompleted: Boolean) {
        val n = if (countAsCompleted) cycleInSet else cycleInSet + 1
        phase = if (n > 0 && n % 4 == 0) PomoPhase.LONG_BREAK else PomoPhase.SHORT_BREAK
        remainingMs = phaseDuration(phase)
        binding.tvPhase.text = phaseLabel()
        renderPresets()
        renderTimer()
        refreshControls()
    }

    private fun goToWork() {
        phase = PomoPhase.WORK
        remainingMs = workDurationMs
        workCompletedInSet = false
        binding.tvPhase.text = phaseLabel()
        renderPresets()
        renderTimer()
        refreshControls()
    }

    private fun phaseLabel(): String = when (phase) {
        PomoPhase.WORK -> "专注中 · 第 ${completedToday + 1} 个番茄"
        PomoPhase.SHORT_BREAK -> "短休息"
        PomoPhase.LONG_BREAK -> "长休息"
    }

    private fun phaseDuration(p: PomoPhase): Long = when (p) {
        PomoPhase.WORK -> workDurationMs
        PomoPhase.SHORT_BREAK -> SHORT_BREAK_MS
        PomoPhase.LONG_BREAK -> LONG_BREAK_MS
    }

    private fun renderTimer() {
        val ms = when (mode) {
            Mode.STOPWATCH -> elapsedMs
            Mode.COUNTDOWN, Mode.POMODORO -> remainingMs
        }
        binding.tvTimer.text = formatMs(ms)
        if (mode == Mode.POMODORO) binding.tvPhase.text = phaseLabel()
    }

    private fun refreshControls() {
        binding.btnPrimary.text = if (running) "暂停" else "开始"
        binding.btnSkip.visibility = if (mode == Mode.POMODORO) View.VISIBLE else View.GONE
    }

    private fun refreshStats() {
        binding.tvStats.text = "已完成 $completedToday 个番茄 · 累计 $focusMinutesToday 分钟"
    }

    private fun loadStats() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val day = prefs.getString(KEY_DAY, "")
        val today = todayKey()
        if (day != today) {
            completedToday = 0
            focusMinutesToday = 0
            prefs.edit().putString(KEY_DAY, today).putInt(KEY_COUNT, 0).putInt(KEY_MINUTES, 0).apply()
        } else {
            completedToday = prefs.getInt(KEY_COUNT, 0)
            focusMinutesToday = prefs.getInt(KEY_MINUTES, 0)
        }
    }

    private fun saveStats() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_DAY, todayKey())
            .putInt(KEY_COUNT, completedToday)
            .putInt(KEY_MINUTES, focusMinutesToday)
            .commit()
    }

    private fun reloadStatsFromPrefs() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val day = prefs.getString(KEY_DAY, "")
        val today = todayKey()
        if (day == today) {
            completedToday = prefs.getInt(KEY_COUNT, 0)
            focusMinutesToday = prefs.getInt(KEY_MINUTES, 0)
        }
    }

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    private fun formatMs(ms: Long): String {
        val totalSec = if (ms <= 0L) 0L else (ms + 999L) / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    companion object {
        private const val PREFS = "notesketch_pomodoro"
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
        private const val KEY_MINUTES = "minutes"
        private const val WORK_MS = 25 * 60_000L
        private const val SHORT_BREAK_MS = 5 * 60_000L
        private const val LONG_BREAK_MS = 15 * 60_000L
    }
}
