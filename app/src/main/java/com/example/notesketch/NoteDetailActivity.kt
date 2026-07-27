package com.example.notesketch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.data.Note
import com.example.notesketch.databinding.ActivityNoteDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteDetailBinding
    private val dao by lazy { AppDatabase.get(this).noteDao() }
    private var note: Note? = null
    private var editing = false
    private var selectedColorId: String = "parchment"

    private val imageActions = NoteImageActions(this) { paths ->
        paths.forEach { NoteInlineImages.insertAtCursor(binding.etContent, this, it) }
        binding.etContent.requestFocus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId < 0) {
            finish()
            return
        }

        binding.btnBackRow.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.tvHeader.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.notePaper.setOnClickListener {
            if (!editing) enterEditMode()
        }
        binding.viewMode.setOnClickListener { enterEditMode() }
        binding.btnSaveEdit.setOnClickListener { saveEditsAndExitEdit() }
        binding.btnCamera.setOnClickListener {
            binding.etContent.snapshotCursor()
            imageActions.takePhoto()
        }
        binding.btnGallery.setOnClickListener {
            binding.etContent.snapshotCursor()
            imageActions.pickFromGallery()
        }
        binding.etContent.setHorizontallyScrolling(false)
        binding.etContent.isVerticalScrollBarEnabled = false
        binding.tvContent.isVerticalScrollBarEnabled = false
        binding.tvContent.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
        binding.etContent.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    v.performClick()
                }
                android.view.MotionEvent.ACTION_CANCEL ->
                    v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        binding.timelineView.onCheckStage = { completeStage(it) }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editing) {
                    saveEditsAndExitEdit()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        applyUi()
        loadNote(noteId)
    }

    override fun onPause() {
        super.onPause()
        if (editing) saveEdits(exitEdit = false)
    }

    override fun onResume() {
        super.onResume()
        applyUi()
    }

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        binding.contentPanel.setBackgroundColor(Color.TRANSPARENT)
        ThemeUi.colorTexts(
            theme.ink,
            binding.tvHeader,
            binding.tvTimelineTitle
        )
        ThemeUi.colorLines(0x737A6F62, binding.rule1)
        applyNotePaperColor()
    }

    private fun applyNotePaperColor() {
        val colorId = if (editing) selectedColorId else (note?.colorId ?: selectedColorId)
        // context 版解析，自定义色 id 才能命中
        val fill = UiPrefs.stickerColor(this, colorId)
        val d = resources.displayMetrics.density
        binding.notePaper.background = GradientDrawable().apply {
            setColor(fill)
            setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
        }
        val ink = ThemeUi.contrastText(fill)
        val muted = ThemeUi.contrastMuted(fill)
        binding.tvTitle.setTextColor(ink)
        binding.tvContent.setTextColor(muted)
        binding.etTitle.setTextColor(ink)
        binding.etTitle.setHintTextColor(muted)
        binding.etContent.setTextColor(ink)
        binding.etContent.setHintTextColor(muted)
    }

    private fun loadNote(noteId: Long) {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dao.getById(noteId) }
            if (loaded == null) {
                Toast.makeText(this@NoteDetailActivity, "笔记不存在", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            val migratedContent = NoteInlineImages.migrateLegacy(loaded.content, loaded.imagePath)
            val normalized = if (
                migratedContent != loaded.content ||
                NoteInlineImages.firstImage(migratedContent) != loaded.imagePath
            ) {
                val updated = loaded.copy(
                    content = migratedContent,
                    imagePath = NoteInlineImages.firstImage(migratedContent)
                )
                withContext(Dispatchers.IO) { dao.update(updated) }
                updated
            } else {
                loaded
            }
            bindNote(normalized)
        }
    }

    private fun bindNote(n: Note) {
        note = n
        selectedColorId = n.colorId
        binding.tvTitle.text = n.title
        NoteInlineImages.bindToTextView(binding.tvContent, this, n.content)
        binding.etTitle.setText(n.title)
        binding.timelineView.bind(n)
        applyNotePaperColor()
    }

    private fun enterEditMode() {
        val n = note ?: return
        editing = true
        selectedColorId = n.colorId
        binding.viewMode.visibility = View.GONE
        binding.editMode.visibility = View.VISIBLE
        binding.etTitle.setText(n.title)
        NoteInlineImages.bindToEditText(binding.etContent, this, n.content)
        renderColorChips()
        binding.etTitle.requestFocus()
        binding.etTitle.setSelection(binding.etTitle.text?.length ?: 0)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etTitle, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun renderColorChips() {
        binding.colorRow.removeAllViews()
        val pageTheme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        // 同步设置里的全部颜色（含自定义色，已隐藏的预设不展示）
        UiPrefs.allThemes(this).forEach { preset ->
            val selected = preset.id == selectedColorId
            val chip = TextView(this).apply {
                text = preset.label
                val fill = if (selected) pageTheme.accent else preset.surface
                setTextColor(ThemeUi.contrastText(fill))
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding((8 * d).toInt(), (5 * d).toInt(), (8 * d).toInt(), (5 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(fill)
                    setStroke(
                        (1.5f * d).toInt().coerceAtLeast(1),
                        if (selected) pageTheme.accent else preset.line
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (5 * d).toInt() }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedColorId = preset.id
                    applyNotePaperColor()
                    renderColorChips()
                }
            }
            binding.colorRow.addView(chip)
        }
    }

    private fun saveEditsAndExitEdit() {
        saveEdits(exitEdit = true)
    }

    private fun saveEdits(exitEdit: Boolean) {
        val current = note ?: return
        if (!editing && exitEdit) return
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val content = NoteInlineImages.serialize(binding.etContent.text ?: "")
        if (title.isEmpty()) {
            Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        val firstImage = NoteInlineImages.firstImage(content)
        if (title == current.title &&
            content == current.content &&
            selectedColorId == current.colorId &&
            firstImage == current.imagePath
        ) {
            if (exitEdit) leaveEditMode()
            return
        }
        val updated = current.copy(
            title = title,
            content = content,
            colorId = selectedColorId,
            imagePath = firstImage
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.update(updated)
                NoteInlineImages.deleteUnreferenced(
                    this@NoteDetailActivity,
                    current.content,
                    current.imagePath,
                    content
                )
            }
            bindNote(updated)
            if (exitEdit) leaveEditMode()
            else Toast.makeText(this@NoteDetailActivity, "已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun leaveEditMode() {
        editing = false
        binding.editMode.visibility = View.GONE
        binding.viewMode.visibility = View.VISIBLE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etTitle.windowToken, 0)
    }

    private fun completeStage(index: Int) {
        val current = note ?: return
        if (current.finished || index != current.stage) {
            binding.timelineView.bind(current)
            return
        }

        val nextStage = current.stage + 1
        val nextTime = Ebbinghaus.reviewTimeFor(current.createdAt, nextStage)
        val finished = nextTime < 0
        val updated = current.copy(
            stage = nextStage,
            nextReviewTime = if (finished) current.nextReviewTime else nextTime,
            finished = finished
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.update(updated) }
            bindNote(updated)
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}
