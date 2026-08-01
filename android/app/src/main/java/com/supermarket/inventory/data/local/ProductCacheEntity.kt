package com.supermarket.inventory.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.supermarket.inventory.data.remote.dto.ProductDto

/**
 * A locally-persisted mirror of the last-known product list, kept up to
 * date opportunistically whenever the server answers a products request
 * successfully. It exists so barcode/name lookup on the Sell screen still
 * works when the phone has no connectivity - the value is a snapshot, so
 * quantity here can drift from the server (see [ProductCacheDao.updateQuantity],
 * applied when an offline sale is queued so a second offline sale of the
 * same item doesn't look like it still has full stock).
 */
@Entity(tableName = "product_cache")
data class ProductCacheEntity(
    @PrimaryKey val id: String,
    val barcode: String?,
    val name: String,
    val imageUrl: String?,
    val category: String?,
    val unit: String,
    val unitsPerPackage: String,
    val purchaseCost: String,
    val sellingPrice: String,
    val quantity: String,
    val lowStockThreshold: String,
    val active: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

fun ProductDto.toCacheEntity() = ProductCacheEntity(
    id = id,
    barcode = barcode,
    name = name,
    imageUrl = imageUrl,
    category = category,
    unit = unit,
    unitsPerPackage = unitsPerPackage,
    purchaseCost = purchaseCost,
    sellingPrice = sellingPrice,
    quantity = quantity,
    lowStockThreshold = lowStockThreshold,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProductCacheEntity.toDto() = ProductDto(
    id = id,
    barcode = barcode,
    name = name,
    imageUrl = imageUrl,
    category = category,
    unit = unit,
    unitsPerPackage = unitsPerPackage,
    purchaseCost = purchaseCost,
    sellingPrice = sellingPrice,
    quantity = quantity,
    lowStockThreshold = lowStockThreshold,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
