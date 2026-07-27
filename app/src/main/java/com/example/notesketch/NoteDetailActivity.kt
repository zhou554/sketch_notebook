package com.example.notesketch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
        binding.etContent.setHorizontallyScrolling(false)
        binding.etContent.isVerticalScrollBarEnabled = false
        binding.tvContent.isVerticalScrollBarEnabled = false
        binding.tvContent.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
        binding.etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                scrollContentToLatest()
            }
        })
        // 避免外层 ScrollView 抢走便签内滚动
        binding.etContent.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                v.performClick()
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
        ThemeUi.colorTexts(theme.ink, binding.tvHeader, binding.tvTitle, binding.tvTimelineTitle)
        ThemeUi.colorTexts(theme.muted, binding.tvContent)
        ThemeUi.colorLines(0x737A6F62, binding.rule1)
        applyNotePaperColor()
    }

    private fun applyNotePaperColor() {
        val colorId = if (editing) selectedColorId else (note?.colorId ?: selectedColorId)
        val fill = UiPrefs.stickerColor(colorId)
        val d = resources.displayMetrics.density
        binding.notePaper.background = GradientDrawable().apply {
            setColor(fill)
            setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
        }
    }

    private fun loadNote(noteId: Long) {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dao.getById(noteId) }
            if (loaded == null) {
                Toast.makeText(this@NoteDetailActivity, "笔记不存在", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            bindNote(loaded)
        }
    }

    private fun bindNote(n: Note) {
        note = n
        selectedColorId = n.colorId
        binding.tvTitle.text = n.title
        binding.tvContent.text = n.content.ifBlank { "（无正文）" }
        binding.etTitle.setText(n.title)
        binding.etContent.setText(n.content)
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
        binding.etContent.setText(n.content)
        renderColorChips()
        binding.etTitle.requestFocus()
        binding.etTitle.setSelection(binding.etTitle.text?.length ?: 0)
        scrollContentToLatest()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etTitle, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun renderColorChips() {
        binding.colorRow.removeAllViews()
        val pageTheme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        UiPrefs.themes.forEach { preset ->
            val selected = preset.id == selectedColorId
            val chip = TextView(this).apply {
                text = preset.label
                setTextColor(if (selected) pageTheme.surface else pageTheme.ink)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(if (selected) pageTheme.accent else preset.surface)
                    setStroke(
                        (1.5f * d).toInt().coerceAtLeast(1),
                        if (selected) pageTheme.accent else preset.line
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (6 * d).toInt() }
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

    private fun scrollContentToLatest() {
        binding.etContent.post {
            val layout = binding.etContent.layout ?: return@post
            val lastLine = (binding.etContent.lineCount - 1).coerceAtLeast(0)
            val y = layout.getLineTop(lastLine)
            binding.etContent.scrollTo(0, y)
        }
    }

    private fun saveEditsAndExitEdit() {
        saveEdits(exitEdit = true)
    }

    private fun saveEdits(exitEdit: Boolean) {
        val current = note ?: return
        if (!editing && exitEdit) return
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val content = binding.etContent.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (title == current.title && content == current.content && selectedColorId == current.colorId) {
            if (exitEdit) leaveEditMode()
            return
        }
        val updated = current.copy(title = title, content = content, colorId = selectedColorId)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.update(updated) }
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
