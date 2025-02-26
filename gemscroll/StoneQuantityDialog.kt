//StoneQuantityDialog.kt
package com.example.gemscroll

import android.app.Dialog
import android.os.Bundle
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class StoneQuantityDialog(
    private val stone: Stone,
    private val onQuantitySelected: (Int) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Выберите количество")

        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_quantity, null)
        val numberPicker: NumberPicker = view.findViewById(R.id.numberPicker)
        numberPicker.minValue = 1
        numberPicker.maxValue = 100

        // Установка значения по умолчанию на основе уровня камня
        numberPicker.value = when (stone.level) {
            1 -> 2  // Для камней 1 уровня устанавливаем 2 шт
            2 -> 1  // Для камней 2 уровня устанавливаем 1 шт
            else -> 1  // Для других уровней по умолчанию 1 шт
        }

        builder.setView(view)
            .setPositiveButton("OK") { _, _ ->
                val quantity = numberPicker.value
                onQuantitySelected(quantity)
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }
}