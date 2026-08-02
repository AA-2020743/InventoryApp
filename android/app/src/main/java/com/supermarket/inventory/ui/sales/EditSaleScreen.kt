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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSaleScreen(
    onBack: () -> Unit,
    viewModel: EditSaleViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_sale_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    label = { Text(stringResource(R.string.sales_add_item_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.searchResults.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Column {
                            state.searchResults.take(6).forEach { product ->
                                androidx.compose.material3.ListItem(
                                    headlineContent = { Text(product.name) },
                                    supportingContent = { Text(formatAmount(product.sellingPrice)) },
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.addProduct(product) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.cart, key = { it.product.id }) { item ->
                        EditCartRow(
                            item = item,
                            onIncrement = { viewModel.updateQuantity(item.product.id, item.quantity + 1) },
                            onDecrement = { viewModel.updateQuantity(item.product.id, item.quantity - 1) },
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
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.customerName,
                            onValueChange = viewModel::onCustomerNameChange,
                            label = { Text(stringResource(R.string.sales_customer_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.error?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::save,
                            enabled = !state.isSaving && state.cart.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            } else {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.stats_delete_sale_title)) },
            text = { Text(stringResource(R.string.stats_delete_sale_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun EditCartRow(item: CartItem, onIncrement: () -> Unit, onDecrement: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.product.name, style = MaterialTheme.typography.titleMedium)
                Text(formatAmount(item.product.sellingPrice), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDecrement) { Icon(Icons.Filled.Remove, contentDescription = null) }
            Text(formatQuantity(item.quantity.toString()), modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onIncrement) { Icon(Icons.Filled.Add, contentDescription = null) }
            Text(formatAmount(item.subtotal.toPlainString()), modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sales_remove_item)) }
        }
    }
}
