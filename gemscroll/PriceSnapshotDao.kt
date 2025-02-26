// PriceSnapshotDao.kt
package com.example.gemscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PriceSnapshotDao {
    @Insert
    suspend fun insertSnapshot(snapshot: PriceSnapshotEntity)

    @Query("SELECT * FROM price_snapshots WHERE timestamp <= :targetTimestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getSnapshotBefore(targetTimestamp: Long): PriceSnapshotEntity?

    @Query("DELETE FROM price_snapshots WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteOldSnapshots(thresholdTimestamp: Long)
}
