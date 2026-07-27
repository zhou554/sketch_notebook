package com.example.notesketch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
            refreshTagBtn()
        }
        binding.btnIncome.setOnClickListener {
            isExpense = false
            selectedCategory = INCOME_CATS.first()
            refreshTypeUi()
            refreshTagBtn()
        }
        binding.btnTag.setOnClickListener { showTagPicker() }
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
        refreshTagBtn()
    }

    override fun onResume() {
        super.onResume()
        applyUi()
        refreshTypeUi()
        refreshTagBtn()
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
            binding.tvSaveLabel
        )
        ThemeUi.colorTexts(
            theme.muted,
            binding.btnBack,
            binding.emptyView
        )
        ThemeUi.colorTexts(cardInk, binding.tvBalance, binding.labelBalance, binding.btnTag)
        ThemeUi.colorTexts(cardMuted, binding.labelIncome, binding.labelExpense)
        ThemeUi.colorTexts(theme.accent, binding.tvIncome)
        ThemeUi.colorTexts(theme.due, binding.tvExpense)
        binding.etAmount.setTextColor(cardInk)
        binding.etAmount.setHintTextColor(cardMuted)
        binding.etMemo.setTextColor(cardInk)
        binding.etMemo.setHintTextColor(cardMuted)
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
        val d = resources.displayMetrics.density
        val inkGreen = Color.parseColor("#3D5C4A")
        binding.summaryCard.background = GradientDrawable().apply {
            setColor(card)
            setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
        }
        val fill = Color.parseColor("#FFFEF8")
        val stroke = (1.5f * d).toInt().coerceAtLeast(2)
        binding.etAmount.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 999 * d
            setStroke(stroke, inkGreen)
        }
        binding.btnTag.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 999 * d
            setStroke(stroke, inkGreen)
        }
        binding.etMemo.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 999 * d
            setStroke(stroke, inkGreen)
        }
        binding.typeDivider.setBackgroundColor(inkGreen)
    }

    private fun refreshTypeUi() {
        val theme = UiPrefs.theme(this)
        val d = resources.displayMetrics.density
        val fill = Color.parseColor("#FFFEF8")
        val inkGreen = Color.parseColor("#3D5C4A")
        binding.typeSegment.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 999 * d
            setStroke((1.5f * d).toInt().coerceAtLeast(2), inkGreen)
        }
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
        tv.setTextColor(if (on) Color.parseColor("#FFFEF8") else ThemeUi.contrastText(Color.parseColor("#FFFEF8")))
        val r = 999 * d
        val radii = if (left) {
            floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        } else {
            floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        tv.background = if (on) {
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#8BB8AB"),
                    theme.accent,
                    Color.parseColor("#5F9084")
                )
            ).apply { cornerRadii = radii }
        } else {
            GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadii = radii
            }
        }
    }

    private fun currentCats(): List<String> = if (isExpense) EXPENSE_CATS else INCOME_CATS

    private fun refreshTagBtn() {
        val cats = currentCats()
        if (selectedCategory !in cats) selectedCategory = cats.first()
        binding.btnTag.text = selectedCategory
        refreshMemoField(focus = false)
    }

    private fun refreshMemoField(focus: Boolean) {
        val show = selectedCategory == "其他"
        binding.etMemo.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            binding.etMemo.setText("")
            return
        }
        if (focus) {
            binding.etMemo.requestFocus()
            binding.etMemo.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(binding.etMemo, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun showTagPicker() {
        val cats = currentCats().toTypedArray()
        val title = if (isExpense) "选择用途" else "选择来源"
        val checked = cats.indexOf(selectedCategory).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(cats, checked) { dialog, which ->
                selectedCategory = cats[which]
                refreshTagBtn()
                if (selectedCategory == "其他") refreshMemoField(focus = true)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshSummary(list: List<LedgerEntry>) {
        var income = 0L
        var expense = 0L
        list.forEach { e ->
            if (e.isExpense) expense += e.amountCents else income += e.amountCents
        }
        val balance = income - expense
        binding.tvIncome.text = formatYuan(income)
        binding.tvExpense.text = formatYuan(expense)
        binding.tvBalance.text = formatYuan(balance)
    }

    private fun addEntry() {
        val raw = binding.etAmount.text?.toString()?.trim().orEmpty()
        val yuan = raw.toDoubleOrNull()
        if (yuan == null || yuan <= 0) {
            Toast.makeText(this, "请输入有效金额", Toast.LENGTH_SHORT).show()
            return
        }
        val cents = Math.round(yuan * 100.0)
        val memo = if (selectedCategory == "其他") {
            binding.etMemo.text?.toString()?.trim().orEmpty()
        } else {
            ""
        }
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
