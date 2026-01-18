package com.pcexplorer.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the application.
 */
@Database(
    entities = [TransferEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao

    companion object {
        const val DATABASE_NAME = "pc_explorer_db"
    }
}
