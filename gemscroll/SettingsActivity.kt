// SettingsActivity.kt
package com.example.gemscroll

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.math.BigDecimal
import java.math.RoundingMode

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var dataManager: DataManager
        private lateinit var sharedPreferences: SharedPreferences

        // Activity Result Launcher для выбора файла базы данных
        private val importDatabaseLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                importDatabaseFromUri(uri)
            } else {
                Toast.makeText(requireContext(), "Файл не выбран", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_activity, rootKey)

            dataManager = DataManager(requireContext())
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

            // Обновление отображения времени последнего обновления
            updateLastUpdateTime()

            // Обработка нажатия на кнопку "Обновить цены"
            val fetchPricesPreference: Preference? = findPreference("fetch_current_prices")
            fetchPricesPreference?.setOnPreferenceClickListener {
                fetchCurrentPrices()
                true
            }

            // Обработка нажатия на кнопку "Экспортировать базу данных"
            val exportDatabasePreference: Preference? = findPreference("export_database")
            exportDatabasePreference?.setOnPreferenceClickListener {
                exportDatabase()
                true
            }

            // Обработка изменения частоты обновления
            val frequencyPreference: ListPreference? = findPreference("background_update_frequency")
            frequencyPreference?.setOnPreferenceChangeListener { _, newValue ->
                val minutes = newValue.toString().toIntOrNull() ?: 15
                lifecycleScope.launch {
                    dataManager.setBackgroundUpdateFrequency(minutes)
                    // Перезапланировать фоновую задачу с новым интервалом
                    scheduleNewPriceCheckWork()
                }
                true
            }

            // Обработка изменения порога изменения цены для камней
            val thresholdStonesPreference: EditTextPreference? = findPreference("price_threshold_stones")
            thresholdStonesPreference?.setOnPreferenceChangeListener { _, newValue ->
                val threshold = newValue.toString().toDoubleOrNull() ?: 5.0
                lifecycleScope.launch {
                    dataManager.setPriceChangeThresholdStones(threshold)
                }
                true
            }

            // Обработка изменения порога изменения цены для токенов
            val thresholdTokensPreference: EditTextPreference? = findPreference("price_threshold_tokens")
            thresholdTokensPreference?.setOnPreferenceChangeListener { _, newValue ->
                val threshold = newValue.toString().toDoubleOrNull() ?: 5.0
                lifecycleScope.launch {
                    dataManager.setPriceChangeThresholdTokens(threshold)
                }
                true
            }

            // Обработка изменения интервала анализа трендов
            val trendCheckIntervalPreference: ListPreference? = findPreference("trend_check_interval")
            trendCheckIntervalPreference?.setOnPreferenceChangeListener { _, newValue ->
                val hours = newValue.toString().toIntOrNull() ?: 1
                lifecycleScope.launch {
                    dataManager.setTrendCheckInterval(hours) // Сохраняем новое значение
                    scheduleTrendCheck(hours) // Планируем новую работу
                }
                true
            }

            // Обработка переключателя Auto Add
            val autoAddPreference: SwitchPreferenceCompat? = findPreference("auto_add")
            autoAddPreference?.isChecked = dataManager.isAutoAddEnabled()
            autoAddPreference?.setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                lifecycleScope.launch {
                    dataManager.setAutoAdd(isEnabled)
                }
                true
            }

            // Обработка нажатия на кнопку "Импортировать базу данных"
            val importDatabasePreference: Preference? = findPreference("import_database")
            importDatabasePreference?.setOnPreferenceClickListener {
                openFilePicker()
                true
            }

            // Установка текущих значений ListPreference из DataManager
            val currentFrequency = dataManager.getBackgroundUpdateFrequency()
            frequencyPreference?.value = currentFrequency.toString()

            val currentTrendInterval = dataManager.getTrendCheckInterval()
            trendCheckIntervalPreference?.value = currentTrendInterval.toString()

            // Планирование анализа тренда при загрузке настроек
            lifecycleScope.launch {
                scheduleTrendCheck(currentTrendInterval)
            }

            // Обработка нажатия на кнопку "История обновлений"
            val updateHistoryPreference: Preference? = findPreference("last_update_history")
            updateHistoryPreference?.setOnPreferenceClickListener {
                showUpdateHistoryDialog()
                true
            }

            // Новый Preference для расчёта себестоимости сундука с учетом открытия
            val calcCostPreference: Preference? = findPreference("calculate_chest_cost")
            calcCostPreference?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    calculateChestCost()
                }
                true
            }
        }

        private fun openFilePicker() {
            importDatabaseLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3"))
        }

        private fun importDatabaseFromUri(uri: Uri) {
            AlertDialog.Builder(requireContext())
                .setTitle("Импортировать базу данных")
                .setMessage("Вы уверены, что хотите импортировать выбранную базу данных? Это перезапишет текущие данные.")
                .setPositiveButton("Да") { dialog, _ ->
                    performDatabaseImport(uri)
                    dialog.dismiss()
                }
                .setNegativeButton("Нет", null)
                .show()
        }

        private fun performDatabaseImport(uri: Uri) {
            lifecycleScope.launch {
                try {
                    val dbName = "gem_scroll_database"
                    val dbFile = requireContext().getDatabasePath(dbName)
                    dbFile.parentFile?.mkdirs()
                    requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(dbFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: throw IOException("Не удалось открыть поток для выбранного файла.")
                    Toast.makeText(requireContext(), "Импорт базы данных успешно завершен. Перезапустите приложение.", Toast.LENGTH_LONG).show()
                    restartApp()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Ошибка при импорте базы данных: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        private fun restartApp() {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            requireActivity().finish()
        }

        private fun scheduleNewPriceCheckWork() {
            val frequencyMinutes = dataManager.getBackgroundUpdateFrequency()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodicWorkRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(frequencyMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                "PriceCheckWork",
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWorkRequest
            )
        }

        private suspend fun scheduleTrendCheck(hours: Int) {
            androidx.work.WorkManager.getInstance(requireContext()).cancelUniqueWork("TrendCheckWork")
            if (hours <= 0) return
            val workRequest = PeriodicWorkRequestBuilder<TrendCheckWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            androidx.work.WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                "TrendCheckWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        private fun fetchCurrentPrices() {
            lifecycleScope.launch {
                val progressDialog = AlertDialog.Builder(requireContext())
                    .setTitle("Обновление цен")
                    .setMessage("Пожалуйста, подождите...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()
                try {
                    // Получаем обновлённые цены в фоне
                    val prices = withContext(Dispatchers.IO) {
                        PriceManager.fetchAllPrices(dataManager)
                    }
                    withContext(Dispatchers.IO) {
                        dataManager.setLastUpdateTimestamp(System.currentTimeMillis())
                    }
                    progressDialog.dismiss()
                    updateLastUpdateTime()
                    Toast.makeText(requireContext(), "Цены успешно обновлены", Toast.LENGTH_SHORT).show()
                    // Показываем всплывающее окно с текущими ценами
                    PriceDialog(prices).show(parentFragmentManager, "PriceDialog")
                    // Добавляем запись в историю обновлений
                    dataManager.addUpdateHistoryEntry(
                        status = "Успешно",
                        message = "Ручное обновление цен прошло успешно."
                    )
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Ошибка при обновлении цен: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    dataManager.addUpdateHistoryEntry(
                        status = "Неуспешно",
                        message = "Ошибка при ручном обновлении цен: ${e.localizedMessage}"
                    )
                }
            }
        }

        private suspend fun calculateChestCost() {
            // Получаем последний снапшот цен
            val snapshotPrices = withContext(Dispatchers.IO) {
                dataManager.getPriceSnapshotBefore(System.currentTimeMillis())
            }

            if (snapshotPrices.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Себестоимость сундука")
                        .setMessage("Нет данных о ценах (снапшот отсутствует)")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
                return
            }

            // Извлекаем курсы токенов из снапшота
            val gmtRate = snapshotPrices.find { it.name.equals("Green Metaverse Token", ignoreCase = true) }?.price ?: 0.0
            val gstRate = snapshotPrices.find { it.name.equals("Green Satoshi Token", ignoreCase = true) }?.price ?: 0.0

            // Для камней предполагаем, что их названия имеют формат "Категория <уровень>"
            val level1StonePrices = snapshotPrices.filter { price ->
                price.name.endsWith("1") && listOf("Luck", "Resilience", "Efficiency", "Comfort")
                    .any { price.name.startsWith(it) }
            }.map { it.price }

            val level2StonePrices = snapshotPrices.filter { price ->
                price.name.endsWith("2") && listOf("Luck", "Resilience", "Efficiency", "Comfort")
                    .any { price.name.startsWith(it) }
            }.map { it.price }

            // Вычисляем средние значения для каждого уровня
            val level1Average = if (level1StonePrices.isNotEmpty()) level1StonePrices.average() else 0.0
            val level2Average = if (level2StonePrices.isNotEmpty()) level2StonePrices.average() else 0.0
            val sumAverage = level1Average + level2Average

            // Рассчитываем стоимость открытия сундука: 100 GST переводятся сначала в USD, а затем в GMT.
            // Для точности используем BigDecimal и округляем до 4 знаков после запятой.
            val openCostGMT = if (gmtRate > 0) {
                val hundred = BigDecimal("100")
                val gstBD = BigDecimal(gstRate.toString())
                val gmtBD = BigDecimal(gmtRate.toString())
                hundred.multiply(gstBD)
                    .divide(gmtBD, 4, RoundingMode.HALF_UP)
                    .toDouble()
            } else 0.0

            // Дополнительные расчёты:
            val additionalCostUsd = 8700 * gstRate
            val additionalCostFormulaUsd = (7500 * gstRate) + (sumAverage * 12 * gmtRate)

            // Форматируем значения для отображения.
            // Здесь для стоимости открытия используем 4 знака после запятой.
            val formattedLevel1Average = String.format(Locale.US, "%.2f", level1Average)
            val formattedLevel2Average = String.format(Locale.US, "%.2f", level2Average)
            val formattedSumAverage = String.format(Locale.US, "%.2f", sumAverage)
            val formattedOpenCost = String.format(Locale.US, "%.4f", openCostGMT)
            val formattedAdditionalCost = String.format(Locale.US, "%.2f", additionalCostUsd)
            val formattedAdditionalFormula = String.format(Locale.US, "%.2f", additionalCostFormulaUsd)
            // Для отладки можно также отформатировать сами курсы с 4 знаками
            val formattedGstRate = String.format(Locale.US, "%.4f", gstRate)
            val formattedGmtRate = String.format(Locale.US, "%.4f", gmtRate)

            // Выводим результат во всплывающем окне
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Себестоимость сундука")
                    .setMessage(
                        "Среднее значение цены:\n" +
                                "• 1 уровень: $formattedLevel1Average GMT\n" +
                                "• 2 уровень: $formattedLevel2Average GMT\n" +
                                "   Сумма 1-го и 2-го уровней: $formattedSumAverage GMT\n\n" +
                                "Стоимость открытия (100 GST): $formattedOpenCost GMT\n" +
                                "   (Курс GST: $formattedGstRate, Курс GMT: $formattedGmtRate)\n\n" +
                                "Доход в месяц без сундуков: $$formattedAdditionalCost\n" +
                                "Доход в месяц с сундуками: $$formattedAdditionalFormula"
                    )
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }









        private fun exportDatabase() {
            AlertDialog.Builder(requireContext())
                .setTitle("Экспорт базы данных")
                .setMessage("Вы уверены, что хотите экспортировать базу данных?")
                .setPositiveButton("Да") { dialog, _ ->
                    performExport()
                    dialog.dismiss()
                }
                .setNegativeButton("Нет", null)
                .show()
        }

        private fun performExport() {
            lifecycleScope.launch {
                try {
                    val progressDialog = AlertDialog.Builder(requireContext())
                        .setTitle("Экспорт")
                        .setMessage("Экспорт базы данных...")
                        .setCancelable(false)
                        .create()
                    progressDialog.show()
                    val exportedUri = withContext(Dispatchers.IO) {
                        exportDatabaseFile()
                    }
                    progressDialog.dismiss()
                    if (exportedUri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, exportedUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Поделиться базой данных через"))
                    } else {
                        Toast.makeText(requireContext(), "Не удалось экспортировать базу данных", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Ошибка при экспорте: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        private fun exportDatabaseFile(): Uri? {
            val dbName = "gem_scroll_database"
            val dbFile = requireContext().getDatabasePath(dbName)
            if (!dbFile.exists()) {
                Toast.makeText(requireContext(), "Файл базы данных не найден", Toast.LENGTH_LONG).show()
                return null
            }
            val cacheDir = requireContext().cacheDir
            val exportFile = File(cacheDir, "$dbName.db")
            try {
                FileInputStream(dbFile).use { fis ->
                    FileOutputStream(exportFile).use { fos ->
                        fis.copyTo(fos)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Ошибка при копировании файла: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                return null
            }
            return FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                exportFile
            )
        }

        private fun updateLastUpdateTime() {
            lifecycleScope.launch {
                val lastUpdateTime = withContext(Dispatchers.IO) {
                    val lastUpdateMillis = sharedPreferences.getLong("last_update_timestamp", 0L)
                    if (lastUpdateMillis > 0)
                        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(lastUpdateMillis))
                    else
                        "Никогда"
                }
                val lastUpdatePref: Preference? = findPreference("last_update_time")
                lastUpdatePref?.summary = lastUpdateTime
            }
        }

        private fun showUpdateHistoryDialog() {
            lifecycleScope.launch {
                val historyList = dataManager.getUpdateHistory()
                if (historyList.isEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("История обновлений")
                        .setMessage("История обновлений пуста.")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                } else {
                    val dialog = UpdateHistoryDialog(historyList)
                    dialog.show(parentFragmentManager, "UpdateHistoryDialog")
                }
            }
        }
    }
}
