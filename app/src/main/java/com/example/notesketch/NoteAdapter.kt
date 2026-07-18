package com.example.notesketch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notesketch.data.Note
import com.example.notesketch.databinding.ItemNoteBinding

class NoteAdapter(
    private val onDone: (Note) -> Unit,
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
            val colorRes = if (due) R.color.due else R.color.text_secondary
            tvReview.setTextColor(root.context.getColor(colorRes))

            btnDone.isEnabled = !note.finished
            btnDone.setOnClickListener { onDone(note) }
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
