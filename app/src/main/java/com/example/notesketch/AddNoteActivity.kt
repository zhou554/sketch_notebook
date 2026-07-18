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
    }

    private fun save() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "请填写标题", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val firstReview = Ebbinghaus.reviewTimeFor(now, 0)
        val note = Note(
            title = title,
            content = content,
            createdAt = now,
            stage = 0,
            nextReviewTime = firstReview,
            finished = false
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.insert(note) }
            Toast.makeText(this@AddNoteActivity, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
