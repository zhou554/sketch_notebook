package com.example.notesketch

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.data.Note
import com.example.notesketch.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val dao by lazy { AppDatabase.get(this).noteDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = NoteAdapter(
            onClick = { openDetail(it) },
            onDelete = { deleteNote(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddNoteActivity::class.java))
        }

        lifecycleScope.launch {
            dao.observeAll().collectLatest { notes ->
                adapter.submitList(notes)
                binding.emptyView.visibility =
                    if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun openDetail(note: Note) {
        startActivity(
            Intent(this, NoteDetailActivity::class.java)
                .putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
        )
    }

    private fun deleteNote(note: Note) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.delete(note) }
        }
    }
}
