package com.example.notesketch

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.data.Note
import com.example.notesketch.databinding.ActivityAddNoteBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddNoteBinding
    private val dao by lazy { AppDatabase.get(this).noteDao() }
    private var selectedColorId: String = "parchment"
    private var saving = false

    private val imageActions = NoteImageActions(this) { paths ->
        paths.forEach { NoteInlineImages.insertAtCursor(binding.etContent, this, it) }
        binding.etContent.requestFocus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedColorId = UiPrefs.theme(this).id
        binding.btnBackRow.setOnClickListener { saveAndFinish() }
        binding.tvHeader.setOnClickListener { saveAndFinish() }
        binding.btnSave.setOnClickListener { saveAndFinish() }
        binding.btnCamera.setOnClickListener {
            binding.etContent.snapshotCursor()
            imageActions.takePhoto()
        }
        binding.btnGallery.setOnClickListener {
            binding.etContent.snapshotCursor()
            imageActions.pickFromGallery()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndFinish()
            }
        })

        applyUi()
        renderColorChips()
    }

    override fun onResume() {
        super.onResume()
        applyUi()
        renderColorChips()
    }

    private fun applyUi() {
        val pageTheme = UiPrefs.theme(this)
        val sticker = UiPrefs.themeById(selectedColorId)
        ThemeUi.applyWindow(this, pageTheme)
        ThemeUi.applyBrightness(this)
        binding.paperBg.paperColor = sticker.surface
        binding.paperBg.gridColor = sticker.line
        binding.paperBg.pattern = UiPrefs.paperType(this)
        binding.root.setBackgroundColor(sticker.surface)
        binding.contentPanel.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        ThemeUi.colorTexts(ThemeUi.contrastMuted(sticker.surface), binding.labelColor)
        ThemeUi.colorTexts(ThemeUi.contrastMuted(sticker.surface), binding.tvHeader)
        val ink = ThemeUi.contrastText(sticker.surface)
        val muted = ThemeUi.contrastMuted(sticker.surface)
        binding.etTitle.setTextColor(ink)
        binding.etTitle.setHintTextColor(muted)
        binding.etContent.setTextColor(ink)
        binding.etContent.setHintTextColor(muted)
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
    }

    private fun renderColorChips() {
        binding.colorRow.removeAllViews()
        val pageTheme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        UiPrefs.themes.forEach { preset ->
            val selected = preset.id == selectedColorId
            val chip = TextView(this).apply {
                text = preset.label
                val fill = if (selected) pageTheme.accent else preset.surface
                setTextColor(ThemeUi.contrastText(fill))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(fill)
                    setStroke(
                        (2 * d).toInt().coerceAtLeast(1),
                        if (selected) pageTheme.accent else preset.line
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (6 * d).toInt() }
                minHeight = (36 * d).toInt()
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedColorId = preset.id
                    applyUi()
                    renderColorChips()
                }
            }
            binding.colorRow.addView(chip)
        }
    }

    private fun saveAndFinish() {
        if (saving) return
        val titleRaw = binding.etTitle.text?.toString()?.trim().orEmpty()
        val content = NoteInlineImages.serialize(binding.etContent.text ?: "")
        val plain = NoteInlineImages.plainPreview(content)
        if (titleRaw.isEmpty() && content.isBlank()) {
            NoteInlineImages.listedImages(content).forEach { NoteImageStore.delete(this, it) }
            finish()
            return
        }
        val title = when {
            titleRaw.isNotEmpty() -> titleRaw
            plain.isNotEmpty() -> plain.take(80)
            else -> "图片便签"
        }
        saving = true
        val now = System.currentTimeMillis()
        val note = Note(
            title = title,
            content = content,
            createdAt = now,
            stage = 0,
            nextReviewTime = Ebbinghaus.reviewTimeFor(now, 0),
            finished = false,
            colorId = selectedColorId,
            imagePath = NoteInlineImages.firstImage(content)
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.insert(note) }
            finish()
        }
    }
}
