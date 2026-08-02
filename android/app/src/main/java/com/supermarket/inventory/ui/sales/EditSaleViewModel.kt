package com.supermarket.inventory.ui.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.repository.ProductRepository
import com.supermarket.inventory.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class EditSaleUiState(
    val isLoading: Boolean = true,
    val cart: List<CartItem> = emptyList(),
    val customerName: String = "",
    val isDeferred: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ProductDto> = emptyList(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val total: BigDecimal get() = cart.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotal) }
}

// Reopens an already-completed sale as an editable cart (same CartItem shape
// the Sell screen uses) so the owner can correct a mistake without having to
// understand stock deltas themselves - Save resubmits the full item list and
// the backend reconciles stock/cash-register/profit figures from that.
@HiltViewModel
class EditSaleViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val productRepository: ProductRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val saleId: String = checkNotNull(savedStateHandle["saleId"])

    var uiState by mutableStateOf(EditSaleUiState())
        private set

    init { load() }

    private fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = salesRepository.getSale(saleId)) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, cart = result.data.toCartItems(), customerName = result.data.customerName ?: "", isDeferred = result.data.paymentStatus == "DEFERRED")
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    private fun SaleDto.toCartItems(): List<CartItem> = items.map { item ->
        val product = item.product ?: ProductDto(
            id = item.productId,
            barcode = null,
            name = item.productId,
            imageUrl = null,
            category = null,
            unit = "pcs",
            unitsPerPackage = "1",
            soldByWeight = false,
            purchaseCost = item.unitCost,
            sellingPrice = item.unitPrice,
            quantity = "0",
            lowStockThreshold = "0",
            active = true,
            createdAt = "",
            updatedAt = "",
        )
        CartItem(product, item.quantity.toDoubleOrNull() ?: 0.0)
    }

    fun onCustomerNameChange(value: String) {
        uiState = uiState.copy(customerName = value)
    }

    fun onDeferredToggle(value: Boolean) {
        uiState = uiState.copy(isDeferred = value)
    }

    fun onSearchQueryChange(value: String) {
        uiState = uiState.copy(searchQuery = value)
        if (value.isBlank()) {
            uiState = uiState.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            when (val result = productRepository.getProducts(search = value)) {
                is ApiResult.Success -> uiState = uiState.copy(searchResults = result.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    fun addProduct(product: ProductDto) {
        val existing = uiState.cart.find { it.product.id == product.id }
        val newCart = if (existing != null) {
            uiState.cart.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
        } else {
            uiState.cart + CartItem(product, 1.0)
        }
        uiState = uiState.copy(cart = newCart, searchQuery = "", searchResults = emptyList())
    }

    fun updateQuantity(productId: String, quantity: Double) {
        if (quantity <= 0) {
            removeItem(productId)
            return
        }
        uiState = uiState.copy(cart = uiState.cart.map { if (it.product.id == productId) it.copy(quantity = quantity) else it })
    }

    fun removeItem(productId: String) {
        uiState = uiState.copy(cart = uiState.cart.filterNot { it.product.id == productId })
    }

    fun save() {
        if (uiState.cart.isEmpty()) return
        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, error = null)
            val items = uiState.cart.map { it.product.id to it.quantity }
            val customerName = uiState.customerName.trim().ifBlank { null }
            val paymentStatus = if (uiState.isDeferred) "DEFERRED" else "PAID"
            when (val result = salesRepository.updateSale(saleId, items, customerName, paymentStatus)) {
                is ApiResult.Success -> uiState = uiState.copy(isSaving = false, saved = true)
                is ApiResult.Error -> uiState = uiState.copy(isSaving = false, error = result.message)
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            uiState = uiState.copy(isDeleting = true, error = null)
            when (val result = salesRepository.deleteSale(saleId)) {
                is ApiResult.Success -> uiState = uiState.copy(isDeleting = false, deleted = true)
                is ApiResult.Error -> uiState = uiState.copy(isDeleting = false, error = result.message)
            }
        }
    }
}
