// StonesTotalDialog.kt
package com.example.gemscroll

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class StonesTotalDialog(private val groupedStones: List<Triple<String, Int, Int>>) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Статистика камней")

        val messageBuilder = StringBuilder()
        groupedStones.forEach { (category, level, total) ->
            messageBuilder.append("$category $level lvl - $total шт.\n")
        }

        builder.setMessage(messageBuilder.toString().trimEnd())
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }
}
