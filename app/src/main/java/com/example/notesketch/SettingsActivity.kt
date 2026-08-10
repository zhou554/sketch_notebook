package com.example.notesketch

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.databinding.ActivitySettingsBinding
import com.example.notesketch.databinding.DialogCustomColorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BackupManager.writeToUri(this@SettingsActivity, uri)
                }
                val summary = withContext(Dispatchers.IO) {
                    BackupManager.exportSummary(this@SettingsActivity)
                }
                showBackupStatus(
                    "已导出 ${summary.noteCount} 条便签、${summary.ledgerCount} 条账本、${summary.moodCount} 条心情（约 ${BackupManager.formatBytes(summary.approxBytes)}）"
                )
            } catch (e: Exception) {
                showBackupStatus(e.message ?: "导出失败", true)
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    BackupManager.readFromUri(this@SettingsActivity, uri)
                }
                BackupManager.validate(doc)
                val summary = BackupManager.importSummary(doc)
                val sizeHint = BackupManager.formatBytes(summary.approxBytes)
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("从备份恢复")
                    .setMessage(
                        "备份含 ${summary.noteCount} 条便签、${summary.ledgerCount} 条账本、${summary.moodCount} 条心情（约 $sizeHint）。\n\n确定 = 与本机合并\n取消 = 选择覆盖导入"
                    )
                    .setPositiveButton("合并") { _, _ ->
                        runImport(doc, BackupManager.ImportMode.MERGE)
                    }
                    .setNegativeButton("覆盖") { _, _ ->
                        confirmReplaceImport(doc)
                    }
                    .setNeutralButton("取消") { _, _ ->
                        showBackupStatus("已取消导入")
                    }
                    .show()
            } catch (e: Exception) {
                showBackupStatus(e.message ?: "导入失败", true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.backupExportBtn.setOnClickListener {
            exportLauncher.launch(BackupManager.defaultExportFileName())
        }
        binding.backupImportBtn.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }
        setupBrightness()
        applyChrome()
        renderThemes()
        renderPaperTypes()
        styleBackupButtons()
    }

    private fun confirmReplaceImport(doc: org.json.JSONObject) {
        AlertDialog.Builder(this)
            .setTitle("覆盖导入")
            .setMessage("用备份完全替换本机数据？\n本机便签、设置、账本与心情将被覆盖，建议先导出一份当前备份。")
            .setPositiveButton("覆盖") { _, _ ->
                runImport(doc, BackupManager.ImportMode.REPLACE)
            }
            .setNegativeButton("取消") { _, _ ->
                showBackupStatus("已取消导入")
            }
            .show()
    }

    private fun runImport(doc: org.json.JSONObject, mode: BackupManager.ImportMode) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BackupManager.applyImport(this@SettingsActivity, doc, mode)
                }
                applyChrome()
                renderThemes()
                renderPaperTypes()
                styleBackupButtons()
                val notes = withContext(Dispatchers.IO) {
                    AppDatabase.get(this@SettingsActivity).noteDao().getAllOnce().size
                }
                val ledger = withContext(Dispatchers.IO) {
                    AppDatabase.get(this@SettingsActivity).ledgerDao().getAllOnce().size
                }
                val mood = withContext(Dispatchers.IO) {
                    AppDatabase.get(this@SettingsActivity).moodDao().getAllOnce().size
                }
                val msg = if (mode == BackupManager.ImportMode.REPLACE) {
                    "已覆盖恢复：$notes 条便签、$ledger 条账本、$mood 条心情"
                } else {
                    "已合并：$notes 条便签、$ledger 条账本、$mood 条心情"
                }
                showBackupStatus(msg)
            } catch (e: Exception) {
                showBackupStatus(e.message ?: "导入失败", true)
            }
        }
    }

    private fun showBackupStatus(msg: String, isError: Boolean = false) {
        binding.backupStatus.visibility = if (msg.isBlank()) View.GONE else View.VISIBLE
        binding.backupStatus.text = msg
        val theme = UiPrefs.theme(this)
        binding.backupStatus.setTextColor(
            if (isError) theme.due else ThemeUi.contrastMuted(ThemeUi.stickerPanelColor(theme))
        )
    }

    private fun styleBackupButtons() {
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        listOf(binding.backupExportBtn, binding.backupImportBtn).forEach { btn ->
            val fill = Color.parseColor("#FFFEF8")
            btn.background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = 999 * d
                setStroke((1.5f * d).toInt().coerceAtLeast(2), Color.parseColor("#483D3428"))
            }
            btn.setTextColor(ThemeUi.contrastText(fill))
        }
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
            binding.sectionBackup,
            binding.sectionBrightness,
            binding.sectionLook,
            binding.sectionPaper,
            binding.valBrightness
        )
        ThemeUi.colorTexts(
            theme.muted,
            binding.btnBack,
            binding.labelBackup,
            binding.labelBrightness,
            binding.labelTheme,
            binding.labelPaper,
            binding.hintOpacity
        )
        ThemeUi.colorLines(0x597A6F62, binding.headerLine, binding.backupDivider)
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
                styleBackupButtons()
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
            styleBackupButtons()
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
                styleBackupButtons()
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
