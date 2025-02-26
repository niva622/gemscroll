// AddGstDialog.kt
package com.example.gemscroll

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class AddGstDialog : DialogFragment() {

    interface AddGstDialogListener {
        fun onGstAdded(amount: Int, total: Double)
    }

    private var listener: AddGstDialogListener? = null

    fun setListener(listener: AddGstDialogListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_add_gst, null)

        val editTextGstAmount = view.findViewById<EditText>(R.id.editTextGstAmount)
        val editTextGmtPrice = view.findViewById<EditText>(R.id.editTextGmtPrice)

        builder.setView(view)
            .setTitle("Добавить GST")
            .setPositiveButton("Добавить") { _, _ ->
                val amountString = editTextGstAmount.text.toString()
                val gmtPriceString = editTextGmtPrice.text.toString()

                if (amountString.isNotEmpty() && gmtPriceString.isNotEmpty()) {
                    val amount = amountString.toIntOrNull() ?: 0
                    val gmtPrice = gmtPriceString.toDoubleOrNull() ?: 0.0
                    val rawTotal = amount * gmtPrice
                    // Округляем до трёх десятичных знаков
                    val total = String.format(Locale.US, "%.3f", rawTotal).toDouble()

                    listener?.onGstAdded(amount, total)
                }
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.cancel()
            }

        return builder.create()
    }
}