// UpdateHistoryDialog.kt
package com.example.gemscroll

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gemscroll.databinding.DialogUpdateHistoryBinding

class UpdateHistoryDialog(private val historyList: List<UpdateHistoryEntry>) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("История обновлений цен")

        // Используем View Binding для диалога
        val binding = DialogUpdateHistoryBinding.inflate(layoutInflater)
        builder.setView(binding.root)

        // Настраиваем RecyclerView
        binding.recyclerViewUpdateHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewUpdateHistory.adapter = UpdateHistoryAdapter(historyList)

        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }

        return builder.create()
    }
}
