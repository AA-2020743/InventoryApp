package com.supermarket.inventory.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductCacheEntity)

    @Query("SELECT * FROM product_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductCacheEntity?

    @Query("SELECT * FROM product_cache WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductCacheEntity?

    @Query("SELECT * FROM product_cache WHERE active = 1 AND name LIKE '%' || :query || '%' LIMIT 6")
    suspend fun searchByName(query: String): List<ProductCacheEntity>

    @Query("UPDATE product_cache SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: String)
}
