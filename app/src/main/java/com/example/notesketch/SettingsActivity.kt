package com.example.notesketch

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
        bindControls()
        applyChrome()
        renderThemes()
    }

    private fun bindControls() {
        binding.seekOpacity.max = 100
        binding.seekOpacity.progress = UiPrefs.contentOpacity(this)
        binding.seekSeaHeight.progress = UiPrefs.seaHeight(this)
        binding.seekSeaAmp.progress = UiPrefs.seaAmp(this)
        binding.seekShell.progress = UiPrefs.shellFreq(this)
        refreshValues()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || seekBar == null) return
                when (seekBar.id) {
                    R.id.seekOpacity -> {
                        val v = progress.coerceIn(20, 100)
                        if (progress < 20) binding.seekOpacity.progress = 20
                        UiPrefs.setContentOpacity(this@SettingsActivity, v)
                    }
                    R.id.seekSeaHeight -> UiPrefs.setSeaHeight(this@SettingsActivity, progress)
                    R.id.seekSeaAmp -> UiPrefs.setSeaAmp(this@SettingsActivity, progress)
                    R.id.seekShell -> UiPrefs.setShellFreq(this@SettingsActivity, progress)
                }
                refreshValues()
                livePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        binding.seekOpacity.setOnSeekBarChangeListener(listener)
        binding.seekSeaHeight.setOnSeekBarChangeListener(listener)
        binding.seekSeaAmp.setOnSeekBarChangeListener(listener)
        binding.seekShell.setOnSeekBarChangeListener(listener)
    }

    private fun refreshValues() {
        binding.valOpacity.text = "${UiPrefs.contentOpacity(this)}%"
        binding.valSeaHeight.text = "${UiPrefs.seaHeight(this)}"
        binding.valSeaAmp.text = "${UiPrefs.seaAmp(this)}"
        binding.valShell.text = "${UiPrefs.shellFreq(this)}"
    }

    private fun livePreview() {
        val theme = UiPrefs.theme(this)
        binding.seaScene.applyConfig(
            theme,
            UiPrefs.seaHeight(this),
            UiPrefs.seaAmp(this),
            UiPrefs.shellFreq(this)
        )
        applyChrome()
    }

    private fun applyChrome() {
        val theme = UiPrefs.theme(this)
        val opacity = UiPrefs.contentOpacity(this)
        ThemeUi.applyWindow(this, theme)
        binding.root.setBackgroundColor(theme.bg)
        ThemeUi.setPanel(binding.contentPanel, theme, opacity)
        ThemeUi.colorTexts(
            theme.ink,
            binding.tvHeader,
            binding.sectionLook,
            binding.sectionSea
        )
        ThemeUi.colorTexts(
            theme.muted,
            binding.btnBack,
            binding.labelOpacity,
            binding.labelTheme,
            binding.labelSeaHeight,
            binding.labelSeaAmp,
            binding.labelShell,
                binding.hintShell,
                binding.hintOpacity,
                binding.valOpacity,
            binding.valSeaHeight,
            binding.valSeaAmp,
            binding.valShell
        )
        ThemeUi.colorLines(theme.line, binding.headerLine)
        val accent = android.content.res.ColorStateList.valueOf(theme.accent)
        binding.seekOpacity.progressTintList = accent
        binding.seekSeaHeight.progressTintList = accent
        binding.seekSeaAmp.progressTintList = accent
        binding.seekShell.progressTintList = accent
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
