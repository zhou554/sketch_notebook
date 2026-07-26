package com.example.notesketch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notesketch.data.Note
import com.example.notesketch.databinding.ItemNoteBinding

class NoteAdapter(
    private val onClick: (Note) -> Unit,
    private val onDelete: (Note) -> Unit
) : ListAdapter<Note, NoteAdapter.VH>(DIFF) {

    private val tilts = floatArrayOf(-0.45f, 0.35f, -0.3f, 0.4f, -0.35f, 0.25f)
    private val shiftsDp = floatArrayOf(0f, 4f, -3f, 5f, -4f, 2f)

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
            val ctx = root.context
            val density = ctx.resources.displayMetrics.density

            tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.ink))
            tvContent.setTextColor(ContextCompat.getColor(ctx, R.color.muted))
            tvReview.setTextColor(
                ContextCompat.getColor(ctx, if (due) R.color.due else R.color.muted)
            )
            btnDelete.setTextColor(ContextCompat.getColor(ctx, R.color.muted))

            val yellow = position % 2 == 0
            stickerCard.setBackgroundResource(
                if (yellow) R.drawable.bg_sticker_yellow else R.drawable.bg_sticker_pink
            )

            val tilt = tilts[position % tilts.size]
            val shift = shiftsDp[position % shiftsDp.size] * density
            stickerCard.rotation = tilt
            stickerCard.translationX = shift

            root.setOnClickListener { onClick(note) }
            stickerCard.setOnClickListener { onClick(note) }
            btnDelete.setOnClickListener { onDelete(note) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(o: Note, n: Note) = o.id == n.id
            override fun areContentsTheSame(o: Note, n: Note) = o == n
        }
    }
}
