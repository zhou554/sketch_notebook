package com.example.notesketch

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notesketch.data.Note
import com.example.notesketch.databinding.ItemNoteBinding

class NoteAdapter(
    private val onClick: (Note) -> Unit,
    private val onDelete: (Note) -> Unit
) : ListAdapter<Note, NoteAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val note = getItem(position)
        with(holder.binding) {
            tvTitle.text = note.title
            tvContent.text = note.content
            tvReview.text = DateUtil.reviewText(note.nextReviewTime, note.finished)

            val due = DateUtil.isDue(note.nextReviewTime, note.finished)
            val theme = UiPrefs.theme(root.context)
            val opacity = UiPrefs.contentOpacity(root.context)
            val density = root.resources.displayMetrics.density

            tvTitle.setTextColor(theme.ink)
            tvContent.setTextColor(theme.muted)
            tvReview.setTextColor(if (due) theme.due else theme.muted)
            btnDelete.setTextColor(theme.muted)
            btnDelete.background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(theme.surface, (opacity * 255 / 100).coerceIn(50, 255)))
                setStroke((1 * density).toInt().coerceAtLeast(1), theme.line)
            }
            root.background = floatingRowBackground(theme, opacity, density)

            root.setOnClickListener { onClick(note) }
            btnDelete.setOnClickListener { onDelete(note) }
        }
    }

    private fun floatingRowBackground(
        theme: ThemePalette,
        opacity: Int,
        density: Float
    ): LayerDrawable {
        val fill = GradientDrawable().apply { setColor(theme.surfaceWithAlpha(opacity)) }
        val rail = GradientDrawable().apply { setColor(theme.accent) }
        val bottom = GradientDrawable().apply { setColor(theme.line) }
        val ld = LayerDrawable(arrayOf(fill, rail, bottom))
        val railW = (1 * density).toInt().coerceAtLeast(1)
        val lineH = (1 * density).toInt().coerceAtLeast(1)
        ld.setLayerWidth(1, railW)
        ld.setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
        ld.setLayerHeight(2, lineH)
        ld.setLayerGravity(2, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
        return ld
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(o: Note, n: Note) = o.id == n.id
            override fun areContentsTheSame(o: Note, n: Note) = o == n
        }
    }
}
