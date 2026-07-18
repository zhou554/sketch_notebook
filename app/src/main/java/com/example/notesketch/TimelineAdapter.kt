package com.example.notesketch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.notesketch.data.Note
import com.example.notesketch.databinding.ItemTimelineStageBinding

class TimelineAdapter(
    private val onCheckNext: (Int) -> Unit
) : RecyclerView.Adapter<TimelineAdapter.VH>() {

    private var note: Note? = null

    fun submit(note: Note) {
        this.note = note
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemTimelineStageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTimelineStageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = Ebbinghaus.stageCount

    override fun onBindViewHolder(holder: VH, position: Int) {
        val current = note ?: return
        val plannedAt = Ebbinghaus.reviewTimeFor(current.createdAt, position)
        val done = current.finished || position < current.stage
        val canCheck = !current.finished && position == current.stage

        with(holder.binding) {
            tvStageLabel.text = Ebbinghaus.stageLabel(position)
            tvStageDate.text = "计划：" + DateUtil.formatDateTime(plannedAt)

            checkStage.setOnCheckedChangeListener(null)
            checkStage.isChecked = done
            checkStage.isEnabled = canCheck

            if (canCheck) {
                checkStage.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) onCheckNext(position)
                }
            }
        }
    }
}
