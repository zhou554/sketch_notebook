package com.example.notesketch

import android.graphics.Color
import android.os.Bundle
import android.widget.SeekBar
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
        setupBrightness()
        applyChrome()
        renderThemes()
        renderPaperTypes()
    }

    private fun setupBrightness() {
        // SeekBar 0..70 maps to brightness 30..100
        val brightness = UiPrefs.brightness(this)
        binding.seekBrightness.progress = (brightness - 30).coerceIn(0, 70)
        binding.valBrightness.text = brightness.toString()
        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 30
                binding.valBrightness.text = value.toString()
                if (fromUser) {
                    UiPrefs.setBrightness(this@SettingsActivity, value)
                    ThemeUi.applyBrightness(this@SettingsActivity)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun applyChrome() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        binding.contentPanel.setBackgroundColor(Color.TRANSPARENT)
        ThemeUi.colorTexts(
            theme.ink,
            binding.tvHeader,
            binding.sectionBrightness,
            binding.sectionLook,
            binding.sectionPaper,
            binding.valBrightness
        )
        ThemeUi.colorTexts(
            theme.muted,
            binding.btnBack,
            binding.labelBrightness,
            binding.labelTheme,
            binding.labelPaper,
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
                renderPaperTypes()
                applyChrome()
                Toast.makeText(this, "背景「${theme.label}」", Toast.LENGTH_SHORT).show()
            }
            binding.themeRow.addView(chip)
        }
    }

    private fun renderPaperTypes() {
        binding.paperTypeRow.removeAllViews()
        val theme = UiPrefs.theme(this)
        val current = UiPrefs.paperType(this)
        UiPrefs.paperTypes.forEach { pattern ->
            val chip = ThemeUi.patternChip(this, pattern, pattern == current, theme)
            chip.setOnClickListener {
                UiPrefs.setPaperType(this, pattern)
                renderPaperTypes()
                applyChrome()
                Toast.makeText(this, "背景类型「${pattern.label}」", Toast.LENGTH_SHORT).show()
            }
            binding.paperTypeRow.addView(chip)
        }
    }
}
