package com.supermarket.inventory.di

import android.content.Context
import androidx.room.Room
import com.supermarket.inventory.data.local.AppDatabase
import com.supermarket.inventory.data.local.PendingSaleDao
import com.supermarket.inventory.data.local.ProductCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        // This DB only holds a transient product cache and a pending-sales
        // queue meant to drain quickly - never permanent records - so a
        // schema bump can safely just wipe and recreate it rather than
        // needing a hand-written Migration.
        Room.databaseBuilder(context, AppDatabase::class.java, "inventory_local.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProductCacheDao(db: AppDatabase): ProductCacheDao = db.productCacheDao()

    @Provides
    fun providePendingSaleDao(db: AppDatabase): PendingSaleDao = db.pendingSaleDao()
}
