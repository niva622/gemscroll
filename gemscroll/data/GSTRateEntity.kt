package com.example.gemscroll.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gst_rate")
data class GSTRateEntity(
    @PrimaryKey val id: Int = 1, // Используем фиксированный ID для единственной записи
    val rate: Double?
)