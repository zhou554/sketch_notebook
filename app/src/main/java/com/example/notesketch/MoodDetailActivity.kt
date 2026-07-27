package com.example.notesketch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.data.MoodEntry
import com.example.notesketch.databinding.ActivityMoodDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMoodDetailBinding
    private val dao by lazy { AppDatabase.get(this).moodDao() }
    private var entry: MoodEntry? = null
    private var editing = false
    private var selectedIcon = 0

    private val imageActions = NoteImageActions(this) { paths ->
        paths.forEach { NoteInlineImages.insertAtCursor(binding.etContent, this, it) }
        binding.etContent.requestFocus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoodDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val entryId = intent.getLongExtra(EXTRA_MOOD_ID, -1L)
        if (entryId < 0) {
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
        loadEntry(entryId)
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
        ThemeUi.colorTexts(theme.ink, binding.tvHeader)
        applyPaperColor()
    }

    private fun applyPaperColor() {
        val icon = if (editing) selectedIcon else (entry?.icon ?: selectedIcon)
        val fill = UiPrefs.stickerColor(MoodIcons.colorIdOf(icon))
        val d = resources.displayMetrics.density
        binding.notePaper.background = GradientDrawable().apply {
            setColor(fill)
            setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
        }
        val ink = ThemeUi.contrastText(fill)
        val muted = ThemeUi.contrastMuted(fill)
        binding.tvMood.setTextColor(ink)
        binding.tvContent.setTextColor(muted)
        binding.tvDate.setTextColor(muted)
        binding.etContent.setTextColor(ink)
        binding.etContent.setHintTextColor(muted)
        binding.etTitle.setTextColor(ink)
        binding.etTitle.setHintTextColor(muted)
    }

    private fun loadEntry(entryId: Long) {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dao.getById(entryId) }
            if (loaded == null) {
                Toast.makeText(this@MoodDetailActivity, "心情日记不存在", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            bindEntry(loaded)
        }
    }

    private fun bindEntry(e: MoodEntry) {
        entry = e
        selectedIcon = e.icon
        binding.tvMood.text = e.mood
        binding.ivMoodBadge.setImageResource(MoodIcons.drawableOf(e.icon))
        NoteInlineImages.bindToTextView(binding.tvContent, this, e.content)
        binding.tvDate.text = DATE_FMT.format(Date(e.createdAt))
        applyPaperColor()
    }

    private fun enterEditMode() {
        val e = entry ?: return
        editing = true
        selectedIcon = e.icon
        binding.viewMode.visibility = View.GONE
        binding.editMode.visibility = View.VISIBLE
        binding.etTitle.setText(e.mood)
        bindIconRow()
        NoteInlineImages.bindToEditText(binding.etContent, this, e.content)
        applyPaperColor()
        binding.etContent.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etContent, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun bindIconRow() {
        MoodIcons.bindPicker(binding.iconRow, 40, selectedIcon) { index ->
            selectedIcon = index
            bindIconRow()
            applyPaperColor()
        }
    }

    private fun saveEditsAndExitEdit() {
        saveEdits(exitEdit = true)
    }

    private fun saveEdits(exitEdit: Boolean) {
        val current = entry ?: return
        if (!editing && exitEdit) return
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
            .ifBlank { MoodDiaryActivity.DEFAULT_TITLE }
        val content = NoteInlineImages.serialize(binding.etContent.text ?: "")
        if (title == current.mood && selectedIcon == current.icon && content == current.content) {
            if (exitEdit) leaveEditMode()
            return
        }
        val updated = current.copy(mood = title, icon = selectedIcon, content = content)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.update(updated)
                NoteInlineImages.deleteUnreferenced(
                    this@MoodDetailActivity,
                    current.content,
                    "",
                    content
                )
            }
            bindEntry(updated)
            if (exitEdit) leaveEditMode()
            else Toast.makeText(this@MoodDetailActivity, "已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun leaveEditMode() {
        editing = false
        binding.editMode.visibility = View.GONE
        binding.viewMode.visibility = View.VISIBLE
        applyPaperColor()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etContent.windowToken, 0)
    }

    companion object {
        const val EXTRA_MOOD_ID = "mood_id"
        private val DATE_FMT = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
    }
}
