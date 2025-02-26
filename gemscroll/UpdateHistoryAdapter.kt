// UpdateHistoryAdapter.kt
package com.example.gemscroll

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gemscroll.databinding.ItemUpdateHistoryBinding

class UpdateHistoryAdapter(private val historyList: List<UpdateHistoryEntry>) :
    RecyclerView.Adapter<UpdateHistoryAdapter.UpdateHistoryViewHolder>() {

    inner class UpdateHistoryViewHolder(private val binding: ItemUpdateHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: UpdateHistoryEntry) {
            binding.textViewTimestamp.text = entry.timestamp
            binding.textViewStatus.text = entry.status
            binding.textViewMessage.text = entry.message ?: ""
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpdateHistoryViewHolder {
        val binding = ItemUpdateHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UpdateHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UpdateHistoryViewHolder, position: Int) {
        holder.bind(historyList[position])
    }

    override fun getItemCount(): Int = historyList.size
}
