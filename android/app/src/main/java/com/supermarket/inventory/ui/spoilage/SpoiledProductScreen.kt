package com.supermarket.inventory.ui.spoilage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoiledProductScreen(
    onScan: () -> Unit,
    scannedBarcode: String?,
    onScannedBarcodeConsumed: () -> Unit,
    onBack: () -> Unit,
    viewModel: SpoiledProductViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            viewModel.submitBarcode(scannedBarcode)
            onScannedBarcodeConsumed()
        }
    }

    val notFoundMessage = stringResource(R.string.scan_not_found)
    val invalidQuantityMessage = stringResource(R.string.spoiled_product_invalid_quantity)
    val insufficientStockMessage = stringResource(R.string.spoiled_product_insufficient_stock)
    val successMessage = stringResource(R.string.spoiled_product_success)
    LaunchedEffect(state.error, state.successMessage) {
        val message = state.successMessage?.let {
            when (it) {
                "SPOILED" -> successMessage
                else -> it
            }
        } ?: state.error?.let {
            when {
                it.startsWith("NOT_FOUND:") -> notFoundMessage
                it == "INVALID_QUANTITY" -> invalidQuantityMessage
                it == "INSUFFICIENT_STOCK" -> insufficientStockMessage
                else -> it
            }
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spoiled_product_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            Row {
                OutlinedTextField(
                    value = state.barcodeInput,
                    onValueChange = viewModel::onBarcodeInputChange,
                    label = { Text(stringResource(R.string.scan_manual_entry_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { viewModel.submitBarcode() },
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onScan) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.action_scan))
                }
            }

            if (state.searchResults.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column {
                        state.searchResults.take(6).forEach { product ->
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(product.name) },
                                supportingContent = { Text(formatAmount(product.sellingPrice)) },
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectSearchResult(product) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (state.isLookingUp) {
                CircularProgressIndicator()
            }

            state.selectedProduct?.let { product ->
                val available = product.quantity.toDoubleOrNull() ?: 0.0
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.spoiled_product_in_stock,
                                if (product.soldByWeight) formatQuantity((available * 1000).toString()) else formatQuantity(available.toString()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.quantityInput,
                            onValueChange = viewModel::onQuantityInputChange,
                            label = {
                                Text(
                                    stringResource(
                                        if (product.soldByWeight) R.string.spoiled_product_quantity_grams_label
                                        else R.string.spoiled_product_quantity_label,
                                    ),
                                )
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = viewModel::onNotesChange,
                            label = { Text(stringResource(R.string.spoiled_product_notes_label)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = viewModel::cancelSelection) { Text(stringResource(R.string.action_cancel)) }
                            Spacer(Modifier.height(0.dp))
                            Button(
                                onClick = viewModel::confirmSpoilage,
                                enabled = !state.isSaving,
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                } else {
                                    Text(stringResource(R.string.spoiled_product_confirm))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
