package com.example.notesketch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notesketch.data.AppDatabase
import com.example.notesketch.data.LedgerEntry
import com.example.notesketch.databinding.ActivityLedgerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LedgerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLedgerBinding
    private val dao by lazy { AppDatabase.get(this).ledgerDao() }
    private lateinit var adapter: LedgerAdapter

    private var isExpense = true
    private var selectedCategory = EXPENSE_CATS.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLedgerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = LedgerAdapter(onDelete = { entry ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { dao.delete(entry) }
            }
        })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnBackRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnExpense.setOnClickListener {
            isExpense = true
            selectedCategory = EXPENSE_CATS.first()
            refreshTypeUi()
            renderTagChips()
        }
        binding.btnIncome.setOnClickListener {
            isExpense = false
            selectedCategory = INCOME_CATS.first()
            refreshTypeUi()
            renderTagChips()
        }
        binding.btnAddEntry.setOnClickListener { addEntry() }

        lifecycleScope.launch {
            dao.observeAll().collectLatest { list ->
                adapter.submitList(list)
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                refreshSummary(list)
            }
        }

        applyUi()
        refreshTypeUi()
        renderTagChips()
    }

    override fun onResume() {
        super.onResume()
        applyUi()
        refreshTypeUi()
        renderTagChips()
    }

    private fun applyUi() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        binding.contentPanel.setBackgroundColor(Color.TRANSPARENT)
        val card = ThemeUi.stickerPanelColor(theme)
        val cardInk = ThemeUi.contrastText(card)
        val cardMuted = ThemeUi.contrastMuted(card)
        ThemeUi.colorTexts(
            theme.ink,
            binding.tvHeader,
            binding.sectionAdd,
            binding.sectionList,
            binding.labelAmount,
            binding.labelCategory,
            binding.labelMemo
        )
        ThemeUi.colorTexts(
            theme.muted,
            binding.btnBack,
            binding.emptyView
        )
        ThemeUi.colorTexts(cardInk, binding.tvBalance, binding.labelBalance, binding.tvCurrency)
        ThemeUi.colorTexts(cardMuted, binding.labelIncome, binding.labelExpense)
        ThemeUi.colorTexts(theme.accent, binding.tvIncome)
        ThemeUi.colorTexts(theme.due, binding.tvExpense)
        binding.etAmount.setTextColor(cardInk)
        binding.etAmount.setHintTextColor(cardMuted)
        binding.etMemo.setTextColor(cardInk)
        binding.etMemo.setHintTextColor(cardMuted)
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
        val d = resources.displayMetrics.density
        val panel = Color.parseColor("#FFFEF8")
        val panelBorder = Color.parseColor("#2E3D3428")
        binding.summaryCard.background = GradientDrawable().apply {
            setColor(card)
            setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
        }
        binding.amountHero.background = GradientDrawable().apply {
            setColor(panel)
            cornerRadius = 12 * d
            setStroke((2 * d).toInt().coerceAtLeast(1), panelBorder)
        }
        binding.etMemo.background = GradientDrawable().apply {
            setColor(panel)
            cornerRadius = 10 * d
            setStroke((1.5f * d).toInt().coerceAtLeast(2), panelBorder)
        }
        binding.btnAddEntry.background = ThemeUi.primaryButtonDrawable(theme, 10 * d)
        binding.btnAddEntry.setTextColor(ThemeUi.primaryOnAccentText(theme))
        binding.typeDivider.setBackgroundColor(ThemeUi.accentBorder(theme))
    }

    private fun refreshTypeUi() {
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        binding.typeSegment.background = ThemeUi.outlinePanelDrawable(theme, 10 * d, d)
        styleSegmentHalf(binding.btnExpense, isExpense, theme, d, left = true)
        styleSegmentHalf(binding.btnIncome, !isExpense, theme, d, left = false)
    }

    private fun styleSegmentHalf(
        tv: TextView,
        on: Boolean,
        theme: ThemePalette,
        d: Float,
        left: Boolean
    ) {
        tv.setTextColor(
            if (on) ThemeUi.primaryOnAccentText(theme)
            else ThemeUi.contrastText(ThemeUi.lightPanelFill)
        )
        val r = 10 * d
        val radii = if (left) {
            floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        } else {
            floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        tv.background = if (on) {
            ThemeUi.segmentSelectedDrawable(theme, radii)
        } else {
            GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadii = radii
            }
        }
    }

    private fun currentCats(): List<String> = if (isExpense) EXPENSE_CATS else INCOME_CATS

    private fun renderTagChips() {
        binding.tagChipWrap.removeAllViews()
        val cats = currentCats()
        if (selectedCategory !in cats) selectedCategory = cats.first()
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val maxRowWidth = (resources.displayMetrics.widthPixels - (40 * d).toInt()).coerceAtLeast(1)
        var row = newChipRow()
        var rowWidth = 0
        cats.forEach { cat ->
            val chip = makeTagChip(cat, cat == selectedCategory, theme, d)
            chip.measure(
                View.MeasureSpec.makeMeasureSpec(maxRowWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val chipWidth = chip.measuredWidth + (8 * d).toInt()
            if (rowWidth > 0 && rowWidth + chipWidth > maxRowWidth) {
                binding.tagChipWrap.addView(row)
                row = newChipRow()
                rowWidth = 0
            }
            row.addView(chip)
            rowWidth += chipWidth
        }
        if (row.childCount > 0) binding.tagChipWrap.addView(row)
    }

    private fun newChipRow(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

    private fun makeTagChip(label: String, selected: Boolean, theme: ThemePalette, d: Float): TextView {
        val fill = if (selected) theme.accent else Color.parseColor("#FFFEF8")
        return TextView(this).apply {
            text = label
            setTextColor(if (selected) Color.parseColor("#FFFEF8") else ThemeUi.contrastText(fill))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = 999 * d
                setStroke(
                    (1.5f * d).toInt().coerceAtLeast(2),
                    if (selected) theme.accent else Color.parseColor("#483D3428")
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.marginEnd = (8 * d).toInt()
                it.bottomMargin = (8 * d).toInt()
            }
            minHeight = (36 * d).toInt()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedCategory = label
                renderTagChips()
                if (label == "其他") {
                    binding.etMemo.requestFocus()
                }
            }
        }
    }

    private fun refreshSummary(list: List<LedgerEntry>) {
        var income = 0L
        var expense = 0L
        list.forEach { e ->
            if (e.isExpense) expense += e.amountCents else income += e.amountCents
        }
        binding.tvIncome.text = formatYuan(income)
        binding.tvExpense.text = formatYuan(expense)
        binding.tvBalance.text = formatYuan(income - expense)
    }

    private fun addEntry() {
        val raw = binding.etAmount.text?.toString()?.trim().orEmpty()
        val yuan = raw.toDoubleOrNull()
        if (yuan == null || yuan <= 0) {
            Toast.makeText(this, "请输入有效金额", Toast.LENGTH_SHORT).show()
            return
        }
        val cents = Math.round(yuan * 100.0)
        val memo = binding.etMemo.text?.toString()?.trim().orEmpty()
        val entry = LedgerEntry(
            amountCents = cents,
            isExpense = isExpense,
            category = selectedCategory,
            memo = memo,
            createdAt = System.currentTimeMillis()
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.insert(entry) }
            binding.etAmount.setText("")
            binding.etMemo.setText("")
            Toast.makeText(this@LedgerActivity, "已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatYuan(cents: Long): String =
        String.format(Locale.CHINA, "¥%.2f", cents / 100.0)

    companion object {
        val EXPENSE_CATS = listOf("餐饮", "交通", "学习", "日用", "娱乐", "其他")
        val INCOME_CATS = listOf("工资", "红包", "兼职", "其他")
    }
}
