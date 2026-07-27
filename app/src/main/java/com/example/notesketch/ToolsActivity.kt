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
        ThemeUi.colorTexts(theme.ink, binding.tvLedgerLabel, binding.tvMoodLabel)
        binding.tvHeader.setTextColor(Color.parseColor("#3D2818"))
        ThemeUi.colorTexts(Color.parseColor("#3D2818"), binding.btnBack)
    }
}
