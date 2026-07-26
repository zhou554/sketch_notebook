package com.example.notesketch

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.notesketch.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        applyChrome()
        renderThemes()
    }

    private fun livePreview() {
        applyChrome()
    }

    private fun applyChrome() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyWindow(this, theme)
        binding.root.setBackgroundColor(theme.bg)
        binding.paperBg.paperColor = theme.bg
        binding.paperBg.gridColor = theme.line
        binding.contentPanel.setBackgroundColor(Color.TRANSPARENT)
        ThemeUi.colorTexts(theme.ink, binding.tvHeader, binding.sectionLook)
        ThemeUi.colorTexts(
            theme.muted,
            binding.btnBack,
            binding.labelTheme,
            binding.hintOpacity
        )
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
    }

    private fun renderThemes() {
        binding.themeRow.removeAllViews()
        val current = UiPrefs.theme(this).id
        UiPrefs.themes.forEach { theme ->
            val chip = ThemeUi.themeChip(this, theme, theme.id == current)
            chip.setOnClickListener {
                UiPrefs.setTheme(this, theme.id)
                renderThemes()
                livePreview()
                Toast.makeText(this, "已切换「${theme.label}」", Toast.LENGTH_SHORT).show()
            }
            binding.themeRow.addView(chip)
        }
    }
}
