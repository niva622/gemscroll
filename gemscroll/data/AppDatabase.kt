package com.example.gemscroll.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [StoneEntity::class, GSTEntity::class, GSTRateEntity::class, PriceSnapshotEntity::class],
    version = 4, // <-- Увеличиваем версию базы данных с 3 до 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stoneDao(): StoneDao
    abstract fun gstDao(): GSTDao
    abstract fun priceSnapshotDao(): PriceSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Миграция 1 -> 2: добавляем новый столбец для цены открытия сундука
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Добавляем поле chestOpenCostUsd
                database.execSQL(
                    "ALTER TABLE stones ADD COLUMN chestOpenCostUsd REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        // Миграция 2 -> 3: добавляем таблицу price_snapshots
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        pricesJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Новая миграция 3 -> 4: добавляем столбец totalUsd
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Добавляем новое поле totalUsd в таблицу stones
                database.execSQL(
                    "ALTER TABLE stones ADD COLUMN totalUsd REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gem_scroll_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4) // Подключаем все миграции
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
