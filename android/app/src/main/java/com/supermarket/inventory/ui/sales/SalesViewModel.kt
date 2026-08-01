package com.supermarket.inventory.ui.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.ProductDto
import com.supermarket.inventory.data.repository.ProductRepository
import com.supermarket.inventory.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class CartItem(val product: ProductDto, val quantity: Double) {
    val subtotal: BigDecimal get() = BigDecimal(product.sellingPrice).multiply(BigDecimal(quantity))
}

data class SalesUiState(
    val barcodeInput: String = "",
    val searchResults: List<ProductDto> = emptyList(),
    val cart: List<CartItem> = emptyList(),
    val isLookingUp: Boolean = false,
    val isCheckingOut: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
) {
    val total: BigDecimal get() = cart.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotal) }
}

private const val SEARCH_DEBOUNCE_MS = 300L

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val salesRepository: SalesRepository,
) : ViewModel() {

    var uiState by mutableStateOf(SalesUiState())
        private set

    private var searchJob: Job? = null

    // The same field doubles as barcode entry (scanner or manual, submitted
    // with Enter) and a live name search - typing a product name shows
    // tappable suggestions below, while a fast HID scanner burst followed
    // by Enter never lingers long enough for the debounced search to fire.
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
                is ApiResult.Success -> addToCart(result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLookingUp = false, error = "NOT_FOUND:$barcode")
            }
        }
    }

    fun selectSearchResult(product: ProductDto) {
        searchJob?.cancel()
        addToCart(product)
        uiState = uiState.copy(barcodeInput = "", searchResults = emptyList())
    }

    private fun addToCart(product: ProductDto) {
        val existing = uiState.cart.find { it.product.id == product.id }
        val newCart = if (existing != null) {
            uiState.cart.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
        } else {
            uiState.cart + CartItem(product, 1.0)
        }
        uiState = uiState.copy(cart = newCart, isLookingUp = false)
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

    fun dismissMessages() {
        uiState = uiState.copy(error = null, successMessage = null)
    }

    fun checkout() {
        if (uiState.cart.isEmpty()) return
        viewModelScope.launch {
            uiState = uiState.copy(isCheckingOut = true, error = null)
            val items = uiState.cart.map { it.product.id to it.quantity }
            when (val result = salesRepository.createSale(items)) {
                is ApiResult.Success -> uiState = SalesUiState(successMessage = "Sale completed")
                is ApiResult.Error -> uiState = uiState.copy(isCheckingOut = false, error = result.message)
            }
        }
    }
}
