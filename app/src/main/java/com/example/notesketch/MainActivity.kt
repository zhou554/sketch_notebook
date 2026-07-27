package com.example.notesketch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.data.Note
import com.example.notesketch.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val dao by lazy { AppDatabase.get(this).noteDao() }
    private lateinit var adapter: NoteAdapter

    private val pullHandler = Handler(Looper.getMainLooper())
    private var pullStartY = 0f
    private var pullDistance = 0f
    private var pullHoldStartAt = 0L
    private var pullArmed = false
    private var pullTriggered = false
    private var pullCooldownUntil = 0L
    private var minPullPx = 0f

    private val pullTickRunnable = object : Runnable {
        override fun run() {
            if (pullTriggered || !pullArmed) return
            val progress = ((System.currentTimeMillis() - pullHoldStartAt).toFloat() / HOLD_MS)
                .coerceIn(0f, 1f)
            updatePullVisual(pullDistance, progress)
            if (progress >= 1f) {
                pullTriggered = true
                pullCooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
                resetPull(hideHint = true)
                startActivity(Intent(this@MainActivity, AddNoteActivity::class.java))
                return
            }
            pullHandler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        minPullPx = 48f * resources.displayMetrics.density

        adapter = NoteAdapter(
            onClick = { openDetail(it) },
            onDelete = { deleteNote(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false)
        setupPullToAdd()
        setupForestPeeps()

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
        binding.btnTools.setOnClickListener {
            startActivity(Intent(this, ToolsActivity::class.java))
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

    override fun onDestroy() {
        pullHandler.removeCallbacks(pullTickRunnable)
        super.onDestroy()
    }

    private fun setupForestPeeps() {
        binding.root.doOnLayout { layoutForestPeeps() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            layoutForestPeeps()
            insets
        }
    }

    /** 对齐预览：宽约 46% 屏宽、高约 21% 屏高，贴紧左右底边。 */
    private fun layoutForestPeeps() {
        val root = binding.root
        if (root.width <= 0 || root.height <= 0) return
        val d = resources.displayMetrics.density
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        val bottomInset = insets?.bottom ?: 0
        val forestW = (root.width * 0.46f).roundToInt()
            .coerceIn((120 * d).roundToInt(), (root.width * 0.5f).roundToInt())
        val forestH = min((root.height * 0.21f).roundToInt(), (118 * d).roundToInt())
            .coerceAtLeast((72 * d).roundToInt())
        val edgePull = (6 * d).roundToInt()

        fun place(iv: ImageView, startSide: Boolean) {
            val lp = (iv.layoutParams as FrameLayout.LayoutParams).apply {
                width = forestW
                height = forestH
                gravity = if (startSide) {
                    android.view.Gravity.BOTTOM or android.view.Gravity.START
                } else {
                    android.view.Gravity.BOTTOM or android.view.Gravity.END
                }
                bottomMargin = bottomInset
                marginStart = 0
                marginEnd = 0
            }
            iv.layoutParams = lp
            iv.translationX = if (startSide) -edgePull.toFloat() else edgePull.toFloat()
            iv.translationY = (4 * d)
        }
        place(binding.forestLeft, startSide = true)
        place(binding.forestRight, startSide = false)
    }

    private fun setupPullToAdd() {
        binding.recyclerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pullStartY = event.y
                    pullTriggered = false
                    resetPull(hideHint = true)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pullTriggered || System.currentTimeMillis() < pullCooldownUntil) {
                        return@setOnTouchListener false
                    }
                    if (!isListAtBottom()) {
                        resetPull(hideHint = true)
                        return@setOnTouchListener false
                    }
                    val pullY = max(0f, pullStartY - event.y)
                    pullDistance = pullY
                    tickHold(pullY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!pullTriggered) resetPull(hideHint = true)
                }
            }
            false
        }
    }

    private fun tickHold(delta: Float) {
        if (delta < minPullPx) {
            pullHoldStartAt = 0L
            pullArmed = false
            pullHandler.removeCallbacks(pullTickRunnable)
            updatePullVisual(delta, 0f)
            return
        }
        if (!pullArmed) {
            pullArmed = true
            pullHoldStartAt = System.currentTimeMillis()
            pullHandler.removeCallbacks(pullTickRunnable)
            pullHandler.post(pullTickRunnable)
        }
        val progress = ((System.currentTimeMillis() - pullHoldStartAt).toFloat() / HOLD_MS)
            .coerceIn(0f, 1f)
        updatePullVisual(delta, progress)
    }

    private fun updatePullVisual(dist: Float, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val hint = binding.pullHint
        if (dist < minPullPx * 0.25f) {
            hint.visibility = View.GONE
            hint.alpha = 1f
            return
        }
        hint.visibility = View.VISIBLE
        hint.alpha = 0.45f + p * 0.55f
        hint.text = when {
            p >= 1f -> HINT_READY
            dist >= minPullPx -> "$HINT_PROGRESS ${(p * 100).roundToInt()}%"
            else -> HINT_BASE
        }
    }

    private fun isListAtBottom(): Boolean {
        val rv = binding.recyclerView
        if (rv.adapter?.itemCount == 0) return true
        return !rv.canScrollVertically(1)
    }

    private fun resetPull(hideHint: Boolean) {
        pullArmed = false
        pullHoldStartAt = 0L
        pullDistance = 0f
        pullHandler.removeCallbacks(pullTickRunnable)
        if (hideHint) {
            binding.pullHint.visibility = View.GONE
            binding.pullHint.alpha = 1f
            binding.pullHint.text = HINT_BASE
        }
    }

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        ThemeUi.colorTexts(theme.ink, binding.pullHint)
    }

    private fun openDetail(note: Note) {
        startActivity(
            Intent(this, NoteDetailActivity::class.java)
                .putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
        )
    }

    private fun deleteNote(note: Note) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.delete(note)
                val images = (
                    NoteInlineImages.listedImages(note.content) +
                        listOfNotNull(note.imagePath.takeIf { it.isNotBlank() })
                    ).toSet()
                images.forEach { NoteImageStore.delete(this@MainActivity, it) }
            }
        }
    }

    companion object {
        private const val HOLD_MS = 700L
        private const val COOLDOWN_MS = 900L
        private const val TICK_MS = 32L
        private const val HINT_BASE = "滑到底后，继续上拉并按住片刻"
        private const val HINT_PROGRESS = "继续按住…"
        private const val HINT_READY = "即将进入新建页…"
    }
}
