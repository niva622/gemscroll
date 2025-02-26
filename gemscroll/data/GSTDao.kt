package com.example.gemscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface GSTDao {
    @Insert
    suspend fun insert(gstEntry: GSTEntity)

    @Query("SELECT * FROM gst_entries ORDER BY date DESC")
    suspend fun getAllGSTEntries(): List<GSTEntity>

    @Query("DELETE FROM gst_entries WHERE id = (SELECT MAX(id) FROM gst_entries)")
    suspend fun deleteLastGSTEntry()

    @Query("SELECT * FROM gst_rate WHERE id = 1")
    suspend fun getGSTRate(): GSTRateEntity?

    @Insert
    suspend fun insertGSTRate(gstRate: GSTRateEntity)

    @Update
    suspend fun updateGSTRate(gstRate: GSTRateEntity)

}