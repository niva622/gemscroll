// PriceDialog.kt
package com.example.gemscroll

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class PriceDialog(private val prices: List<StonePrice>) : DialogFragment() {
    private lateinit var dataManager: DataManager
    private var gmtPrice: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataManager = DataManager(requireContext())

        // Загружаем курс GMT асинхронно
        lifecycleScope.launch {
            gmtPrice = dataManager.getGmtPrice()
            updateDialogContent()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Цены на камни и токены")
            .setMessage("Загрузка данных...") // Временный текст
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
    }

    private fun updateDialogContent() {
        val message = buildPriceMessage()
        (dialog as? AlertDialog)?.setMessage(message)
    }

    private fun buildPriceMessage(): String {
        val message = StringBuilder()

        val stoneFormatterGmt = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("ru", "RU")).apply {
            decimalSeparator = ','
        })

        val usdFormatter = DecimalFormat("#,##0.0000", DecimalFormatSymbols(Locale.US))

        prices.forEach { price ->
            when {
                isStone(price.name) -> {
                    val priceGmt = stoneFormatterGmt.format(price.price)

                    // Новое условие для цен <= 1 GMT
                    if (price.price > 1.0) {
                        val priceUsd = if (gmtPrice > 0) {
                            val usdValue = price.price * gmtPrice
                            " (${usdFormatter.format(usdValue)}$)"
                        } else {
                            " (курс GMT недоступен)"
                        }
                        message.append("${price.name}: $priceGmt GMT$priceUsd\n")
                    } else {
                        // Только GMT для цен <= 1
                        message.append("${price.name}: $priceGmt GMT\n")
                    }
                }
                else -> {
                    val formattedPrice = usdFormatter.format(price.price)
                    message.append("${price.name}: $${formattedPrice}\n")
                }
            }
        }

        return message.toString().trimEnd()
    }

    private fun isStone(name: String): Boolean {
        val stoneCategories = listOf("Efficiency", "Luck", "Comfort", "Resilience")
        return stoneCategories.any { name.startsWith(it, ignoreCase = true) }
    }
}