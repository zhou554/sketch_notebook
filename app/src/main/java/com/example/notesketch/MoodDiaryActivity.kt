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
            binding.labelIcon,
            binding.labelTitle,
            binding.labelContent
        )
        ThemeUi.colorTexts(
            theme.muted,
            binding.btnBack,
            binding.emptyView
        )
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
        val d = resources.displayMetrics.density
        val panel = Color.parseColor("#FFFEF8")
        val panelBorder = Color.parseColor("#483D3428")
        val panelInk = Color.parseColor("#3D3428")
        val panelMuted = Color.parseColor("#6F6256")
        styleField(binding.etTitle, panel, panelBorder, panelInk, panelMuted, d)
        styleField(binding.etContent, panel, panelBorder, panelInk, panelMuted, d)
        binding.btnAddEntry.background = ThemeUi.primaryButtonDrawable(theme, 10 * d)
        binding.btnAddEntry.setTextColor(ThemeUi.primaryOnAccentText(theme))
    }

    private fun styleField(
        et: android.widget.EditText,
        panel: Int,
        border: Int,
        ink: Int,
        muted: Int,
        d: Float
    ) {
        et.setTextColor(ink)
        et.setHintTextColor(muted)
        et.background = GradientDrawable().apply {
            setColor(panel)
            cornerRadius = 10 * d
            setStroke((1.5f * d).toInt().coerceAtLeast(2), border)
        }
    }

    private fun bindIconRow() {
        val theme = UiPrefs.theme(this)
        MoodIcons.bindPicker(binding.iconRow, 48, selectedIcon, theme) { index ->
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
            Toast.makeText(this@MoodDiaryActivity, "已保存", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val DEFAULT_TITLE = "今日心情"
    }
}
