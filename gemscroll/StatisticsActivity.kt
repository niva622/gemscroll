// StatisticsActivity.kt
package com.example.gemscroll

import android.widget.TextView
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gemscroll.databinding.ActivityStatisticsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import android.util.Log // Добавьте этот импорт

class StatisticsActivity : AppCompatActivity(), ChestAdapter.OnChestClickListener {

    private lateinit var binding: ActivityStatisticsBinding
    private lateinit var dataManager: DataManager
    private lateinit var chestAdapter: ChestAdapter
    private lateinit var textViewLastUpdate: TextView // Добавьте эту строку

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Инициализация View Binding
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация DataManager
        dataManager = DataManager(this)

        // Настройка RecyclerView для камней
        setupRecyclerView()

        // Загрузка и отображение статистики камней
        loadStoneStatistics()

        // Загрузка и отображение статистики GST
        loadGSTStatistics()

        // Кнопка "Рассчитать итоги"
        binding.btnCalculateTotals.setOnClickListener {
            lifecycleScope.launch {
                val stoneEntries = withContext(Dispatchers.IO) {
                    dataManager.getStoneEntries()
                }

                val gmtRate = withContext(Dispatchers.IO) {
                    dataManager.getGmtPrice()
                }

                // Сумма сундуков в GMT
                val chestTotalGMT = stoneEntries.sumOf { it.price * it.quantity }
                // Переводим в USD
                val chestTotalUSD = chestTotalGMT * gmtRate

                val gstEntries = withContext(Dispatchers.IO) {
                    dataManager.getGSTEntries()
                }

                // Сумма GST уже в долларах (total хранится в USD)
                val gstTotalUSD = gstEntries.sumOf { it.total }

                val message = String.format(
                    Locale.US,
                    "Сумма сундуков: %.2f GMT (%.2f $)\nСумма GST: %.2f $",
                    chestTotalGMT, chestTotalUSD, gstTotalUSD
                )

                AlertDialog.Builder(this@StatisticsActivity)
                    .setTitle("Итоги")
                    .setMessage(message)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                        showStonesTotalDialog(stoneEntries)
                    }
                    .show()
            }
        }

        // Удаление последней GST-записи
        binding.btnDeleteLastGST.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удалить последнюю GST-запись")
                .setMessage("Вы уверены, что хотите удалить последнюю GST-запись из статистики?")
                .setPositiveButton("Да") { _, _ ->
                    lifecycleScope.launch {
                        val success = dataManager.deleteLastGSTEntry()
                        if (success) {
                            Toast.makeText(
                                this@StatisticsActivity,
                                "Последняя GST-запись удалена",
                                Toast.LENGTH_SHORT
                            ).show()
                            loadGSTStatistics()
                        } else {
                            Toast.makeText(
                                this@StatisticsActivity,
                                "Нет GST-записей для удаления",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .setNegativeButton("Нет", null)
                .show()
        }
    }

    private fun showStonesTotalDialog(stoneEntries: List<StoneEntry>) {
        // Группируем камни по категории и уровню и суммируем количество
        val groupedStones = stoneEntries.groupBy { Pair(it.category, it.level) }
            .map { (pair, entries) ->
                Triple(pair.first, pair.second, entries.sumOf { it.quantity })
            }

        val stonesTotalDialog = StonesTotalDialog(groupedStones)
        stonesTotalDialog.show(supportFragmentManager, "StonesTotalDialog")
    }

    private fun setupRecyclerView() {
        chestAdapter = ChestAdapter(emptyList(), this)
        binding.recyclerViewStatistics.apply {
            layoutManager = LinearLayoutManager(this@StatisticsActivity)
            adapter = chestAdapter
        }
    }

    override fun onChestClick(chest: Chest) {
        showDeleteChestDialog(chest)
    }

    private fun showDeleteChestDialog(chest: Chest) {
        AlertDialog.Builder(this)
            .setTitle("Удалить сундук")
            .setMessage("Вы уверены, что хотите удалить сундук №${chest.number} от ${chest.date}?")
            .setPositiveButton("Да") { _, _ ->
                lifecycleScope.launch {
                    val success = dataManager.deleteStoneEntries(chest.entries)
                    if (success) {
                        Toast.makeText(
                            this@StatisticsActivity,
                            "Сундук удалён",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadStoneStatistics()
                    } else {
                        Toast.makeText(
                            this@StatisticsActivity,
                            "Ошибка при удалении сундука",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun loadStoneStatistics() {
        lifecycleScope.launch {
            val stoneEntries = withContext(Dispatchers.IO) {
                dataManager.getStoneEntries()
            }

            if (stoneEntries.isEmpty()) {
                binding.textViewStatistics.visibility = View.VISIBLE
                binding.textViewStatistics.text = "Статистика камней пуста"
                binding.recyclerViewStatistics.visibility = View.GONE
                return@launch
            }

            val chestList = mutableListOf<Chest>()
            var chestNumber = 1

            // Для форматирования дат
            fun formatDate(dateTime: String): String {
                val inputFormat = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd:MM:yyyy", Locale.getDefault())
                return try {
                    val date = inputFormat.parse(dateTime)
                    if (date != null) outputFormat.format(date) else dateTime
                } catch (e: ParseException) {
                    dateTime
                }
            }

            // Новый алгоритм формирования сундуков: ищем пару последовательных камней с разными уровнями.
            var index = 0
            while (index < stoneEntries.size - 1) {
                val first = stoneEntries[index]
                val second = stoneEntries[index + 1]
                // Если уровни камней различны – создаём сундук.
                if (first.level != second.level) {
                    var chestSumGMT = 0.0
                    var chestSumUSD = 0.0
                    var chestDate = ""

                    // Берём цену открытия (предполагаем, что для двух камней она одинаковая, берём из первой записи)
                    val openingCostUsd = first.chestOpenCostUsd

                    listOf(first, second).forEachIndexed { i, entry ->
                        chestSumGMT += entry.price * entry.quantity
                        chestSumUSD += entry.totalUsd
                        if (i == 0) {
                            chestDate = formatDate(entry.date)
                        }
                    }

                    // Округляем суммы до двух знаков
                    chestSumGMT = String.format(Locale.US, "%.2f", chestSumGMT).toDouble()
                    chestSumUSD = String.format(Locale.US, "%.2f", chestSumUSD).toDouble()

                    val chest = Chest(
                        number = chestNumber,
                        date = chestDate,
                        entries = listOf(first, second),
                        sumGMT = chestSumGMT,
                        sumUSD = chestSumUSD,
                        openingCostUsd = openingCostUsd
                    )
                    chestList.add(chest)
                    chestNumber++
                    index += 2  // переходим к следующей паре
                } else {
                    // Если уровни одинаковы – пропускаем первую запись и пробуем со следующей.
                    index++
                }
            }

            if (chestList.isEmpty()) {
                binding.textViewStatistics.visibility = View.VISIBLE
                binding.textViewStatistics.text = "Нет сундуков с камнями разных уровней"
                binding.recyclerViewStatistics.visibility = View.GONE
            } else {
                // Обновляем адаптер RecyclerView
                chestAdapter = ChestAdapter(chestList, this@StatisticsActivity)
                binding.recyclerViewStatistics.adapter = chestAdapter
                binding.recyclerViewStatistics.visibility = View.VISIBLE
                binding.textViewStatistics.visibility = View.GONE
            }
        }
    }



    private fun loadGSTStatistics() {
        lifecycleScope.launch {
            val gstEntries = withContext(Dispatchers.IO) {
                dataManager.getGSTEntries()
            }

            if (gstEntries.isEmpty()) {
                binding.textViewGSTStatistics.visibility = View.VISIBLE
                binding.textViewGSTStatistics.text = "GST статистика пуста"
            } else {
                binding.textViewGSTStatistics.visibility = View.VISIBLE
                val gstMessage = StringBuilder()

                fun formatDate(dateTime: String): String {
                    val inputFormat = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("dd:MM:yyyy", Locale.getDefault())
                    return try {
                        val date = inputFormat.parse(dateTime)
                        if (date != null) outputFormat.format(date) else dateTime
                    } catch (e: ParseException) {
                        dateTime
                    }
                }

                val decimalFormatSymbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
                    decimalSeparator = ','
                }
                val formatter = DecimalFormat("#,##0.00", decimalFormatSymbols)

                gstEntries.forEach { entry ->
                    val formattedDate = formatDate(entry.date)
                    val formattedTotal = formatter.format(entry.total)
                    gstMessage.append("$formattedDate - ${entry.amount} GST $formattedTotal$\n")
                }
                binding.textViewGSTStatistics.text = gstMessage.toString().trimEnd()
            }
        }
    }
}
