package com.supermarket.inventory.ui.cashregister

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.CashRegisterEntryDto
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.theme.LossRed
import com.supermarket.inventory.ui.theme.ProfitGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashRegisterScreen(
    onBack: () -> Unit,
    viewModel: CashRegisterViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    var showSetBalanceDialog by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var showZeroConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cash_register_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { showSetBalanceDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cash_register_set_balance))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddEntryDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cash_register_add_entry))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(stringResource(R.string.cash_register_balance), style = MaterialTheme.typography.labelLarge)
                                Text(formatAmount(state.balance), style = MaterialTheme.typography.headlineMedium)
                            }
                            TextButton(onClick = { showZeroConfirm = true }) {
                                Text(stringResource(R.string.cash_register_reset_to_zero))
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.cash_register_ledger),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (state.entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.cash_register_ledger_empty))
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp)) {
                        items(state.entries, key = { it.id }) { entry ->
                            LedgerRow(entry)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showSetBalanceDialog) {
        SetBalanceDialog(
            currentBalance = state.balance,
            onDismiss = { showSetBalanceDialog = false },
            onConfirm = { value, note -> viewModel.setBalance(value, note); showSetBalanceDialog = false },
        )
    }

    if (showAddEntryDialog) {
        AddEntryDialog(
            onDismiss = { showAddEntryDialog = false },
            onConfirm = { amount, note -> viewModel.addEntry(amount, note); showAddEntryDialog = false },
        )
    }

    if (showZeroConfirm) {
        AlertDialog(
            onDismissRequest = { showZeroConfirm = false },
            title = { Text(stringResource(R.string.cash_register_reset_to_zero)) },
            text = { Text(stringResource(R.string.cash_register_reset_to_zero_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBalance(0.0, null)
                    showZeroConfirm = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showZeroConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun LedgerRow(entry: CashRegisterEntryDto) {
    val amountValue = entry.amount.toDoubleOrNull() ?: 0.0
    val color = if (amountValue < 0) LossRed else ProfitGreen
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(entry.note ?: "", style = MaterialTheme.typography.bodyMedium)
                Text(formatIsoDateTime(entry.createdAt), style = MaterialTheme.typography.bodySmall)
            }
            Text(formatAmount(entry.amount), color = color, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SetBalanceDialog(currentBalance: String, onDismiss: () -> Unit, onConfirm: (Double, String?) -> Unit) {
    var value by remember { mutableStateOf(currentBalance) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cash_register_set_balance)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.cash_register_new_value)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.cash_register_entry_note)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                value.toDoubleOrNull()?.let { onConfirm(it, note.ifBlank { null }) }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun AddEntryDialog(onDismiss: () -> Unit, onConfirm: (Double, String?) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cash_register_add_entry)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.cash_register_entry_amount)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.cash_register_entry_note)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amount.toDoubleOrNull()?.let { onConfirm(it, note.ifBlank { null }) }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
