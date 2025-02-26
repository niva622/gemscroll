package com.example.gemscroll.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gst_entries")
data class GSTEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val amount: Int,
    val total: Double
)