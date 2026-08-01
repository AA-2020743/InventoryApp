package com.supermarket.inventory.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSaleDao {
    @Insert
    suspend fun insert(sale: PendingSaleEntity)

    @Query("SELECT * FROM pending_sales ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSaleEntity>

    @Query("DELETE FROM pending_sales WHERE clientId = :clientId")
    suspend fun delete(clientId: String)

    @Query("SELECT COUNT(*) FROM pending_sales")
    fun countFlow(): Flow<Int>
}
