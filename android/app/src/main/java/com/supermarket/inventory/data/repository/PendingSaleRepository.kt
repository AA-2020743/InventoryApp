package com.supermarket.inventory.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.local.PendingSaleDao
import com.supermarket.inventory.data.local.PendingSaleEntity
import com.supermarket.inventory.data.local.PendingSaleItem
import com.supermarket.inventory.data.local.ProductCacheDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class SyncOutcome(val synced: Int, val droppedWithError: Int, val stillPendingNetworkError: Boolean)

/**
 * Holds sales that finished locally (the cart was finalized at checkout)
 * but couldn't reach the server, and drains them once connectivity returns.
 * Also applies an optimistic local stock decrement to the product cache at
 * queue time, so a second offline sale of the same item doesn't look like
 * it still has full stock - see [ProductCacheDao] for the tradeoff this
 * implies (the cache can still drift from the server across two different
 * offline sales of the same low-stock item; there's no way around that
 * without a live connection).
 */
@Singleton
class PendingSaleRepository @Inject constructor(
    private val dao: PendingSaleDao,
    private val cacheDao: ProductCacheDao,
    moshi: Moshi,
) {
    private val itemsType = Types.newParameterizedType(List::class.java, PendingSaleItem::class.java)
    private val itemsAdapter = moshi.adapter<List<PendingSaleItem>>(itemsType)

    val pendingCount: Flow<Int> = dao.countFlow()

    suspend fun queue(
        clientId: String,
        items: List<Pair<String, Double>>,
        isDeferred: Boolean = false,
        customerName: String? = null,
    ) {
        val pendingItems = items.map { (productId, quantity) -> PendingSaleItem(productId, quantity) }
        dao.insert(
            PendingSaleEntity(
                clientId,
                itemsAdapter.toJson(pendingItems),
                System.currentTimeMillis(),
                isDeferred,
                customerName,
            )
        )
        for ((productId, quantity) in items) {
            val cached = cacheDao.getById(productId) ?: continue
            val remaining = (cached.quantity.toDoubleOrNull() ?: 0.0) - quantity
            cacheDao.updateQuantity(productId, remaining.coerceAtLeast(0.0).toString())
        }
    }

    // Attempts every queued sale in creation order. A network error stops
    // the batch immediately (nothing after it is attempted either, since
    // the same outage almost certainly affects them too) so it can all be
    // retried as a whole next time. Any other error means the server itself
    // rejected that specific sale (e.g. the item no longer exists) -
    // retrying it forever would block every sale queued after it, so it's
    // dropped and surfaced to the owner instead.
    suspend fun drainAndSync(salesRepository: SalesRepository): SyncOutcome {
        var synced = 0
        var dropped = 0
        for (entry in dao.getAll()) {
            val items = itemsAdapter.fromJson(entry.itemsJson) ?: emptyList()
            when (
                val result = salesRepository.createSale(
                    items.map { it.productId to it.quantity },
                    entry.clientId,
                    entry.isDeferred,
                    entry.customerName,
                )
            ) {
                is ApiResult.Success -> {
                    dao.delete(entry.clientId)
                    synced++
                }
                is ApiResult.Error -> {
                    if (result.isNetworkError) {
                        return SyncOutcome(synced, dropped, stillPendingNetworkError = true)
                    }
                    dao.delete(entry.clientId)
                    dropped++
                }
            }
        }
        return SyncOutcome(synced, dropped, stillPendingNetworkError = false)
    }
}
