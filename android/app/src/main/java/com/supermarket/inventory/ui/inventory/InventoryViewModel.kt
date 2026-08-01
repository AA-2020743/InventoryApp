package com.supermarket.inventory.ui.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val isLoading: Boolean = true,
    val products: List<ProductDto> = emptyList(),
    val search: String = "",
    val lowStockOnly: Boolean = false,
    val error: String? = null,
)

sealed class BarcodeResolution {
    data class ExistingProduct(val productId: String) : BarcodeResolution()
    data class NewProduct(val barcode: String) : BarcodeResolution()
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    var uiState by mutableStateOf(InventoryUiState())
        private set

    fun fullImageUrl(path: String?): String? =
        path?.let { if (it.startsWith("http")) it else sessionManager.serverUrl.value + it }

    init {
        load()
    }

    fun onSearchChange(value: String) {
        uiState = uiState.copy(search = value)
        load()
    }

    fun onToggleLowStockOnly() {
        uiState = uiState.copy(lowStockOnly = !uiState.lowStockOnly)
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            val search = uiState.search.ifBlank { null }
            when (val result = repository.getProducts(search = search, lowStockOnly = uiState.lowStockOnly)) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, products = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    suspend fun resolveScannedBarcode(barcode: String): BarcodeResolution {
        return when (val result = repository.getByBarcode(barcode)) {
            is ApiResult.Success -> BarcodeResolution.ExistingProduct(result.data.id)
            is ApiResult.Error -> BarcodeResolution.NewProduct(barcode)
        }
    }
}
