package com.example.notesketch

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
    private lateinit var timelineAdapter: TimelineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId < 0) {
            finish()
            return
        }

        timelineAdapter = TimelineAdapter(onCheckNext = { completeStage(it) })
        binding.timelineList.layoutManager = LinearLayoutManager(this)
        binding.timelineList.adapter = timelineAdapter
        binding.btnBackRow.setOnClickListener { finish() }
        binding.tvHeader.setOnClickListener { finish() }

        applyUi()
        loadNote(noteId)
    }

    override fun onResume() {
        super.onResume()
        applyUi()
    }

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyWindow(this, theme)
        binding.root.setBackgroundColor(theme.bg)
        binding.paperBg.paperColor = theme.bg
        binding.paperBg.gridColor = theme.line
        binding.contentPanel.setBackgroundColor(Color.TRANSPARENT)
        ThemeUi.colorTexts(theme.ink, binding.tvHeader, binding.tvTitle, binding.tvTimelineTitle)
        ThemeUi.colorTexts(theme.muted, binding.tvContent, binding.tvTimelineHelper)
        ThemeUi.colorLines(0x737A6F62, binding.rule1)
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
        binding.tvTitle.text = n.title
        binding.tvContent.text = n.content.ifBlank { "（无正文）" }
        timelineAdapter.submit(n)
    }

    private fun completeStage(index: Int) {
        val current = note ?: return
        if (current.finished || index != current.stage) {
            timelineAdapter.submit(current)
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
