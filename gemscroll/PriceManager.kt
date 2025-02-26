// PriceManager.kt
package com.example.gemscroll

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object PriceManager {

    private val stoneUrls = mapOf(
        "Resilience 1" to "https://apilb.stepn.com/run/orderlist?saleId=1&order=2001&chain=103&refresh=false&page=0&otd=&type=501&gType=4&quality=&level=2010&bread=0",
        "Resilience 2" to "https://apilb.stepn.com/run/orderlist?saleId=1&order=2001&chain=103&refresh=false&page=0&otd=&type=501&gType=4&quality=&level=3010&bread=0",
        "Luck 1" to "https://apilb.stepn.com/run/orderlist?saleId=1&order=2001&chain=103&refresh=false&page=0&otd=&type=501&gType=2&quality=&level=2010&bread=0",
        "Luck 2" to "https://apilb.stepn.com/run/orderlist?saleId=1&order=2001&chain=103&refresh=false&page=0&otd=&type=501&gType=2&quality=&level=3010&bread=0",
        "Efficiency 1" to "https://apilb.stepn.com/run/orderlist?saleId=1&order=2001&chain=103&refresh=false&page=0&otd=&type=501&gType=1&quality=&level=2010&bread=0",
        "Efficiency 2" to "https://apilb.stepn.com/run/orderlist?saleId=1&order=2001&chain=103&refresh=false&page=0&otd=&type=501&gType=1&quality=&level=3010&bread=0",
        "Comfort 1" to "https://apilb.stepn.com/run/orderlist?saleId=1&order=2001&chain=103&refresh=false&page=0&otd=&type=501&gType=3&quality=&level=2010&bread=0",
        "Comfort 2" to "https://apilb.stepn.com/run/orderlist?saleId=1&order=2001&chain=103&refresh=false&page=0&otd=&type=501&gType=3&quality=&level=3010&bread=0"
    )

    private val coinMarketCapUrls = mapOf(
        "Green Metaverse Token" to "https://coinmarketcap.com/currencies/green-metaverse-token/",
        "Green Satoshi Token" to "https://coinmarketcap.com/currencies/green-satoshi-token/"
    )

    // Переменная для хранения последних спарсенных цен
    private var lastFetchedPrices: List<StonePrice> = emptyList()

    // Добавляем метод для получения цены GMT
    fun getGmtPrice(): Double {
        return lastFetchedPrices.find { it.name.equals("Green Metaverse Token", ignoreCase = true) }?.price ?: 0.0
    }

    suspend fun fetchAllPrices(dataManager: DataManager): List<StonePrice> = coroutineScope {
        // Попробуем загрузить сохранённые цены из предыдущего обновления
        val savedPrices = withContext(Dispatchers.IO) { dataManager.getSavedPrices() }

        // Выполняем запросы для камней
        val stonePricesDeferred = stoneUrls.map { (name, url) ->
            async(Dispatchers.IO) {
                val response = RetrofitClient.apiService.getStonePrice(url)
                val fetchedPrice = if (response.isSuccessful) {
                    response.body()?.data?.getOrNull(0)?.sellPrice?.let { formatPrice(it) } ?: 0.0
                } else {
                    0.0
                }
                // Если цена равна 0, пробуем использовать сохранённую цену для этого элемента
                val finalPrice = if (fetchedPrice == 0.0) {
                    savedPrices.find { it.name.equals(name, ignoreCase = true) }?.price ?: 0.0
                } else {
                    fetchedPrice
                }
                StonePrice(name, finalPrice)
            }
        }

        // Выполняем запросы для монет (токенов)
        val coinPricesDeferred = coinMarketCapUrls.map { (name, url) ->
            async(Dispatchers.IO) {
                val priceText = CoinMarketCapParser.getPriceFromUrl(url)
                val rawPrice = priceText?.replace("$", "")?.toDoubleOrNull() ?: 0.0
                // Если не удалось спарсить цену, пробуем взять сохранённую цену
                val finalPrice = if (rawPrice == 0.0) {
                    savedPrices.find { it.name.equals(name, ignoreCase = true) }?.price ?: 0.0
                } else {
                    rawPrice
                }
                StonePrice(name, finalPrice)
            }
        }

        val stonePrices = stonePricesDeferred.map { it.await() }
        val coinPrices = coinPricesDeferred.map { it.await() }

        // Объединяем результаты
        val allFetchedPrices = stonePrices + coinPrices

        // Добавляем проверку: если хоть для одного элемента цена равна 0,
        // то считаем, что данные не загрузились корректно, и возвращаем сохранённые цены
        val isDataValid = allFetchedPrices.all { it.price > 0.0 }
        if (!isDataValid) {
            // Логируем информацию о некорректном обновлении
            println("Получены некорректные данные (одна или несколько цен равны 0).")
            // Можно добавить запись в историю обновлений через dataManager.addUpdateHistoryEntry(...)
            return@coroutineScope savedPrices
        }

        // Если все цены валидны, обновляем lastFetchedPrices
        lastFetchedPrices = allFetchedPrices

        // Обновляем курс GST, если удалось получить актуальную цену
        lastFetchedPrices.find { it.name.equals("Green Satoshi Token", ignoreCase = true) }?.let { gst ->
            if (gst.price > 0.0) {
                dataManager.setGSTRate(gst.price)
            }
        }

        // Сохраняем снимок цен (так как данные корректны)
        dataManager.savePriceSnapshot(lastFetchedPrices)

        lastFetchedPrices
    }


    fun getLastFetchedPrices(): List<StonePrice> {
        return lastFetchedPrices
    }

    // Новая функция для форматирования цены
    private fun formatPrice(price: Double): Double {
        val priceStr = price.toInt().toString()
        return when (priceStr.length) {
            3 -> price / 100.0 // Для 3 цифр
            4 -> price / 100.0 // Для 4 цифр
            else -> price // Для остальных случаев оставляем без изменений
        }
    }
}
