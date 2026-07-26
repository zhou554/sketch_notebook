package com.example.notesketch

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        binding.btnBackRow.setOnClickListener { finish() }
        binding.tvHeader.setOnClickListener { finish() }
        applyUi()
    }

    override fun onResume() {
        super.onResume()
        applyUi()
    }

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        val yellow = ContextCompat.getColor(this, R.color.sticker_yellow)
        ThemeUi.applyScrapbook(this, binding.paperBg, paperColorOverride = yellow)
        binding.root.setBackgroundColor(yellow)
        binding.contentPanel.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        ThemeUi.colorTexts(theme.ink, binding.tvHeader)
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
            finish()
        }
    }
}
