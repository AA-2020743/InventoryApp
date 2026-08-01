package com.supermarket.inventory.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProductCacheEntity::class, PendingSaleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productCacheDao(): ProductCacheDao
    abstract fun pendingSaleDao(): PendingSaleDao
}
