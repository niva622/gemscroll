// PriceCheckWorker.kt
package com.example.gemscroll

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class PriceCheckWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "price_updates"
        const val PRICE_THRESHOLD_STONES_KEY = "price_threshold_stones"
        const val PRICE_THRESHOLD_TOKENS_KEY = "price_threshold_tokens"
        const val NOTIFICATIONS_ENABLED_KEY = "notifications_enabled"
        const val TARGET_GST_KEY = "target_gst"
        const val TARGET_GMT_KEY = "target_gmt"
    }

    private val dataManager = DataManager(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!isNotificationsEnabled()) {
                Log.d("PriceCheckWorker", "Уведомления отключены.")
                return@withContext Result.success()
            }

            // Получаем новые цены
            val currentPrices = PriceManager.fetchAllPrices(dataManager)
            val previousPrices = loadPreviousPrices()
            val thresholdStones = dataManager.getPriceChangeThresholdStones()
            val thresholdTokens = dataManager.getPriceChangeThresholdTokens()

            Log.d("PriceCheckWorker", "Текущие цены: $currentPrices")
            Log.d("PriceCheckWorker", "Предыдущие цены: $previousPrices")
            Log.d("PriceCheckWorker", "Порог изменения цены для камней: $thresholdStones%")
            Log.d("PriceCheckWorker", "Порог изменения цены для токенов: $thresholdTokens%")

            // Обрабатываем изменения цен и, если превышен порог, отправляем уведомление
            currentPrices.forEach { currentPrice ->
                val previousPrice = previousPrices.find { it.name == currentPrice.name }?.price ?: 0.0
                val isStone = isStonePrice(currentPrice.name)
                val threshold = if (isStone) thresholdStones else thresholdTokens
                val changePercent = calculateChangePercent(previousPrice, currentPrice.price)

                if (previousPrice != 0.0 && kotlin.math.abs(changePercent) >= threshold) {
                    Log.d("PriceCheckWorker", "Цена изменена для ${currentPrice.name}: $changePercent%")
                    // Независимо от деталей изменения отправляем минимальное уведомление
                    sendNotification(currentPrice.name, "", currentPrice.name.hashCode())
                }
            }

            // Проверка достижения целевых курсов для токенов
            checkTargetPrices(currentPrices)

            // Сохраняем обновлённые цены
            savePrices(currentPrices)

            // Сохраняем снимок цен для анализа трендов
            dataManager.savePriceSnapshot(currentPrices)

            // Обновляем метку последнего обновления
            dataManager.setLastUpdateTimestamp(System.currentTimeMillis())

            // Записываем успешное обновление в историю
            dataManager.addUpdateHistoryEntry(
                status = "Успешно",
                message = "Фоновое обновление цен прошло успешно."
            )

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()

            // Запись ошибки в историю обновлений
            dataManager.addUpdateHistoryEntry(
                status = "Неуспешно",
                message = "Ошибка при фоновом обновлении цен: ${e.localizedMessage}"
            )

            Result.failure()
        }
    }

    private fun isNotificationsEnabled(): Boolean {
        val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return sharedPreferences.getBoolean(NOTIFICATIONS_ENABLED_KEY, true)
    }

    private suspend fun loadPreviousPrices(): List<StonePrice> {
        return withContext(Dispatchers.IO) {
            dataManager.getSavedPrices()
        }
    }

    private suspend fun savePrices(prices: List<StonePrice>) {
        withContext(Dispatchers.IO) {
            dataManager.savePrices(prices)
        }
    }

    private fun calculateChangePercent(previousPrice: Double, currentPrice: Double): Double {
        if (previousPrice == 0.0) return 0.0
        return ((currentPrice - previousPrice) / previousPrice) * 100
    }

    private fun isStonePrice(name: String): Boolean {
        val stoneCategories = listOf("Efficiency", "Luck", "Comfort", "Resilience")
        return stoneCategories.any { name.startsWith(it, ignoreCase = true) }
    }

    private fun checkTargetPrices(currentPrices: List<StonePrice>) {
        val targetGST = getTargetPrice(TARGET_GST_KEY)
        val targetGMT = getTargetPrice(TARGET_GMT_KEY)

        val currentGSTPrice = currentPrices.find { it.name.equals("Green Satoshi Token", ignoreCase = true) }?.price
        val currentGMTPrice = currentPrices.find { it.name.equals("Green Metaverse Token", ignoreCase = true) }?.price

        Log.d("PriceCheckWorker", "Целевой GST: $targetGST, Текущий GST: $currentGSTPrice")
        Log.d("PriceCheckWorker", "Целевой GMT: $targetGMT, Текущий GMT: $currentGMTPrice")

        if (targetGST != null && currentGSTPrice != null && currentGSTPrice >= targetGST) {
            Log.d("PriceCheckWorker", "Целевой курс GST достигнут.")
            sendNotification("", "", "GST".hashCode())
        }

        if (targetGMT != null && currentGMTPrice != null && currentGMTPrice >= targetGMT) {
            Log.d("PriceCheckWorker", "Целевой курс GMT достигнут.")
            sendNotification("", "", "GMT".hashCode())
        }
    }

    /**
     * Функция отправки уведомления.
     * Параметры title и message игнорируются – вместо них формируется минимальное сообщение вида:
     * "gst 0.01, gmt 0.20"
     */
    private fun sendNotification(ignoredTitle: String, ignoredMessage: String, notificationId: Int) {
        createNotificationChannel()

        // Получаем текущие курсы GST и GMT из последнего запроса
        val prices = PriceManager.getLastFetchedPrices()
        val gstPrice = prices.find { it.name.equals("Green Satoshi Token", ignoreCase = true) }?.price ?: 0.0
        val gmtPrice = prices.find { it.name.equals("Green Metaverse Token", ignoreCase = true) }?.price ?: 0.0
        val minimalMessage = "gst " + String.format(Locale.US, "%.4f", gstPrice) +
                ", gmt " + String.format(Locale.US, "%.4f", gmtPrice)

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
            .setContentTitle(minimalMessage)
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(notificationId, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Price Updates"
            val descriptionText = "Notifications for price changes and target prices"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getTargetPrice(key: String): Double? {
        val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val value = sharedPreferences.getString(key, null)
        return value?.toDoubleOrNull()
    }
}
