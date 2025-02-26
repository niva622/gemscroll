// MainActivity.kt
package com.example.gemscroll

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.work.*
import com.example.gemscroll.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity(), StoneAdapter.OnItemClickListener, InputGstAmountDialog.InputGstAmountListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var dataManager: DataManager
    private lateinit var btnAddGST: Button
    private var currentGSTRate: Double = 0.0
    private var loadingDialog: AlertDialog? = null
    private var fetchPricesJob: Job? = null // Добавляем Job для управления корутиной
    private var firstSelectedStone: Stone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataManager = DataManager(this)
        schedulePriceCheck() // Планируем фоновую задачу с учетом настроек

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val stones = listOf(
            Stone("Luck", 1, R.drawable.luck1),
            Stone("Luck", 2, R.drawable.luck2),
            Stone("Efficiency", 1, R.drawable.efficiency1),
            Stone("Efficiency", 2, R.drawable.efficiency2),
            Stone("Resilience", 1, R.drawable.resilience1),
            Stone("Resilience", 2, R.drawable.resilience2),
            Stone("Comfort", 1, R.drawable.comfort1),
            Stone("Comfort", 2, R.drawable.comfort2)
        )

        val adapter = StoneAdapter(stones, this)
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter

        btnAddGST = findViewById(R.id.btnAddGST)

        // Удалены следующие строки:
        // binding.switchAutoAdd.isChecked = dataManager.isAutoAddEnabled()
        // binding.switchAutoAdd.setOnCheckedChangeListener { _, isChecked ->
        //     dataManager.setAutoAdd(isChecked)
        // }

        binding.btnStatistics.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        btnAddGST.setOnClickListener {
            showInputGstAmountDialog()
        }

        // Создание диалога загрузки
        createLoadingDialog()

        fetchAndShowPrices()
    }

    override fun onGstAmountEntered(amount: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val gstRate = dataManager.getGSTRate() ?: 0.0
            if (gstRate > 0.0) {
                val rawTotal = amount * gstRate
                val total = String.format(Locale.US, "%.3f", rawTotal).toDouble()
                dataManager.addGSTEntry(amount, total)

                val decimalFormatSymbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
                    decimalSeparator = ','
                }
                val formatter = DecimalFormat("#,##0.000", decimalFormatSymbols)
                val formattedTotal = formatter.format(total)

                Toast.makeText(this@MainActivity, "Добавлено: $amount GST = $formattedTotal \$", Toast.LENGTH_SHORT).show()

                // Запись успешного обновления в историю
                dataManager.addUpdateHistoryEntry(
                    status = "Успешно",
                    message = "Добавление GST: $amount GST = $formattedTotal \$"
                )
            } else {
                Toast.makeText(this@MainActivity, "Курс GST недоступен", Toast.LENGTH_SHORT).show()

                // Запись неуспешного обновления в историю
                dataManager.addUpdateHistoryEntry(
                    status = "Неуспешно",
                    message = "Добавление GST: Курс GST недоступен"
                )
            }
        }
    }

    private fun schedulePriceCheck() {
        // Получаем частоту обновления из DataManager
        val frequencyMinutes = dataManager.getBackgroundUpdateFrequency()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest =
            PeriodicWorkRequestBuilder<PriceCheckWorker>(frequencyMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PriceCheckWork",
            ExistingPeriodicWorkPolicy.REPLACE, // Заменяем существующую задачу при изменении интервала
            periodicWorkRequest
        )
    }

    private fun showInputGstAmountDialog() {
        val dialog = InputGstAmountDialog()
        dialog.setListener(this)
        dialog.show(supportFragmentManager, "InputGstAmountDialog")
    }

    override fun onItemClick(stone: Stone) {
        // Если первый камень ещё не выбран, сохраняем его
        if (firstSelectedStone == null) {
            firstSelectedStone = stone
            Toast.makeText(
                this,
                "Выбран первый камень: ${stone.category} ${stone.level} lvl. Выберите второй камень с другим уровнем.",
                Toast.LENGTH_SHORT
            ).show()
            // (Опционально можно добавить визуальное выделение выбранного элемента)
        } else {
            val firstStone = firstSelectedStone!!
            // Если уровень второго камня равен уровню первого, выводим предупреждение и сбрасываем выбор
            if (firstStone.level == stone.level) {
                Toast.makeText(
                    this,
                    "Оба камня имеют один и тот же уровень. Выберите второй камень другого уровня.",
                    Toast.LENGTH_SHORT
                ).show()
                firstSelectedStone = null
                return
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val isAutoAdd = dataManager.isAutoAddEnabled()

                        // Если автодобавление включено, используем заданные количества
                        val quantity1: Int
                        val quantity2: Int

                        if (isAutoAdd) {
                            // Например, если уровень 1 – 3 шт, если уровень 2 – 1 шт
                            quantity1 = if (firstStone.level == 1) 3 else 1
                            quantity2 = if (stone.level == 1) 3 else 1
                        } else {
                            // Если автодобавление выключено, запрашиваем количество через диалоги
                            quantity1 = suspendCoroutine { continuation ->
                                StoneQuantityDialog(firstStone) { qty ->
                                    continuation.resume(qty)
                                }.show(supportFragmentManager, "StoneQuantityDialog1")
                            }
                            quantity2 = suspendCoroutine { continuation ->
                                StoneQuantityDialog(stone) { qty ->
                                    continuation.resume(qty)
                                }.show(supportFragmentManager, "StoneQuantityDialog2")
                            }
                        }

                        // Получаем последние цены для выбранных камней
                        val prices = PriceManager.getLastFetchedPrices()
                        val stone1Name = "${firstStone.category} ${firstStone.level}"
                        val stone2Name = "${stone.category} ${stone.level}"
                        val stone1Price = prices.find { it.name.equals(stone1Name, ignoreCase = true) }?.price ?: 0.0
                        val stone2Price = prices.find { it.name.equals(stone2Name, ignoreCase = true) }?.price ?: 0.0

                        // Сохраняем записи для каждого камня (используем метод dataManager.saveOpening)
                        dataManager.saveOpening(firstStone, quantity1, stone1Price)
                        dataManager.saveOpening(stone, quantity2, stone2Price)

                        Toast.makeText(
                            this@MainActivity,
                            "Сундук сформирован из:\n" +
                                    "${firstStone.category} ${firstStone.level} lvl – $quantity1 шт\n" +
                                    "${stone.category} ${stone.level} lvl – $quantity2 шт",
                            Toast.LENGTH_LONG
                        ).show()

                        // Добавляем запись в историю обновлений
                        dataManager.addUpdateHistoryEntry(
                            status = "Успешно",
                            message = "Сформирован сундук из: ${firstStone.category} ${firstStone.level} lvl ($quantity1 шт) и " +
                                    "${stone.category} ${stone.level} lvl ($quantity2 шт)"
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Ошибка при выборе количества: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        // Сбрасываем выбор после формирования сундука
                        firstSelectedStone = null
                    }
                }
            }
        }
    }


    private fun fetchAndShowPrices() {
        // Отменяем предыдущую задачу, если она есть
        fetchPricesJob?.cancel()

        fetchPricesJob = CoroutineScope(Dispatchers.Main).launch {
            showLoadingDialog()
            try {
                val prices = withContext(Dispatchers.IO) {
                    PriceManager.fetchAllPrices(dataManager)
                }
                currentGSTRate = dataManager.getGSTRate() ?: 0.0

                // Проверяем, не была ли корутина отменена
                if (isActive) {
                    val priceDialog = PriceDialog(prices)
                    priceDialog.show(supportFragmentManager, "PriceDialog")

                    // Запись успешного обновления в историю
                    dataManager.addUpdateHistoryEntry(
                        status = "Успешно",
                        message = "Ручное обновление цен прошло успешно."
                    )
                }
            } catch (e: CancellationException) {
                Log.i("MainActivity", "Загрузка цен отменена")
                // Запись отмененного обновления в историю
                dataManager.addUpdateHistoryEntry(
                    status = "Неуспешно",
                    message = "Ручное обновление цен отменено."
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка при загрузке цен: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()

                // Запись неуспешного обновления в историю
                dataManager.addUpdateHistoryEntry(
                    status = "Неуспешно",
                    message = "Ошибка при ручном обновлении цен: ${e.localizedMessage}"
                )
            } finally {
                if (isActive) {
                    dismissLoadingDialog()
                }
            }
        }
    }

    private fun createLoadingDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Загрузка")
            .setMessage("Пожалуйста, подождите...")
            .setCancelable(true) // Делаем диалог отменяемым
            .setOnCancelListener { // Добавляем слушатель отмены
                fetchPricesJob?.cancel() // Отменяем корутину
            }
        loadingDialog = builder.create()
    }

    private fun showLoadingDialog() {
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
    }
}
