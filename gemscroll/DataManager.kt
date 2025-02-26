package com.example.gemscroll

import android.content.Context
import android.content.SharedPreferences
import com.example.gemscroll.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log // Добавлено для логирования

class DataManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("gemScrollPrefs", Context.MODE_PRIVATE)
    private val autoAddKey = "autoAdd"
    private val backgroundFrequencyKey = "background_update_frequency"

    private val database = AppDatabase.getDatabase(context)
    private val stoneDao = database.stoneDao()
    private val gstDao = database.gstDao()
    private val priceSnapshotDao = database.priceSnapshotDao()

    // Новые ключи для порогов изменения цены
    private val priceThresholdStonesKey = "price_threshold_stones"
    private val priceThresholdTokensKey = "price_threshold_tokens"

    // Новый ключ для интервала анализа трендов
    private val trendCheckIntervalKey = "trend_check_interval"

    // Новый ключ для истории обновлений цен
    private val updateHistoryKey = "update_history"

    // Получение порога изменения цены для камней
    fun getPriceChangeThresholdStones(): Double {
        return sharedPreferences.getString(priceThresholdStonesKey, "10.0")?.toDoubleOrNull() ?: 10.0
    }
    suspend fun addUpdateHistoryEntry(status: String, message: String? = null) {
        withContext(Dispatchers.IO) {
            val gson = Gson()
            val existingHistoryJson = sharedPreferences.getString(updateHistoryKey, null)
            val type = object : TypeToken<MutableList<UpdateHistoryEntry>>() {}.type
            val historyList: MutableList<UpdateHistoryEntry> = if (existingHistoryJson != null) {
                gson.fromJson(existingHistoryJson, type) ?: mutableListOf()
            } else {
                mutableListOf()
            }

            // Форматируем текущую дату и время
            val currentTimestamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())

            // Создаём новую запись
            val newEntry = UpdateHistoryEntry(
                timestamp = currentTimestamp,
                status = status,
                message = message
            )

            // Добавляем в начало списка
            historyList.add(0, newEntry)

            // Оставляем только последние 20 записей
            if (historyList.size > 20) {
                historyList.removeAt(historyList.size - 1)
            }

            // Сохраняем обратно в SharedPreferences
            val updatedHistoryJson = gson.toJson(historyList)
            sharedPreferences.edit().putString(updateHistoryKey, updatedHistoryJson).apply()

            Log.d("DataManager", "Добавлена запись в историю обновлений: $newEntry")
        }
    }

    suspend fun getUpdateHistory(): List<UpdateHistoryEntry> {
        return withContext(Dispatchers.IO) {
            val gson = Gson()
            val existingHistoryJson = sharedPreferences.getString(updateHistoryKey, null)
            val type = object : TypeToken<List<UpdateHistoryEntry>>() {}.type
            val historyList: List<UpdateHistoryEntry> = if (existingHistoryJson != null) {
                gson.fromJson(existingHistoryJson, type) ?: emptyList()
            } else {
                emptyList()
            }
            Log.d("DataManager", "Получена история обновлений: $historyList")
            historyList
        }
    }




    // Установка порога изменения цены для камней
    suspend fun setPriceChangeThresholdStones(threshold: Double) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putString(priceThresholdStonesKey, threshold.toString()).apply()
            Log.d("DataManager", "Порог изменения цены для камней установлен на $threshold%")
        }
    }

    // Получение порога изменения цены для токенов
    fun getPriceChangeThresholdTokens(): Double {
        return sharedPreferences.getString(priceThresholdTokensKey, "1.0")?.toDoubleOrNull() ?: 1.0
    }

    // Установка порога изменения цены для токенов
    suspend fun setPriceChangeThresholdTokens(threshold: Double) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putString(priceThresholdTokensKey, threshold.toString()).apply()
            Log.d("DataManager", "Порог изменения цены для токенов установлен на $threshold%")
        }
    }

    // Получение интервала анализа трендов в часах
    fun getTrendCheckInterval(): Int {
        return sharedPreferences.getInt(trendCheckIntervalKey, 1) // Значение по умолчанию 1 час
    }

    // Установка интервала анализа трендов
    suspend fun setTrendCheckInterval(hours: Int) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putInt(trendCheckIntervalKey, hours).apply()
            Log.d("DataManager", "Интервал анализа тренда установлен на $hours часа(ов)")
        }
    }

    suspend fun getGmtPrice(): Double = withContext(Dispatchers.IO) {
        // Получаем курс GMT из сохранённых цен
        getSavedPrices()
            .find { it.name.equals("Green Metaverse Token", true) }
            ?.price ?: 0.0
    }

    companion object {
        private const val PRICES_PREFS_KEY = "prices_prefs"
    }

    suspend fun savePrices(prices: List<StonePrice>) {
        withContext(Dispatchers.IO) {
            val json = Gson().toJson(prices)
            sharedPreferences.edit().putString(PRICES_PREFS_KEY, json).apply()
            Log.d("DataManager", "Цены сохранены: $json")
        }
    }

    suspend fun setBackgroundUpdateFrequency(minutes: Int) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putInt(backgroundFrequencyKey, minutes).apply()
            Log.d("DataManager", "Частота обновления цен установлена на каждые $minutes минут")
        }
    }

    fun getBackgroundUpdateFrequency(): Int {
        // Возвращаем значение по умолчанию 15 минут, если значение не установлено
        return sharedPreferences.getInt(backgroundFrequencyKey, 15)
    }

    suspend fun getSavedPrices(): List<StonePrice> {
        return withContext(Dispatchers.IO) {
            val json = sharedPreferences.getString(PRICES_PREFS_KEY, null)
            if (json != null) {
                val prices = Gson().fromJson<List<StonePrice>>(json, object : TypeToken<List<StonePrice>>() {}.type)
                Log.d("DataManager", "Считаны сохранённые цены: $prices")
                prices
            } else {
                Log.d("DataManager", "Сохранённые цены отсутствуют")
                emptyList()
            }
        }
    }

    // Сохранение открытия сундука (камней)
    suspend fun saveOpening(stone: Stone, quantity: Int, price: Double) {
        // Формируем дату
        val date = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        // Узнаём текущий курс GST (для открытия сундука)
        val gstRate = getGSTRate() ?: 0.0
        // Стоимость открытия: 100 GST, переводим в $
        val chestOpenCostUsd = 100.0 * gstRate

        // Узнаём текущий курс GMT, чтобы зафиксировать цену камня в USD
        val currentGmtRate = getGmtPrice()

        // Итого по данному камню (price = цена в GMT за 1 шт),
        // totalUsd = price * quantity * текущий курс GMT
        val totalUsd = price * quantity * currentGmtRate

        // Сохраняем запись о камне
        val stoneEntity = StoneEntity(
            date = date,
            category = stone.category,
            level = stone.level,
            quantity = quantity,
            price = price,
            chestOpenCostUsd = chestOpenCostUsd,
            totalUsd = totalUsd
        )
        withContext(Dispatchers.IO) {
            stoneDao.insert(stoneEntity)
            Log.d("DataManager", "Сохранена запись камня: $stoneEntity")
        }
    }



    suspend fun setLastUpdateTimestamp(timestamp: Long) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().putLong("last_update_timestamp", timestamp).apply()
            Log.d("DataManager", "Последнее обновление установлено на $timestamp")
        }
    }

    // Добавление GST-записи
    suspend fun addGSTEntry(amount: Int, total: Double) {
        val date = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val gstEntity = GSTEntity(date = date, amount = amount, total = total)
        withContext(Dispatchers.IO) {
            gstDao.insert(gstEntity)
            Log.d("DataManager", "Добавлена GST-запись: $gstEntity")
        }
    }

    // Получение всех записей камней
    suspend fun getStoneEntries(): List<StoneEntry> {
        return withContext(Dispatchers.IO) {
            stoneDao.getAllStones().map {
                StoneEntry(
                    id = it.id,
                    date = it.date,
                    category = it.category,
                    level = it.level,
                    quantity = it.quantity,
                    price = it.price,
                    chestOpenCostUsd = it.chestOpenCostUsd,
                    totalUsd = it.totalUsd
                )
            }.also { Log.d("DataManager", "Получены все записи камней: $it") }
        }
    }

    suspend fun deleteStoneEntries(entries: List<StoneEntry>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val stoneEntities = entries.map { entry ->
                    StoneEntity(
                        id = entry.id,
                        date = entry.date,
                        category = entry.category,
                        level = entry.level,
                        quantity = entry.quantity,
                        price = entry.price,
                        chestOpenCostUsd = entry.chestOpenCostUsd
                    )
                }
                stoneDao.deleteStones(stoneEntities)
                Log.d("DataManager", "Удалены записи камней: $stoneEntities")
                true
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("DataManager", "Ошибка при удалении записей камней: ${e.localizedMessage}")
                false
            }
        }
    }

    // Получение всех GST-записей
    suspend fun getGSTEntries(): List<GSTEntry> {
        return withContext(Dispatchers.IO) {
            gstDao.getAllGSTEntries().map {
                GSTEntry(it.date, it.amount, it.total)
            }.also { Log.d("DataManager", "Получены все GST-записи: $it") }
        }
    }

    // Установка курса GST
    suspend fun setGSTRate(rate: Double) {
        withContext(Dispatchers.IO) {
            val existingRate = gstDao.getGSTRate()
            if (existingRate != null) {
                // Обновляем существующую запись
                gstDao.updateGSTRate(existingRate.copy(rate = rate))
                Log.d("DataManager", "Курс GST обновлён на $rate")
            } else {
                // Вставляем новую запись
                gstDao.insertGSTRate(GSTRateEntity(rate = rate))
                Log.d("DataManager", "Курс GST установлен на $rate")
            }
        }
    }

    suspend fun getGSTRate(): Double? {
        return withContext(Dispatchers.IO) {
            val rate = gstDao.getGSTRate()?.rate
            Log.d("DataManager", "Считан курс GST: $rate")
            rate
        }
    }

    // Удаление последней записи камней
    suspend fun deleteLastStoneEntry(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                stoneDao.deleteLastTwoStones()
                Log.d("DataManager", "Удалены последние две записи камней")
                true
            } catch (e: Exception) {
                Log.e("DataManager", "Ошибка при удалении последних двух записей камней: ${e.localizedMessage}")
                false
            }
        }
    }

    // Удаление последней GST-записи
    suspend fun deleteLastGSTEntry(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                gstDao.deleteLastGSTEntry()
                Log.d("DataManager", "Удалена последняя GST-запись")
                true
            } catch (e: Exception) {
                Log.e("DataManager", "Ошибка при удалении последней GST-записи: ${e.localizedMessage}")
                false
            }
        }
    }

    // Сохранение состояния переключателя (используем SharedPreferences)
    fun setAutoAdd(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(autoAddKey, enabled).apply()
        Log.d("DataManager", "Авто-добавление установлено на: $enabled")
    }

    // Получение состояния переключателя (используем SharedPreferences)
    fun isAutoAddEnabled(): Boolean {
        val enabled = sharedPreferences.getBoolean(autoAddKey, true)
        Log.d("DataManager", "Авто-добавление: $enabled")
        return enabled
    }

    // Новые методы для PriceSnapshots

    // Сохранение снимка цен с текущим временем
    suspend fun savePriceSnapshot(prices: List<StonePrice>) {
        withContext(Dispatchers.IO) {
            val gson = Gson()
            val pricesJson = gson.toJson(prices)
            val timestamp = System.currentTimeMillis()
            val snapshot = PriceSnapshotEntity(
                timestamp = timestamp,
                pricesJson = pricesJson
            )
            priceSnapshotDao.insertSnapshot(snapshot)
            Log.d("DataManager", "Сохранён снимок цен: $snapshot")
            // Опционально: удаляем старые снимки (например, старше 7 дней)
            val sevenDaysMillis = 7 * 24 * 60 * 60 * 1000L
            priceSnapshotDao.deleteOldSnapshots(timestamp - sevenDaysMillis)
            Log.d("DataManager", "Удалены старые снимки цен")
        }
    }

    // Получение снимка цен до определенного времени
    suspend fun getPriceSnapshotBefore(targetTimestamp: Long): List<StonePrice>? {
        return withContext(Dispatchers.IO) {
            val snapshot = priceSnapshotDao.getSnapshotBefore(targetTimestamp)
            if (snapshot != null) {
                val gson = Gson()
                val type = object : TypeToken<List<StonePrice>>() {}.type
                val prices = gson.fromJson<List<StonePrice>>(snapshot.pricesJson, type)
                Log.d("DataManager", "Считан снимок цен: $prices")
                prices
            } else {
                Log.d("DataManager", "Снимок цен для timestamp $targetTimestamp не найден")
                null
            }
        }
    }
}
