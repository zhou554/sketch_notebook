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
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.notesketch.databinding.ActivityPomodoroBinding
import java.util.Locale

class PomodoroActivity : AppCompatActivity() {

    private enum class Mode { STOPWATCH, COUNTDOWN, POMODORO }
    private enum class PomoPhase { WORK, SHORT_BREAK, LONG_BREAK }
    private enum class PomoPresetKind { WORK, SHORT_BREAK, LONG_BREAK, CUSTOM_WORK, CUSTOM_BREAK }

    private data class PomoPreset(val kind: PomoPresetKind, val minutes: Int?, val label: String)

    private lateinit var binding: ActivityPomodoroBinding

    private var mode = Mode.POMODORO
    private var running = false
    private var elapsedMs = 0L
    private var remainingMs = DEFAULT_WORK_MS
    private var workDurationMs = DEFAULT_WORK_MS
    private var shortBreakDurationMs = DEFAULT_SHORT_BREAK_MS
    private var longBreakDurationMs = DEFAULT_LONG_BREAK_MS
    private var countdownTotalMs = 5 * 60_000L
    private var phase = PomoPhase.WORK
    private var completedToday = 0
    private var focusMinutesToday = 0
    private var handlingFinish = false

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
        loadDurations()

        binding.btnBackRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnModeStopwatch.setOnClickListener { switchMode(Mode.STOPWATCH) }
        binding.btnModeCountdown.setOnClickListener { switchMode(Mode.COUNTDOWN) }
        binding.btnModePomodoro.setOnClickListener { switchMode(Mode.POMODORO) }
        binding.btnPrimary.setOnClickListener { toggleRun() }
        binding.btnReset.setOnClickListener { resetTimer() }
        binding.btnStatsEntry.setOnClickListener {
            startActivity(PomodoroStatsActivity.intent(this))
        }

