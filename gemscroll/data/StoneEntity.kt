package com.example.gemscroll.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stones")
data class StoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val category: String,
    val level: Int,
    val quantity: Int,
    val price: Double,

    // Новое поле — стоимость открытия сундука в долларах (было и раньше)
    val chestOpenCostUsd: Double = 0.0,

    // Новое поле — итоговая стоимость камня в USD "на момент создания"
    val totalUsd: Double = 0.0
)
