package com.example.notesketch

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.notesketch.databinding.ActivityToolsBinding

class ToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityToolsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnLedger.setOnClickListener {
            startActivity(Intent(this, LedgerActivity::class.java))
        }
        binding.btnMood.setOnClickListener {
            startActivity(Intent(this, MoodDiaryActivity::class.java))
        }
        binding.btnPomodoro.setOnClickListener {
            startActivity(Intent(this, PomodoroActivity::class.java))
        }
        applyChrome()
    }

    override fun onResume() {
        super.onResume()
        applyChrome()
    }

    private fun applyChrome() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        val panel = Color.parseColor("#FFFEF8")
        val panelInk = Color.parseColor("#3D3428")
        val panelMuted = Color.parseColor("#6F6256")
        val d = resources.displayMetrics.density
        ThemeUi.colorTexts(theme.ink, binding.tvHeader, binding.sectionTitle)
        ThemeUi.colorTexts(theme.muted, binding.btnBack, binding.sectionSub)
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
        styleNavItem(binding.btnLedger, binding.tvLedgerTitle, binding.tvLedgerSub, panel, panelInk, panelMuted, d)
        styleNavItem(binding.btnMood, binding.tvMoodTitle, binding.tvMoodSub, panel, panelInk, panelMuted, d)
        styleNavItem(binding.btnPomodoro, binding.tvPomodoroTitle, binding.tvPomodoroSub, panel, panelInk, panelMuted, d)
        listOf(binding.btnLedger, binding.btnMood, binding.btnPomodoro).forEach { row ->
            row.getChildAt(row.childCount - 1)?.let { chevron ->
                if (chevron is TextView) chevron.setTextColor(panelMuted)
            }
        }
    }

    private fun styleNavItem(
        row: LinearLayout,
        title: TextView,
        sub: TextView,
        panel: Int,
        panelInk: Int,
        panelMuted: Int,
        d: Float
    ) {
        row.background = GradientDrawable().apply {
            setColor(panel)
            cornerRadius = 12 * d
            setStroke((1.5f * d).toInt().coerceAtLeast(2), Color.parseColor("#2E3D3428"))
        }
        title.setTextColor(panelInk)
        sub.setTextColor(panelMuted)
    }
}
