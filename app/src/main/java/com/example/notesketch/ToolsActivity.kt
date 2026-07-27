package com.example.notesketch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.notesketch.databinding.ActivityToolsBinding

class ToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityToolsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnLedger.setOnClickListener {
            startActivity(Intent(this, LedgerActivity::class.java))
        }
        binding.btnMood.setOnClickListener {
            startActivity(Intent(this, MoodDiaryActivity::class.java))
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
        // 图标文字画在固定浅色的 tools_board 手账图上，须用固定深色而非 theme.ink（深色主题下是浅色）
        val boardInk = Color.parseColor("#3D2818")
        ThemeUi.colorTexts(boardInk, binding.tvLedgerLabel, binding.tvMoodLabel)
        binding.tvHeader.setTextColor(boardInk)
        ThemeUi.colorTexts(boardInk, binding.btnBack)
    }
}
