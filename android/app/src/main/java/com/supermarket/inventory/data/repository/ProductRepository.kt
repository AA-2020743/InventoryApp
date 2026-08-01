package com.supermarket.inventory.data.repository

import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.apiCall
import com.supermarket.inventory.data.remote.ApiService
import com.supermarket.inventory.data.remote.dto.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(private val api: ApiService) {

    suspend fun getProducts(search: String? = null, lowStockOnly: Boolean = false): ApiResult<List<ProductDto>> =
        apiCall { api.getProducts(search = search, lowStockOnly = if (lowStockOnly) true else null) }

    suspend fun getByBarcode(barcode: String): ApiResult<ProductDto> =
        apiCall { api.getProductByBarcode(barcode) }

    suspend fun getProduct(id: String): ApiResult<ProductDto> = apiCall { api.getProduct(id) }

    suspend fun create(input: ProductInput): ApiResult<ProductDto> = apiCall { api.createProduct(input) }

    suspend fun update(id: String, input: ProductInput): ApiResult<ProductDto> =
        apiCall { api.updateProduct(id, input) }

    suspend fun delete(id: String): ApiResult<Unit> = apiCall { api.deleteProduct(id) }

    suspend fun restock(id: String, quantity: Double, unitCost: Double?, note: String?): ApiResult<ProductDto> =
        apiCall { api.restockProduct(id, RestockRequest(quantity, unitCost, note)) }

    suspend fun adjust(id: String, quantityChange: Double, note: String): ApiResult<ProductDto> =
        apiCall { api.adjustProduct(id, AdjustRequest(quantityChange, note)) }

    suspend fun getTransactions(id: String): ApiResult<List<InventoryTransactionDto>> =
        apiCall { api.getProductTransactions(id) }

    suspend fun uploadImage(file: File): ApiResult<UploadImageResponse> = apiCall {
        val body = file.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", file.name, body)
        api.uploadImage(part)
    }
}
