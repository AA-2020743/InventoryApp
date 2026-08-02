package com.supermarket.inventory.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * A sale that completed locally (the cart was finalized) but couldn't reach
 * the server, kept until [SalesSyncWorker][com.supermarket.inventory.notifications.SalesSyncWorker]
 * can deliver it. [clientId] is sent on every sync attempt so a retry after
 * a dropped *response* is recognized server-side instead of double-selling.
 */
@Entity(tableName = "pending_sales")
data class PendingSaleEntity(
    @PrimaryKey val clientId: String,
    val itemsJson: String,
    val createdAt: Long,
    val isDeferred: Boolean = false,
    val customerName: String? = null,
)

@JsonClass(generateAdapter = true)
data class PendingSaleItem(val productId: String, val quantity: Double)
