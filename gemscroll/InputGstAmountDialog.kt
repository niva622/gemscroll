// InputGstAmountDialog.kt
package com.example.gemscroll

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.util.Locale

class InputGstAmountDialog : DialogFragment() {

    interface InputGstAmountListener {
        fun onGstAmountEntered(amount: Int)
    }

    private var listener: InputGstAmountListener? = null

    fun setListener(listener: InputGstAmountListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_input_gst_amount, null)

        val editTextGstAmount = view.findViewById<EditText>(R.id.editTextGstAmount)

        builder.setView(view)
            .setTitle("Добавить GST")
            .setPositiveButton("Добавить") { _, _ ->
                val amountString = editTextGstAmount.text.toString()
                if (amountString.isNotEmpty()) {
                    val amount = amountString.toIntOrNull() ?: 0
                    listener?.onGstAmountEntered(amount)
                }
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.cancel()
            }

        return builder.create()
    }
}