        applyUi()
        switchMode(Mode.POMODORO, force = true)
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        reloadStatsFromPrefs()
        loadDurations()
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
        ThemeUi.colorTexts(theme.muted, binding.btnBack)
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
        binding.btnStatsEntry.background = GradientDrawable().apply {
            setColor(card)
            setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
        }
        ThemeUi.colorTexts(ThemeUi.contrastText(card), binding.tvStatsTitle, binding.tvStats, binding.btnStatsEntry)
        stylePrimaryButtons()
        renderModeUi()
    }

    private fun stylePrimaryButtons() {
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val r = 999 * d
        binding.btnPrimary.background = ThemeUi.primaryButtonDrawable(theme, r)
        binding.btnPrimary.setTextColor(ThemeUi.primaryOnAccentText(theme))
        binding.btnReset.background = ThemeUi.outlinePanelDrawable(theme, r, d)
        binding.btnReset.setTextColor(ThemeUi.contrastText(ThemeUi.lightPanelFill))
    }

    private fun renderModeUi() {
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val border = ThemeUi.accentBorder(theme)
        binding.modeSegment.background = ThemeUi.outlinePanelDrawable(theme, 999 * d, d)
        binding.modeDivider1.setBackgroundColor(border)
        binding.modeDivider2.setBackgroundColor(border)
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
        tv.setTextColor(
            if (on) ThemeUi.primaryOnAccentText(theme)
            else ThemeUi.contrastText(ThemeUi.lightPanelFill)
        )
        val r = 999 * d
        val radii = when {
            left -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            right -> floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            else -> FloatArray(8) { 0f }
        }
        tv.background = if (on) {
            ThemeUi.segmentSelectedDrawable(theme, radii)
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
            }
            Mode.COUNTDOWN -> {
                remainingMs = countdownTotalMs
                binding.tvPhase.text = "倒计时"
            }
            Mode.POMODORO -> {
                phase = PomoPhase.WORK
                remainingMs = workDurationMs
                binding.tvPhase.text = phaseLabel()
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
            listOf(
                PomoPreset(PomoPresetKind.WORK, 1, "1 分"),
                PomoPreset(PomoPresetKind.WORK, 5, "5 分"),
                PomoPreset(PomoPresetKind.WORK, 10, "10 分"),
                PomoPreset(PomoPresetKind.WORK, 15, "15 分"),
                PomoPreset(PomoPresetKind.WORK, 25, "25 分"),
                PomoPreset(PomoPresetKind.WORK, 45, "45 分")
            )
        } else {
            listOf(
                PomoPreset(PomoPresetKind.WORK, 25, "专注 25"),
                PomoPreset(PomoPresetKind.SHORT_BREAK, 5, "短休 5"),
                PomoPreset(PomoPresetKind.LONG_BREAK, 15, "长休 15"),
                PomoPreset(PomoPresetKind.CUSTOM_WORK, null, customWorkChipLabel()),
                PomoPreset(PomoPresetKind.CUSTOM_BREAK, null, customBreakChipLabel())
            )
        }

        items.forEach { preset ->
            val minutes = preset.minutes
            val chip = TextView(this).apply {
                text = preset.label
                textSize = 14f
                typeface = ResourcesCompat.getFont(this@PomodoroActivity, R.font.patrick_hand)
                gravity = android.view.Gravity.CENTER
                setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                val targetWorkMs = minutes?.times(60_000L)
                val selected = when (mode) {
                    Mode.COUNTDOWN -> targetWorkMs != null && countdownTotalMs == targetWorkMs
                    Mode.POMODORO -> isPomoPresetSelected(preset)
                    else -> false
                }
                background = ThemeUi.chipDrawable(theme, selected, 999 * d, d)
                setTextColor(
                    if (selected) ThemeUi.primaryOnAccentText(theme)
                    else ThemeUi.contrastText(ThemeUi.lightPanelFill)
                )
                setOnClickListener {
                    if (running) {
                        Toast.makeText(this@PomodoroActivity, "计时中，请先暂停或重置", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    when (mode) {
                        Mode.COUNTDOWN -> {
                            if (minutes == null) return@setOnClickListener
                            countdownTotalMs = minutes * 60_000L
                            remainingMs = countdownTotalMs
                            renderPresets()
                            renderTimer()
                        }
                        Mode.POMODORO -> applyPomoPreset(preset)
                        else -> Unit
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * d).toInt() }
            binding.presetRow.addView(chip, lp)
        }
    }

    private fun isPomoPresetSelected(preset: PomoPreset): Boolean = when (preset.kind) {
        PomoPresetKind.WORK -> {
            val m = preset.minutes
            m != null && phase == PomoPhase.WORK && workDurationMs == m * 60_000L && !running
        }
        PomoPresetKind.SHORT_BREAK ->
            phase == PomoPhase.SHORT_BREAK && shortBreakDurationMs == (preset.minutes ?: 0) * 60_000L
        PomoPresetKind.LONG_BREAK ->
            phase == PomoPhase.LONG_BREAK && longBreakDurationMs == (preset.minutes ?: 0) * 60_000L
        PomoPresetKind.CUSTOM_WORK ->
            phase == PomoPhase.WORK && !isPresetWorkMinutes(workMinutes()) && !running
        PomoPresetKind.CUSTOM_BREAK ->
            (phase == PomoPhase.SHORT_BREAK && !isPresetShortBreakMinutes(shortBreakMinutes())) ||
                (phase == PomoPhase.LONG_BREAK && !isPresetLongBreakMinutes(longBreakMinutes()))
    }

    private fun applyPomoPreset(preset: PomoPreset) {
        when (preset.kind) {
            PomoPresetKind.CUSTOM_WORK -> showCustomWorkDialog()
            PomoPresetKind.CUSTOM_BREAK -> showCustomBreakDialog()
            PomoPresetKind.WORK -> {
                val minutes = preset.minutes ?: return
                phase = PomoPhase.WORK
                workDurationMs = minutes * 60_000L
                remainingMs = workDurationMs
                binding.tvPhase.text = phaseLabel()
                saveDurations()
                renderPresets()
                renderTimer()
            }
            PomoPresetKind.SHORT_BREAK, PomoPresetKind.LONG_BREAK -> {
                if (preset.kind == PomoPresetKind.SHORT_BREAK) {
                    phase = PomoPhase.SHORT_BREAK
                    shortBreakDurationMs = (preset.minutes ?: 5) * 60_000L
                    remainingMs = shortBreakDurationMs
                } else {
                    phase = PomoPhase.LONG_BREAK
                    longBreakDurationMs = (preset.minutes ?: 15) * 60_000L
                    remainingMs = longBreakDurationMs
                }
                binding.tvPhase.text = phaseLabel()
                saveDurations()
                renderPresets()
                renderTimer()
            }
        }
    }

    private fun customWorkChipLabel(): String {
        val m = workMinutes()
        return if (isPresetWorkMinutes(m)) "自定义专注" else "专注 $m"
    }

    private fun customBreakChipLabel(): String {
        val short = shortBreakMinutes()
        val long = longBreakMinutes()
        return when {
            !isPresetShortBreakMinutes(short) && !isPresetLongBreakMinutes(long) ->
                "短休 $short · 长休 $long"
            !isPresetShortBreakMinutes(short) -> "短休 $short"
            !isPresetLongBreakMinutes(long) -> "长休 $long"
            else -> "自定义休息"
        }
    }

    private fun workMinutes(): Int = (workDurationMs / 60_000L).toInt().coerceAtLeast(1)
    private fun shortBreakMinutes(): Int = (shortBreakDurationMs / 60_000L).toInt().coerceAtLeast(1)
    private fun longBreakMinutes(): Int = (longBreakDurationMs / 60_000L).toInt().coerceAtLeast(1)

    private fun isPresetWorkMinutes(minutes: Int): Boolean = minutes == 25
    private fun isPresetShortBreakMinutes(minutes: Int): Boolean = minutes == 5
    private fun isPresetLongBreakMinutes(minutes: Int): Boolean = minutes == 15

    private fun showCustomWorkDialog() {
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 180
            value = workMinutes().coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
        }
        AlertDialog.Builder(this)
            .setTitle("自定义专注时长（分钟）")
            .setView(picker)
            .setPositiveButton("确定") { _, _ ->
                phase = PomoPhase.WORK
                workDurationMs = picker.value * 60_000L
                remainingMs = workDurationMs
                binding.tvPhase.text = phaseLabel()
                saveDurations()
                renderPresets()
                renderTimer()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCustomBreakDialog() {
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val typeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val shortRadio = RadioButton(this).apply {
            id = View.generateViewId()
            text = "短休"
            isChecked = phase != PomoPhase.LONG_BREAK
        }
        val longRadio = RadioButton(this).apply {
            id = View.generateViewId()
            text = "长休"
            isChecked = phase == PomoPhase.LONG_BREAK
        }
        typeGroup.addView(shortRadio)
        typeGroup.addView(longRadio)
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 60
            value = if (shortRadio.isChecked) shortBreakMinutes() else longBreakMinutes()
            wrapSelectorWheel = false
        }
        typeGroup.setOnCheckedChangeListener { _, checkedId ->
            picker.value = if (checkedId == shortRadio.id) {
                shortBreakMinutes()
            } else {
                longBreakMinutes()
            }.coerceIn(picker.minValue, picker.maxValue)
        }
        container.addView(typeGroup)
        container.addView(picker)

        AlertDialog.Builder(this)
            .setTitle("自定义休息时长（分钟）")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val minutes = picker.value
                val isShort = typeGroup.checkedRadioButtonId == shortRadio.id
                if (isShort) {
                    shortBreakDurationMs = minutes * 60_000L
                    phase = PomoPhase.SHORT_BREAK
                    remainingMs = shortBreakDurationMs
                } else {
                    longBreakDurationMs = minutes * 60_000L
                    phase = PomoPhase.LONG_BREAK
                    remainingMs = longBreakDurationMs
                }
                binding.tvPhase.text = phaseLabel()
                saveDurations()
                renderPresets()
                renderTimer()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadDurations() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        workDurationMs = prefs.getInt(KEY_WORK_MIN, 25).coerceIn(1, 180) * 60_000L
        shortBreakDurationMs = prefs.getInt(KEY_SHORT_MIN, 5).coerceIn(1, 60) * 60_000L
        longBreakDurationMs = prefs.getInt(KEY_LONG_MIN, 15).coerceIn(1, 60) * 60_000L
        if (::binding.isInitialized && mode == Mode.POMODORO && !running) {
            remainingMs = phaseDuration(phase)
        }
    }

    private fun saveDurations() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_WORK_MIN, workMinutes())
            .putInt(KEY_SHORT_MIN, shortBreakMinutes())
            .putInt(KEY_LONG_MIN, longBreakMinutes())
            .apply()
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
                remainingMs = phaseDuration(phase)
                binding.tvPhase.text = phaseLabel()
            }
        }
        renderPresets()
        renderTimer()
        refreshControls()
    }

    private fun recordWorkComplete() {
        val elapsedWorkMs = (workDurationMs - remainingMs).coerceIn(0L, workDurationMs)
        val minutes = (elapsedWorkMs / 60_000L).toInt().coerceAtLeast(1)
        completedToday += 1
        focusMinutesToday += minutes
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
                    Toast.makeText(this, "专注完成", Toast.LENGTH_SHORT).show()
                    remainingMs = workDurationMs
                } else {
                    Toast.makeText(this, "休息结束", Toast.LENGTH_SHORT).show()
                    remainingMs = phaseDuration(phase)
                }
                binding.tvPhase.text = phaseLabel()
                renderPresets()
                renderTimer()
                refreshControls()
                return
            }
        }
        renderTimer()
        refreshControls()
        } finally {
            handlingFinish = false
        }
    }

    private fun phaseLabel(): String = when (phase) {
        PomoPhase.WORK -> "专注中 · 第 ${completedToday + 1} 个番茄"
        PomoPhase.SHORT_BREAK -> "短休息"
        PomoPhase.LONG_BREAK -> "长休息"
    }

    private fun phaseDuration(p: PomoPhase): Long = when (p) {
        PomoPhase.WORK -> workDurationMs
        PomoPhase.SHORT_BREAK -> shortBreakDurationMs
        PomoPhase.LONG_BREAK -> longBreakDurationMs
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
    }

    private fun refreshStats() {
        binding.tvStats.text = "已完成 $completedToday 个番茄 · 累计 $focusMinutesToday 分钟"
    }

    private fun loadStats() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val day = prefs.getString(KEY_DAY, "")
        val today = todayKey()
        if (day != today) {
            if (!day.isNullOrBlank()) {
                PomodoroStatsStore.archiveDay(
                    this,
                    day,
                    prefs.getInt(KEY_COUNT, 0),
                    prefs.getInt(KEY_MINUTES, 0)
                )
            }
            completedToday = 0
            focusMinutesToday = 0
            prefs.edit().putString(KEY_DAY, today).putInt(KEY_COUNT, 0).putInt(KEY_MINUTES, 0).apply()
        } else {
            completedToday = prefs.getInt(KEY_COUNT, 0)
            focusMinutesToday = prefs.getInt(KEY_MINUTES, 0)
        }
        PomodoroStatsStore.syncToday(this, completedToday, focusMinutesToday)
    }

    private fun saveStats() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_DAY, todayKey())
            .putInt(KEY_COUNT, completedToday)
            .putInt(KEY_MINUTES, focusMinutesToday)
            .commit()
        PomodoroStatsStore.syncToday(this, completedToday, focusMinutesToday)
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
        private const val KEY_WORK_MIN = "work_min"
        private const val KEY_SHORT_MIN = "short_min"
        private const val KEY_LONG_MIN = "long_min"
        private const val DEFAULT_WORK_MS = 25 * 60_000L
        private const val DEFAULT_SHORT_BREAK_MS = 5 * 60_000L
        private const val DEFAULT_LONG_BREAK_MS = 15 * 60_000L
    }
}
