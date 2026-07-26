package com.example.notesketch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        // 点列表空白处 → 小鱼跃起（海景在列表下层收不到触摸）
        binding.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_UP &&
                    rv.findChildViewUnder(e.x, e.y) == null
                ) {
                    val loc = IntArray(2)
                    binding.seaScene.getLocationOnScreen(loc)
                    binding.seaScene.spawnFishAt(e.rawX - loc[0])
                }
                return false
            }
        })
        binding.emptyView.setOnClickListener {
            binding.seaScene.spawnFishAt(binding.seaScene.width / 2f)
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddNoteActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        lifecycleScope.launch {
            dao.observeAll().collectLatest { notes ->
                adapter.submitList(notes)
                binding.emptyView.visibility =
                    if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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
        binding.seaScene.loadFromPrefs(this)
        // 顶栏不透明；列表区域透明，让底部海景透出；列表格自身半透明
        binding.headerBar.setBackgroundColor(theme.surface)
        binding.recyclerView.setBackgroundColor(Color.TRANSPARENT)
        ThemeUi.colorTexts(theme.ink, binding.header)
        ThemeUi.colorTexts(theme.muted, binding.emptyView, binding.btnSettings)
        ThemeUi.colorLines(theme.line, binding.headerLine)
        ThemeUi.styleFab(binding.fabAdd, theme)
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
