package com.supermarket.inventory.ui.spoilage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpoiledProductUiState(
    val barcodeInput: String = "",
    val searchResults: List<ProductDto> = emptyList(),
    val selectedProduct: ProductDto? = null,
    // In pieces for a plain-count product, in grams for a soldByWeight one -
    // mirrors the Sales cart's grams-entry convention (see WeightEntryDialog).
    val quantityInput: String = "1",
    val notes: String = "",
    val isLookingUp: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

private const val SEARCH_DEBOUNCE_MS = 300L

@HiltViewModel
class SpoiledProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
) : ViewModel() {

    var uiState by mutableStateOf(SpoiledProductUiState())
        private set

    private var searchJob: Job? = null

    fun onBarcodeInputChange(value: String) {
        uiState = uiState.copy(barcodeInput = value, error = null)
        searchJob?.cancel()
        if (value.isBlank()) {
            uiState = uiState.copy(searchResults = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            when (val result = productRepository.getProducts(search = value)) {
                is ApiResult.Success -> uiState = uiState.copy(searchResults = result.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    fun submitBarcode(barcode: String = uiState.barcodeInput) {
        if (barcode.isBlank()) return
        searchJob?.cancel()
        viewModelScope.launch {
            uiState = uiState.copy(isLookingUp = true, error = null, barcodeInput = "", searchResults = emptyList())
            when (val result = productRepository.getByBarcode(barcode)) {
                is ApiResult.Success -> selectProduct(result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLookingUp = false, error = "NOT_FOUND:$barcode")
            }
        }
    }

    fun selectSearchResult(product: ProductDto) {
        searchJob?.cancel()
        selectProduct(product)
    }

    private fun selectProduct(product: ProductDto) {
        uiState = uiState.copy(
            selectedProduct = product,
            quantityInput = "1",
            notes = "",
            isLookingUp = false,
            barcodeInput = "",
            searchResults = emptyList(),
        )
    }

    fun onQuantityInputChange(value: String) {
        uiState = uiState.copy(quantityInput = value)
    }

    fun onNotesChange(value: String) {
        uiState = uiState.copy(notes = value)
    }

    fun cancelSelection() {
        uiState = uiState.copy(selectedProduct = null, error = null)
    }

    fun dismissMessages() {
        uiState = uiState.copy(error = null, successMessage = null)
    }

    fun confirmSpoilage() {
        val product = uiState.selectedProduct ?: return
        val entered = uiState.quantityInput.toDoubleOrNull()
        if (entered == null || entered <= 0) {
            uiState = uiState.copy(error = "INVALID_QUANTITY")
            return
        }
        val quantity = if (product.soldByWeight) entered / 1000.0 else entered
        val availableStock = product.quantity.toDoubleOrNull() ?: 0.0
        if (quantity > availableStock) {
            uiState = uiState.copy(error = "INSUFFICIENT_STOCK")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, error = null)
            when (val result = productRepository.spoil(product.id, quantity, uiState.notes.trim().ifBlank { null })) {
                is ApiResult.Success -> uiState = SpoiledProductUiState(successMessage = "SPOILED")
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }
}
