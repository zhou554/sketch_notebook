package com.example.notesketch

import android.os.Bundle
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnSave.setOnClickListener { save() }
        applyUi()
    }

    override fun onResume() {
        super.onResume()
        applyUi()
    }

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        val opacity = UiPrefs.contentOpacity(this)
        ThemeUi.applyWindow(this, theme)
        binding.root.setBackgroundColor(theme.bg)
        binding.seaScene.loadFromPrefs(this)
        ThemeUi.setPanel(binding.contentPanel, theme, opacity)
        ThemeUi.colorTexts(theme.ink, binding.tvHeader)
        ThemeUi.colorTexts(theme.muted, binding.labelTitle, binding.labelContent)
        ThemeUi.colorLines(theme.line, binding.headerLine)
        ThemeUi.styleEdit(binding.etTitle, theme)
        ThemeUi.styleEdit(binding.etContent, theme)
        ThemeUi.styleButton(binding.btnSave, theme)
    }

    private fun save() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "请填写标题", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val note = Note(
            title = title,
            content = content,
            createdAt = now,
            stage = 0,
            nextReviewTime = Ebbinghaus.reviewTimeFor(now, 0),
            finished = false
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.insert(note) }
            Toast.makeText(this@AddNoteActivity, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
