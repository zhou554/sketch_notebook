package com.example.notesketch

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notesketch.data.MoodEntry
import com.example.notesketch.databinding.ItemMoodBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodAdapter(
    private val onClick: (MoodEntry) -> Unit,
    private val onDelete: (MoodEntry) -> Unit
) : ListAdapter<MoodEntry, MoodAdapter.VH>(DIFF) {

    private val tilts = floatArrayOf(-0.45f, 0.35f, -0.3f, 0.4f, -0.35f, 0.25f)
    private val shiftsDp = floatArrayOf(0f, 4f, -3f, 5f, -4f, 2f)

    inner class VH(val binding: ItemMoodBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMoodBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        with(holder.binding) {
            tvMood.text = entry.mood
            ivMood.setImageResource(MoodIcons.drawableOf(entry.icon))
            val preview = NoteInlineImages.plainPreview(entry.content)
            tvContent.text = preview
            tvContent.visibility = if (preview.isBlank()) View.GONE else View.VISIBLE
            NoteImageStore.loadInto(ivThumb, root.context, NoteInlineImages.firstImage(entry.content))
            tvDate.text = DATE_FMT.format(Date(entry.createdAt))

            val density = root.context.resources.displayMetrics.density
            val fill = UiPrefs.stickerColor(MoodIcons.colorIdOf(entry.icon))
            val ink = ThemeUi.contrastText(fill)
            val muted = ThemeUi.contrastMuted(fill)

            tvMood.setTextColor(ink)
            tvContent.setTextColor(muted)
            tvDate.setTextColor(muted)
            btnDelete.setTextColor(muted)

            stickerCard.background = GradientDrawable().apply {
                setColor(fill)
                setStroke((2 * density).toInt().coerceAtLeast(1), 0x523D3428)
            }

            stickerCard.rotation = tilts[position % tilts.size]
            stickerCard.translationX = shiftsDp[position % shiftsDp.size] * density

            root.setOnClickListener { onClick(entry) }
            stickerCard.setOnClickListener { onClick(entry) }
            btnDelete.setOnClickListener { onDelete(entry) }
        }
    }

    companion object {
        private val DATE_FMT = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)

        private val DIFF = object : DiffUtil.ItemCallback<MoodEntry>() {
            override fun areItemsTheSame(o: MoodEntry, n: MoodEntry) = o.id == n.id
            override fun areContentsTheSame(o: MoodEntry, n: MoodEntry) = o == n
        }
    }
}
