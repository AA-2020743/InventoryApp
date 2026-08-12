package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.local.ProductCacheDao
import com.supermarket.inventory.data.local.toCacheEntity
import com.supermarket.inventory.data.local.toDto
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the API with a local cache (see [ProductCacheDao]) so barcode/name
 * lookup on the Sell screen still works with no connectivity: every
 * successful online response refreshes the cache, and a network failure on
 * [getByBarcode] or a [getProducts] name search falls back to it instead of
 * failing outright. Filtered/paginated listing (category, low-stock-only)
 * isn't cache-backed - those callers don't need to work offline.
 */
@Singleton
class ProductRepository @Inject constructor(
    private val api: ApiService,
    private val cacheDao: ProductCacheDao,
) {

    suspend fun getProducts(
        search: String? = null,
        category: String? = null,
        lowStockOnly: Boolean = false,
    ): ApiResult<List<ProductDto>> {
        val result = apiCall { api.getProducts(search = search, category = category, lowStockOnly = if (lowStockOnly) true else null) }
        if (result is ApiResult.Success) {
            cacheDao.upsertAll(result.data.map { it.toCacheEntity() })
            return result
        }
        if (result is ApiResult.Error && result.isNetworkError && !search.isNullOrBlank() && category == null && !lowStockOnly) {
            return ApiResult.Success(cacheDao.searchByName(search).map { it.toDto() })
        }
        return result
    }

    suspend fun getByBarcode(barcode: String): ApiResult<ProductDto> {
        val result = apiCall { api.getProductByBarcode(barcode) }
        if (result is ApiResult.Success) {
            cacheDao.upsert(result.data.toCacheEntity())
            return result
        }
        if (result is ApiResult.Error && result.isNetworkError) {
            cacheDao.getByBarcode(barcode)?.let { return ApiResult.Success(it.toDto()) }
        }
        return result
    }

    suspend fun getCategories(): ApiResult<List<String>> = apiCall { api.getCategories() }

    suspend fun getProduct(id: String): ApiResult<ProductDto> = apiCall { api.getProduct(id) }

    suspend fun create(input: ProductInput): ApiResult<ProductDto> = apiCall { api.createProduct(input) }

    suspend fun update(id: String, input: ProductInput): ApiResult<ProductDto> =
        apiCall { api.updateProduct(id, input) }

    suspend fun delete(id: String): ApiResult<Unit> = apiCall { api.deleteProduct(id) }

    suspend fun restock(
        id: String,
        quantity: Double,
        unitCost: Double?,
        note: String?,
        financing: String? = null,
        supplierInvoiceId: String? = null,
        newInvoice: NewInvoiceForRestockInput? = null,
    ): ApiResult<ProductDto> = apiCall {
        api.restockProduct(id, RestockRequest(quantity, unitCost, note, financing, supplierInvoiceId, newInvoice))
    }

    suspend fun adjust(id: String, quantityChange: Double, note: String): ApiResult<ProductDto> =
        apiCall { api.adjustProduct(id, AdjustRequest(quantityChange, note)) }

    suspend fun spoil(id: String, quantity: Double, notes: String?): ApiResult<SpoilResponse> =
        apiCall { api.spoilProduct(id, SpoilRequest(quantity, notes)) }

    suspend fun getTransactions(id: String): ApiResult<List<InventoryTransactionDto>> =
        apiCall { api.getProductTransactions(id) }

    suspend fun uploadImage(file: File): ApiResult<UploadImageResponse> = apiCall {
        val body = file.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", file.name, body)
        api.uploadImage(part)
    }
}
