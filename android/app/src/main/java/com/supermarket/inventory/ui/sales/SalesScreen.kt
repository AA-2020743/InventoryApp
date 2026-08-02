package com.supermarket.inventory.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    onScan: () -> Unit,
    scannedBarcode: String?,
    onScannedBarcodeConsumed: () -> Unit,
    viewModel: SalesViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            viewModel.submitBarcode(scannedBarcode)
            onScannedBarcodeConsumed()
        }
    }

    val notFoundMessage = stringResource(R.string.scan_not_found)
    val saleCompletedMessage = stringResource(R.string.sales_success)
    val offlineQueuedMessage = stringResource(R.string.sales_offline_queued)
    LaunchedEffect(state.error, state.successMessage) {
        val message = state.successMessage?.let {
            when (it) {
                "SALE_COMPLETED" -> saleCompletedMessage
                "OFFLINE_QUEUED" -> offlineQueuedMessage
                else -> it
            }
        } ?: state.error?.let { if (it.startsWith("NOT_FOUND:")) notFoundMessage else it }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sales_title))
                        if (state.pendingSyncCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.sales_pending_sync_badge, state.pendingSyncCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.barcodeInput,
                    onValueChange = viewModel::onBarcodeInputChange,
                    label = { Text(stringResource(R.string.scan_manual_entry_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { viewModel.submitBarcode() },
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
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

            Spacer(Modifier.height(8.dp))

            if (state.cart.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.sales_cart_empty))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.cart, key = { it.product.id }) { item ->
                        CartRow(
                            item = item,
                            onIncrement = { viewModel.updateQuantity(item.product.id, item.quantity + 1) },
                            onDecrement = { viewModel.updateQuantity(item.product.id, item.quantity - 1) },
                            onEditWeight = { viewModel.requestWeightEdit(item.product) },
                            onRemove = { viewModel.removeItem(item.product.id) },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.sales_total), style = MaterialTheme.typography.titleMedium)
                            Text(formatAmount(state.total.toPlainString()), style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.onDeferredToggle(!state.isDeferred) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.sales_deferred_toggle))
                            Switch(checked = state.isDeferred, onCheckedChange = viewModel::onDeferredToggle)
                        }
                        if (state.isDeferred) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = state.customerName,
                                onValueChange = viewModel::onCustomerNameChange,
                                label = { Text(stringResource(R.string.sales_customer_name_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::checkout,
                            enabled = !state.isCheckingOut,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isCheckingOut) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            } else {
                                Text(stringResource(R.string.sales_checkout))
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingWeightProduct?.let { product ->
        val existingGrams = state.cart.find { it.product.id == product.id }?.quantity?.times(1000)
        WeightEntryDialog(
            product = product,
            initialGrams = existingGrams,
            onConfirm = { grams -> viewModel.confirmWeightEntry(grams) },
            onDismiss = viewModel::cancelWeightEntry,
        )
    }
}

@Composable
private fun CartRow(item: CartItem, onIncrement: () -> Unit, onDecrement: () -> Unit, onEditWeight: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.product.name, style = MaterialTheme.typography.titleMedium)
                Text(formatAmount(item.product.sellingPrice), style = MaterialTheme.typography.bodySmall)
            }
            if (item.product.soldByWeight) {
                Text(
                    stringResource(R.string.sales_grams_display, formatQuantity((item.quantity * 1000).toString())),
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onEditWeight) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
            } else {
                IconButton(onClick = onDecrement) { Icon(Icons.Filled.Remove, contentDescription = null) }
                Text(formatQuantity(item.quantity.toString()), modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = onIncrement) { Icon(Icons.Filled.Add, contentDescription = null) }
            }
            Text(formatAmount(item.subtotal.toPlainString()), modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sales_remove_item)) }
        }
    }
}

@Composable
private fun WeightEntryDialog(
    product: com.supermarket.inventory.data.remote.dto.ProductDto,
    initialGrams: Double?,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var grams by remember { mutableStateOf(initialGrams?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sales_weight_dialog_title, product.name)) },
        text = {
            OutlinedTextField(
                value = grams,
                onValueChange = { grams = it },
                label = { Text(stringResource(R.string.sales_grams_label)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                grams.toDoubleOrNull()?.let { onConfirm(it) }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
