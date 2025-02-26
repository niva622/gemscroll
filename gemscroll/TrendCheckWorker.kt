// TrendCheckWorker.kt
package com.example.gemscroll

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import android.util.Log // Добавлено для логирования

class TrendCheckWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "trend_updates"
    }

    private val dataManager = DataManager(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val trendIntervalHours = getTrendInterval()
            Log.d("TrendCheckWorker", "Текущий интервал тренда: $trendIntervalHours часов")
            if (trendIntervalHours <= 0) {
                return@withContext Result.failure()
            }

            val currentTimestamp = System.currentTimeMillis()
            val targetTimestamp = currentTimestamp - trendIntervalHours * 60 * 60 * 1000L

            val currentPrices = PriceManager.fetchAllPrices(dataManager)
            val previousPrices = dataManager.getPriceSnapshotBefore(targetTimestamp)

            if (previousPrices == null) {
                Log.d("TrendCheckWorker", "Снимок цен для интервала $trendIntervalHours часов не найден")
                return@withContext Result.success()
            }

            // Получаем пороги изменения цен
            val thresholdStones = dataManager.getPriceChangeThresholdStones() // 10.0%
            val thresholdTokens = dataManager.getPriceChangeThresholdTokens() // 1.0%

            Log.d("TrendCheckWorker", "Порог для камней: $thresholdStones%, для токенов: $thresholdTokens%")

            // Сравнение текущих цен с предыдущими
            val trends = mutableListOf<String>()

            // Создаём карты для быстрого доступа
            val previousPriceMap = previousPrices.associateBy { it.name }
            val currentPriceMap = currentPrices.associateBy { it.name }

            currentPriceMap.forEach { (name, currentPrice) ->
                val prevPrice = previousPriceMap[name]
                if (prevPrice != null && prevPrice.price > 0.0) {
                    val changePercent = ((currentPrice.price - prevPrice.price) / prevPrice.price) * 100
                    val isStone = isStonePrice(name)
                    val threshold = if (isStone) thresholdStones else thresholdTokens

                    if (Math.abs(changePercent) >= threshold) {
                        val trendType = if (changePercent > 0) "вырос" else "упал"
                        val formattedChange = String.format(Locale.US, "%.2f", kotlin.math.abs(changePercent))
                        val abbreviatedName = abbreviateName(name)
                        val formattedCurrentPrice = formatPriceForNotification(currentPrice.price, isStone)
                        trends.add("курс $abbreviatedName $trendType на $formattedChange% до $formattedCurrentPrice")
                        Log.d("TrendCheckWorker", "Цена $name изменилась на $formattedChange%, новая цена: $formattedCurrentPrice")
                    }
                }
            }

            if (trends.isNotEmpty()) {
                val message = "$trendIntervalHours ч. назад  ${trends.joinToString(", ")}."
                Log.d("TrendCheckWorker", "Отправка уведомления: $message")
                sendNotification(message)
            } else {
                Log.d("TrendCheckWorker", "Нет значительных изменений цен за последние $trendIntervalHours часов")
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("TrendCheckWorker", "Ошибка: ${e.localizedMessage}")
            Result.failure()
        }
    }

    private fun getTrendInterval(): Int {
        // Получаем интервал анализа трендов из DataManager
        val trendInterval = dataManager.getTrendCheckInterval()
        return trendInterval
    }

    private fun isStonePrice(name: String): Boolean {
        // Определяем, является ли цена камнем
        val stoneCategories = listOf("Efficiency", "Luck", "Comfort", "Resilience")
        return stoneCategories.any { name.startsWith(it, ignoreCase = true) }
    }

    /**
     * Функция для сокращения названий токенов.
     * Возвращает аббревиатуру, если полное название токена известно.
     * Иначе возвращает исходное название.
     */
    private fun abbreviateName(name: String): String {
        return when (name) {
            "Green Metaverse Token" -> "GMT"
            "Green Satoshi Token" -> "GST"
            else -> name
        }
    }

    private fun sendNotification(message: String) {
        createNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Анализ тренда цен")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Для длинных сообщений
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify("TrendCheck".hashCode(), builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Trend Updates"
            val descriptionText = "Уведомления об изменении трендов цен"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Метод для форматирования цены в уведомлении
    private fun formatPriceForNotification(price: Double, isStone: Boolean): String {
        return if (isStone) {
            // Для камней используем форматирование с запятой и двумя знаками после запятой
            String.format(Locale("ru", "RU"), "%.2f GMT", price)
        } else {
            // Для токенов используем форматирование с точкой и четырьмя знаками после запятой
            String.format(Locale.US, "$%.4f", price)
        }
    }
}
