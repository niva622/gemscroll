//StoneAdapter.kt
package com.example.gemscroll

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gemscroll.databinding.ItemStoneBinding

data class Stone(val category: String, val level: Int, val imageRes: Int)

class StoneAdapter(
    private val stones: List<Stone>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<StoneAdapter.StoneViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(stone: Stone)
    }

    inner class StoneViewHolder(val binding: ItemStoneBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.imageStone.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(stones[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoneViewHolder {
        val binding = ItemStoneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StoneViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoneViewHolder, position: Int) {
        val stone = stones[position]
        holder.binding.imageStone.setImageResource(stone.imageRes)

    }

    override fun getItemCount(): Int = stones.size
}