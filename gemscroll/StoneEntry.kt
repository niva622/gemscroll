package com.example.gemscroll

data class StoneEntry(
    val id: Int,
    val date: String,
    val category: String,
    val level: Int,
    val quantity: Int,
    val price: Double,

    // Стоимость открытия сундука в долларах (было)
    val chestOpenCostUsd: Double = 0.0,

    // Новое поле — итоговая стоимость в USD, зафиксированная при добавлении
    val totalUsd: Double = 0.0
)
