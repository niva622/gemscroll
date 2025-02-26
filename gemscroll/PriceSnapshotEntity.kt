// PriceSnapshotEntity.kt
package com.example.gemscroll.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_snapshots")
data class PriceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val pricesJson: String // Хранение списка StonePrice в формате JSON
)
