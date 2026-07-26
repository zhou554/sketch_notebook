package com.example.notesketch

import android.content.Intent
import android.os.Bundle
import android.view.View
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
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = NoteAdapter(
            onClick = { openDetail(it) },
            onDelete = { deleteNote(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false)

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddNoteActivity::class.java))
        }
        binding.ribbon.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val days = Ebbinghaus.INTERVAL_DAYS.joinToString("\n") { it.toString() }
        binding.intervalDays.text = days

        lifecycleScope.launch {
            dao.observeAll().collectLatest { notes ->
                adapter.submitList(notes)
                binding.emptyView.visibility =
                    if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyUi()
        adapter.notifyDataSetChanged()
    }

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyWindow(this, theme)
        binding.root.setBackgroundColor(theme.bg)
        binding.paperBg.paperColor = theme.bg
        binding.paperBg.gridColor = theme.line
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
