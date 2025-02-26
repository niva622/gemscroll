package com.example.gemscroll.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StoneDao {
    @Insert
    suspend fun insert(stone: StoneEntity)

    @Query("SELECT * FROM stones ORDER BY date DESC")
    suspend fun getAllStones(): List<StoneEntity>

    @Query("DELETE FROM stones WHERE id IN (SELECT id FROM stones ORDER BY date DESC LIMIT 2)")
    suspend fun deleteLastTwoStones()

    // Новый метод для удаления конкретных записей камней
    @Delete
    suspend fun deleteStones(stones: List<StoneEntity>)
}