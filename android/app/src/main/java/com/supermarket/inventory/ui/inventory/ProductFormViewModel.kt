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

enum class ThresholdMode { UNITS, PACKAGES }

data class ProductFormUiState(
    val productId: String? = null,
    val barcode: String = "",
    val name: String = "",
    val imageUrl: String? = null,
    val category: String = "",
    val unit: String = "pcs",
    // Packaging: blank packageLabel means "sold as individual units", no
    // package math involved at all - existing simple behavior preserved.
    val packageLabel: String = "",
    val unitsPerPackage: String = "1",
    val packagesOnHand: String = "", // only used to compute initial quantity when creating
    val quantity: String = "", // raw units; used directly when packageLabel is blank
    val thresholdMode: ThresholdMode = ThresholdMode.UNITS,
    val thresholdPackages: String = "",
    val lowStockThreshold: String = "", // raw units; source of truth sent to server
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val isPackaged: Boolean get() = packageLabel.isNotBlank()
    val unitsPerPackageValue: Double get() = unitsPerPackage.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
}

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

    private fun ProductDto.toUiState(): ProductFormUiState {
        val perPackage = unitsPerPackage.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val thresholdUnits = lowStockThreshold.toDoubleOrNull() ?: 0.0
        return ProductFormUiState(
            productId = id,
            barcode = barcode.orEmpty(),
            name = name,
            imageUrl = imageUrl,
            category = category.orEmpty(),
            unit = unit,
            packageLabel = packageLabel.orEmpty(),
            unitsPerPackage = unitsPerPackage,
            quantity = quantity,
            lowStockThreshold = lowStockThreshold,
            thresholdPackages = if (perPackage > 1.0) (thresholdUnits / perPackage).toCleanString() else "",
            isLoading = false,
        )
    }

    fun onBarcodeChange(v: String) { uiState = uiState.copy(barcode = v, error = null) }
    fun onNameChange(v: String) { uiState = uiState.copy(name = v, error = null) }
    fun onCategoryChange(v: String) { uiState = uiState.copy(category = v) }
    fun onUnitChange(v: String) { uiState = uiState.copy(unit = v) }
    fun onPackageLabelChange(v: String) {
        uiState = uiState.copy(packageLabel = v, error = null)
        recomputeQuantityPreview()
    }
    fun onUnitsPerPackageChange(v: String) {
        uiState = uiState.copy(unitsPerPackage = v, error = null)
        recomputeQuantityPreview()
    }
    fun onPackagesOnHandChange(v: String) {
        uiState = uiState.copy(packagesOnHand = v, error = null)
        recomputeQuantityPreview()
    }

    // Keeps the (disabled, preview-only) quantity field in sync while
    // creating a packaged product, since the real value is packages x
    // units-per-package rather than something typed directly.
    private fun recomputeQuantityPreview() {
        if (uiState.productId == null && uiState.isPackaged) {
            val packages = uiState.packagesOnHand.toDoubleOrNull() ?: 0.0
            uiState = uiState.copy(quantity = (packages * uiState.unitsPerPackageValue).toCleanString())
        }
    }
    fun onPurchaseCostChange(v: String) { uiState = uiState.copy(purchaseCost = v, error = null) }
    fun onSellingPriceChange(v: String) { uiState = uiState.copy(sellingPrice = v, error = null) }
    fun onQuantityChange(v: String) { uiState = uiState.copy(quantity = v, error = null) }
    fun onThresholdModeChange(mode: ThresholdMode) { uiState = uiState.copy(thresholdMode = mode) }
    fun onThresholdPackagesChange(v: String) { uiState = uiState.copy(thresholdPackages = v) }
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

    /** Prefills name/photo/category from a best-effort external lookup (e.g. Open Food Facts). */
    fun applyAutoFill(name: String?, imageUrl: String?, category: String?) {
        uiState = uiState.copy(
            name = name?.takeIf { uiState.name.isBlank() } ?: uiState.name,
            imageUrl = imageUrl ?: uiState.imageUrl,
            category = category?.takeIf { uiState.category.isBlank() } ?: uiState.category,
        )
    }

    fun save() {
        val cost = uiState.purchaseCost.toDoubleOrNull()
        val price = uiState.sellingPrice.toDoubleOrNull()
        val perPackage = uiState.unitsPerPackageValue

        if (uiState.name.isBlank()) {
            uiState = uiState.copy(error = "Name is required"); return
        }
        if (cost == null || price == null) {
            uiState = uiState.copy(error = "Purchase cost and selling price must be numbers"); return
        }

        val qty = if (uiState.productId == null && uiState.isPackaged) {
            (uiState.packagesOnHand.toDoubleOrNull() ?: 0.0) * perPackage
        } else {
            uiState.quantity.toDoubleOrNull() ?: 0.0
        }

        val threshold = if (uiState.isPackaged && uiState.thresholdMode == ThresholdMode.PACKAGES) {
            (uiState.thresholdPackages.toDoubleOrNull() ?: 0.0) * perPackage
        } else {
            uiState.lowStockThreshold.toDoubleOrNull() ?: 0.0
        }

        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, error = null)
            val input = ProductInput(
                barcode = uiState.barcode.ifBlank { null },
                name = uiState.name,
                imageUrl = uiState.imageUrl,
                category = uiState.category.ifBlank { null },
                unit = uiState.unit.ifBlank { "pcs" },
                packageLabel = uiState.packageLabel.ifBlank { null },
                unitsPerPackage = perPackage,
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

    /** [packagesReceived] is already resolved to base units by the caller (Screen) if packaged. */
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

private fun Double.toCleanString(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()
