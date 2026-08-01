package com.supermarket.inventory.ui.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.remote.dto.ProductInput
import com.supermarket.inventory.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProductFormUiState(
    val productId: String? = null,
    val barcode: String = "",
    val name: String = "",
    val imageUrl: String? = null,
    val category: String = "",
    val unit: String = "pcs",
    val purchaseCost: String = "",
    val sellingPrice: String = "",
    val quantity: String = "",
    val lowStockThreshold: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    var uiState by mutableStateOf(ProductFormUiState())
        private set

    init {
        val productId = savedStateHandle.get<String>("productId")
        val initialBarcode = savedStateHandle.get<String>("barcode")
        if (productId != null) {
            uiState = uiState.copy(productId = productId, isLoading = true)
            loadProduct(productId)
        } else if (!initialBarcode.isNullOrBlank()) {
            uiState = uiState.copy(barcode = initialBarcode)
        }
    }

    fun fullImageUrl(path: String? = uiState.imageUrl): String? =
        path?.let { if (it.startsWith("http")) it else sessionManager.serverUrl.value + it }

    private fun loadProduct(id: String) {
        viewModelScope.launch {
            when (val result = repository.getProduct(id)) {
                is ApiResult.Success -> uiState = result.data.toUiState()
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    private fun ProductDto.toUiState() = ProductFormUiState(
        productId = id,
        barcode = barcode.orEmpty(),
        name = name,
        imageUrl = imageUrl,
        category = category.orEmpty(),
        unit = unit,
        purchaseCost = purchaseCost,
        sellingPrice = sellingPrice,
        quantity = quantity,
        lowStockThreshold = lowStockThreshold,
        isLoading = false,
    )

    fun onBarcodeChange(v: String) { uiState = uiState.copy(barcode = v, error = null) }
    fun onNameChange(v: String) { uiState = uiState.copy(name = v, error = null) }
    fun onCategoryChange(v: String) { uiState = uiState.copy(category = v) }
    fun onUnitChange(v: String) { uiState = uiState.copy(unit = v) }
    fun onPurchaseCostChange(v: String) { uiState = uiState.copy(purchaseCost = v, error = null) }
    fun onSellingPriceChange(v: String) { uiState = uiState.copy(sellingPrice = v, error = null) }
    fun onQuantityChange(v: String) { uiState = uiState.copy(quantity = v, error = null) }
    fun onLowStockThresholdChange(v: String) { uiState = uiState.copy(lowStockThreshold = v) }
    fun onBarcodeScanned(v: String) { uiState = uiState.copy(barcode = v, error = null) }

    fun uploadImage(file: File) {
        viewModelScope.launch {
            uiState = uiState.copy(isUploadingImage = true)
            when (val result = repository.uploadImage(file)) {
                is ApiResult.Success -> uiState = uiState.copy(isUploadingImage = false, imageUrl = result.data.url)
                is ApiResult.Error -> uiState = uiState.copy(isUploadingImage = false, error = result.message)
            }
        }
    }

    fun save() {
        val cost = uiState.purchaseCost.toDoubleOrNull()
        val price = uiState.sellingPrice.toDoubleOrNull()
        val qty = uiState.quantity.toDoubleOrNull() ?: 0.0
        val threshold = uiState.lowStockThreshold.toDoubleOrNull() ?: 0.0

        if (uiState.name.isBlank()) {
            uiState = uiState.copy(error = "Name is required"); return
        }
        if (cost == null || price == null) {
            uiState = uiState.copy(error = "Purchase cost and selling price must be numbers"); return
        }
        if (uiState.barcode.isBlank() && uiState.imageUrl.isNullOrBlank()) {
            uiState = uiState.copy(error = null) // surfaced by server too, but check client-side:
        }

        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, error = null)
            val input = ProductInput(
                barcode = uiState.barcode.ifBlank { null },
                name = uiState.name,
                imageUrl = uiState.imageUrl,
                category = uiState.category.ifBlank { null },
                unit = uiState.unit.ifBlank { "pcs" },
                purchaseCost = cost,
                sellingPrice = price,
                quantity = qty,
                lowStockThreshold = threshold,
            )
            val result = if (uiState.productId != null) {
                repository.update(uiState.productId!!, input)
            } else {
                repository.create(input)
            }
            when (result) {
                is ApiResult.Success -> uiState = uiState.copy(isSaving = false, saved = true)
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }

    fun restock(quantity: Double, unitCost: Double?) {
        val id = uiState.productId ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true)
            when (val result = repository.restock(id, quantity, unitCost, "Restock")) {
                is ApiResult.Success -> uiState = result.data.toUiState()
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }

    fun adjust(quantityChange: Double, reason: String) {
        val id = uiState.productId ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true)
            when (val result = repository.adjust(id, quantityChange, reason)) {
                is ApiResult.Success -> uiState = result.data.toUiState()
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }
}
