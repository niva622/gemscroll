//CoinMarketCapParser.kt
package com.example.gemscroll

import org.jsoup.Jsoup
import java.io.IOException

object CoinMarketCapParser {

    // Функция для получения цены из HTML-страницы
    fun getPriceFromUrl(url: String): String? {
        return try {
            val document = Jsoup.connect(url).get()
            // Используем селектор для поиска нужного элемента
            val priceElement = document.selectFirst("span[data-test=\"text-cdp-price-display\"]")
            priceElement?.text()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}