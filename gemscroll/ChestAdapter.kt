// ChestAdapter.kt
package com.example.gemscroll

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gemscroll.databinding.ItemChestBinding
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class Chest(
    val number: Int,
    val date: String,
    val entries: List<StoneEntry>,
    val sumGMT: Double,
    val sumUSD: Double,

    val openingCostUsd: Double = 0.0 // Добавлено значение по умолчанию
)

class ChestAdapter(
    private val chests: List<Chest>,
    private val listener: OnChestClickListener
) : RecyclerView.Adapter<ChestAdapter.ChestViewHolder>() {

    interface OnChestClickListener {
        fun onChestClick(chest: Chest)
    }

    inner class ChestViewHolder(private val binding: ItemChestBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onChestClick(chests[position])
                }
            }
        }

        fun bind(chest: Chest) {
            // Отображаем "Сундук {номер} - {дата}"
            binding.textViewChestNumber.text = "Сундук ${chest.number} - ${chest.date}"

            // Формируем текст с деталями камней
            val details = StringBuilder()
            chest.entries.forEach { entry ->
                val formattedDate = entry.date.trim()
                val category = entry.category.trim()
                details.append("$formattedDate - $category ${entry.level} lvl - ${entry.quantity} шт\n")
            }
            binding.textViewChestDetails.text = details.toString().trimEnd()

            // Отображаем сумму сундука в GMT и USD (если > 0)
            if (chest.sumUSD > 0.0) {
                binding.textViewChestSum.text =
                    "Сумма сундука: ${formatCurrency(chest.sumGMT)} GMT ($${formatCurrencyUSD(chest.sumUSD)})"
            } else {
                binding.textViewChestSum.text =
                    "Сумма сундука: ${formatCurrency(chest.sumGMT)} GMT"
            }

            // Новая строка — цена открытия сундука в $
            // Если вдруг openingCostUsd == 0, можно скрыть текст или показать 0$
            if (chest.openingCostUsd > 0) {
                binding.textViewChestOpenCost.visibility = View.VISIBLE
                val costStr = "$${formatCurrencyUSD(chest.openingCostUsd)}"
                binding.textViewChestOpenCost.text = "Цена открытия сундука: $costStr"
            } else {
                binding.textViewChestOpenCost.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChestViewHolder {
        val binding = ItemChestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChestViewHolder, position: Int) {
        holder.bind(chests[position])
    }

    override fun getItemCount(): Int = chests.size

    private fun formatCurrency(value: Double): String {
        val decimalFormatSymbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
            decimalSeparator = ','
        }
        val formatter = DecimalFormat("#,##0.00", decimalFormatSymbols)
        return formatter.format(value)
    }

    // Форматирование для USD
    private fun formatCurrencyUSD(value: Double): String {
        val decimalFormatSymbols = DecimalFormatSymbols(Locale.US).apply {
            decimalSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00", decimalFormatSymbols)
        return formatter.format(value)
    }
}
