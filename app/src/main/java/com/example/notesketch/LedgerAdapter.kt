package com.example.notesketch

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notesketch.data.LedgerEntry
import com.example.notesketch.databinding.ItemLedgerBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LedgerAdapter(
    private val onDelete: (LedgerEntry) -> Unit
) : ListAdapter<LedgerEntry, LedgerAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)

    inner class VH(val binding: ItemLedgerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLedgerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        val theme = UiPrefs.theme(holder.itemView.context)
        val d = holder.itemView.resources.displayMetrics.density
        with(holder.binding) {
            val kind = if (entry.isExpense) "支出" else "收入"
            tvCategory.text = "${entry.category} · $kind"
            val fill = ThemeUi.stickerPanelColor(theme)
            val ink = ThemeUi.contrastText(fill)
            val muted = ThemeUi.contrastMuted(fill)
            tvCategory.setTextColor(ink)
            val time = fmt.format(Date(entry.createdAt))
            tvMemo.text = if (entry.memo.isNotBlank()) "${entry.memo} · $time" else time
            tvMemo.setTextColor(muted)
            tvTime.visibility = View.GONE
            val yuan = entry.amountCents / 100.0
            tvAmount.text = if (entry.isExpense) {
                String.format(Locale.CHINA, "-¥%.2f", yuan)
            } else {
                String.format(Locale.CHINA, "+¥%.2f", yuan)
            }
            tvAmount.setTextColor(if (entry.isExpense) theme.due else theme.accent)
            btnDelete.setTextColor(muted)
            btnDelete.setOnClickListener { onDelete(entry) }
            rowCard.background = GradientDrawable().apply {
                setColor(fill)
                setStroke((2 * d).toInt().coerceAtLeast(1), 0x403D3428)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LedgerEntry>() {
            override fun areItemsTheSame(o: LedgerEntry, n: LedgerEntry) = o.id == n.id
            override fun areContentsTheSame(o: LedgerEntry, n: LedgerEntry) = o == n
        }
    }
}
