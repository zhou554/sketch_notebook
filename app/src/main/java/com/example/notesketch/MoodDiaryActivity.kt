package com.example.notesketch

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.data.MoodEntry
import com.example.notesketch.databinding.ActivityMoodDiaryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoodDiaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMoodDiaryBinding
    private val dao by lazy { AppDatabase.get(this).moodDao() }
    private lateinit var adapter: MoodAdapter

    private var selectedIcon = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoodDiaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MoodAdapter(
            onClick = { entry ->
                startActivity(
                    Intent(this, MoodDetailActivity::class.java)
                        .putExtra(MoodDetailActivity.EXTRA_MOOD_ID, entry.id)
                )
            },
            onDelete = { entry ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        dao.delete(entry)
                        NoteInlineImages.listedImages(entry.content).forEach {
                            NoteImageStore.delete(this@MoodDiaryActivity, it)
                        }
                    }
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnBackRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddEntry.setOnClickListener { addEntry() }
        bindIconRow()

        lifecycleScope.launch {
            dao.observeAll().collectLatest { list ->
                adapter.submitList(list)
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        applyUi()
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
            binding.sectionAdd,
            binding.sectionList,
            binding.tvSaveLabel
        )
        ThemeUi.colorTexts(
            theme.muted,
            binding.btnBack,
            binding.emptyView
        )
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
        val d = resources.displayMetrics.density
        val inkGreen = Color.parseColor("#3D5C4A")
        val fill = Color.parseColor("#FFFEF8")
        val stroke = (1.5f * d).toInt().coerceAtLeast(2)
        binding.etTitle.setTextColor(ThemeUi.contrastText(fill))
        binding.etTitle.setHintTextColor(ThemeUi.contrastMuted(fill))
        binding.etTitle.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 999 * d
            setStroke(stroke, inkGreen)
        }
        binding.etContent.setTextColor(ThemeUi.contrastText(fill))
        binding.etContent.setHintTextColor(ThemeUi.contrastMuted(fill))
        binding.etContent.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 999 * d
            setStroke(stroke, inkGreen)
        }
    }

    private fun bindIconRow() {
        MoodIcons.bindPicker(binding.iconRow, 48, selectedIcon) { index ->
            selectedIcon = index
            bindIconRow()
        }
    }

    private fun addEntry() {
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val content = binding.etContent.text?.toString()?.trim().orEmpty()
        val entry = MoodEntry(
            mood = title.ifBlank { DEFAULT_TITLE },
            icon = selectedIcon,
            content = content,
            createdAt = System.currentTimeMillis()
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.insert(entry) }
            binding.etTitle.setText("")
            binding.etContent.setText("")
            Toast.makeText(this@MoodDiaryActivity, "已贴上", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val DEFAULT_TITLE = "今日心情"
    }
}
