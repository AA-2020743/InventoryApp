package com.supermarket.inventory.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeferredSalesTabContent(viewModel: DeferredSalesViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.sales.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.deferred_sales_empty))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(state.sales, key = { it.id }) { sale ->
                    DeferredSaleRow(
                        sale = sale,
                        onCollect = { viewModel.collect(sale.id) },
                        onCollectPartial = { amount ->
                            viewModel.viewModelScope.launch { viewModel.collectPartial(sale.id, amount) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.deferred_sale_add)) }
    }

    if (showAddDialog) {
        AddDeferredSaleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { amount, customerName ->
                viewModel.viewModelScope.launch {
                    when (viewModel.addManualDeferredSale(amount, customerName)) {
                        is ApiResult.Success -> viewModel.load()
                        is ApiResult.Error -> Unit
                    }
                }
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun DeferredSaleRow(sale: SaleDto, onCollect: () -> Unit, onCollectPartial: (Double) -> Unit) {
    val totalAmount = sale.totalAmount.toDoubleOrNull() ?: 0.0
    val collected = sale.amountCollected.toDoubleOrNull() ?: 0.0
    val remaining = (totalAmount - collected).coerceAtLeast(0.0)
    var showPartialDialog by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatIsoDateTime(sale.createdAt), style = MaterialTheme.typography.bodyMedium)
                Text(formatAmount(sale.totalAmount), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                sale.customerName?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.deferred_sale_customer, it) }
                    ?: stringResource(R.string.deferred_sale_customer_unknown),
                style = MaterialTheme.typography.bodySmall,
            )
            if (collected > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.deferred_sale_remaining, formatAmount(remaining.toString())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showPartialDialog = true }) { Text(stringResource(R.string.deferred_sale_collect_partial)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCollect) { Text(stringResource(R.string.deferred_sale_mark_collected)) }
            }
        }
    }

    if (showPartialDialog) {
        PartialCollectDialog(
            remaining = remaining,
            onDismiss = { showPartialDialog = false },
            onConfirm = { amount -> onCollectPartial(amount); showPartialDialog = false },
        )
    }
}

// Lets the owner record a real partial payment toward a customer's tab
// (capped at what's actually still owed) instead of only being able to
// mark the whole deferred sale collected at once.
@Composable
private fun PartialCollectDialog(remaining: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMessage = stringResource(R.string.deferred_sale_invalid_amount)
    val exceedsMessage = stringResource(R.string.deferred_sale_partial_exceeds_remaining)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deferred_sale_collect_partial)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.deferred_sale_remaining, formatAmount(remaining.toString())),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text(stringResource(R.string.deferred_sale_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.toDoubleOrNull()
                when {
                    value == null || value <= 0 -> error = invalidMessage
                    value > remaining -> error = exceedsMessage
                    else -> onConfirm(value)
                }
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// Records a customer's owed total directly - for a tab/IOU that isn't tied
// to specific inventory - without going through the Sell screen's checkout
// flow. It's still created as a DEFERRED sale server-side, so it shows up
// in revenue/receivables (and the dashboard's net valuation) the same way
// a normal deferred sale would.
@Composable
private fun AddDeferredSaleDialog(onDismiss: () -> Unit, onSave: (Double, String?) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidAmountMessage = stringResource(R.string.deferred_sale_invalid_amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deferred_sale_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.deferred_sale_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text(stringResource(R.string.sales_customer_name_label)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountValue = amount.toDoubleOrNull()
                if (amountValue == null || amountValue <= 0) {
                    error = invalidAmountMessage
                } else {
                    onSave(amountValue, customerName.ifBlank { null })
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
