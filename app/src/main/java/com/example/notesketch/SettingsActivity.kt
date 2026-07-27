package com.example.notesketch

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.notesketch.databinding.ActivitySettingsBinding
import com.example.notesketch.databinding.DialogCustomColorBinding

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
        binding.labelTheme.text = "点选应用 · 长按删除任意颜色 · 点「自定义」色谱选色"
    }

    private fun renderThemes() {
        binding.themeRow.removeAllViews()
        val current = UiPrefs.theme(this).id
        UiPrefs.allThemes(this).forEach { theme ->
            val chip = ThemeUi.themeChip(this, theme, theme.id == current)
            if (theme.custom) {
                val fill = if (theme.id == current) theme.accent else theme.bg
                chip.setTextColor(ThemeUi.contrastText(fill))
                chip.background = GradientDrawable().apply {
                    setColor(fill)
                    setStroke(
                        (1.5f * resources.displayMetrics.density).toInt().coerceAtLeast(1),
                        if (theme.id == current) theme.accent else theme.line
                    )
                }
            }
            chip.setOnClickListener {
                UiPrefs.setTheme(this, theme.id)
                renderThemes()
                renderPaperTypes()
                applyChrome()
                Toast.makeText(this, "背景「${theme.label}」", Toast.LENGTH_SHORT).show()
            }
            chip.setOnLongClickListener {
                confirmDeleteTheme(theme)
                true
            }
            binding.themeRow.addView(chip)
        }
        binding.themeRow.addView(makeAddCustomChip())
    }

    private fun makeAddCustomChip() =
        ThemeUi.themeChip(
            this,
            ThemePalette(
                id = "_add",
                label = "+ 自定义",
                bg = Color.parseColor("#F6F0E4"),
                surface = Color.parseColor("#F8F3E9"),
                ink = Color.parseColor("#3D3428"),
                muted = Color.parseColor("#7A6F62"),
                line = Color.parseColor("#DDD5C8"),
                accent = Color.parseColor("#7AAB9E"),
                due = Color.parseColor("#B03A32")
            ),
            selected = false
        ).also { chip ->
            chip.setOnClickListener { openCustomColorDialog() }
        }

    private fun openCustomColorDialog() {
        val dialogBinding = DialogCustomColorBinding.inflate(LayoutInflater.from(this))
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogBinding.root.background = GradientDrawable().apply {
            setColor(UiPrefs.theme(this@SettingsActivity).surface)
            setStroke(
                (2 * resources.displayMetrics.density).toInt().coerceAtLeast(1),
                0x403D3428
            )
        }

        val initial = UiPrefs.theme(this).bg
        dialogBinding.colorPicker.setColor(initial)
        dialogBinding.previewSwatch.setBackgroundColor(initial)
        dialogBinding.colorPicker.onColorChanged = { color ->
            dialogBinding.previewSwatch.setBackgroundColor(color)
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSaveColor.setOnClickListener {
            val color = dialogBinding.colorPicker.currentColor()
            val name = dialogBinding.etColorName.text?.toString().orEmpty()
            val added = UiPrefs.addCustomTheme(this, name, color)
            if (added == null) {
                Toast.makeText(this, "自定义色最多 12 个", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            UiPrefs.setTheme(this, added.id)
            dialog.dismiss()
            renderThemes()
            renderPaperTypes()
            applyChrome()
            Toast.makeText(this, "已添加「${added.label}」", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun confirmDeleteTheme(theme: ThemePalette) {
        val kind = if (theme.custom) "自定义色" else "预设色"
        AlertDialog.Builder(this)
            .setTitle("删除$kind")
            .setMessage("删除「${theme.label}」？")
            .setPositiveButton("删除") { _, _ ->
                if (!UiPrefs.removeTheme(this, theme.id)) {
                    Toast.makeText(this, "至少保留一种颜色", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                renderThemes()
                renderPaperTypes()
                applyChrome()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
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
