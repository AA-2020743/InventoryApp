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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.CashRegisterEntryDto
import com.supermarket.inventory.ui.common.MonthGroupHeader
import com.supermarket.inventory.ui.common.PeriodSummaryCard
import com.supermarket.inventory.ui.common.PeriodTabs
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.common.formatMonth
import com.supermarket.inventory.ui.theme.lossColor
import com.supermarket.inventory.ui.theme.profitColor
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import java.time.YearMonth
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashRegisterTabContent(viewModel: CashRegisterViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var entryToDelete by remember { mutableStateOf<CashRegisterEntryDto?>(null) }
    var showSetBalanceDialog by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var showZeroConfirm by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    var showHistory by remember { mutableStateOf(false) }
    var expandedMonths by remember { mutableStateOf(emptySet<String>()) }

    Box(Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.fillMaxSize()) {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(stringResource(R.string.cash_register_balance), style = MaterialTheme.typography.labelLarge)
                                Text(formatAmount(state.balance), style = MaterialTheme.typography.headlineMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = { showSetBalanceDialog = true }) {
                                    Text(stringResource(R.string.cash_register_set_balance))
                                }
                                TextButton(onClick = { showZeroConfirm = true }) {
                                    Text(stringResource(R.string.cash_register_reset_to_zero))
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.cash_register_ledger),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                // The ledger grows faster than anything else in the app, so
                // the month in progress stays open and every month before it
                // is folded into one card carrying its own in/out totals.
                // Opening one fetches that month, so the history costs
                // nothing until it's looked at.
                val thisMonthKey = YearMonth.now().toString()
                val earlierMonths = state.months.filter { it.month != thisMonthKey }
                val thisMonthSummary = state.months.firstOrNull { it.month == thisMonthKey }
                if (earlierMonths.isNotEmpty()) {
                    PeriodTabs(
                        currentText = stringResource(R.string.history_tab_current),
                        historyText = stringResource(R.string.history_tab_earlier),
                        showHistory = showHistory,
                        onChange = { showHistory = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!showHistory && state.entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.cash_register_ledger_empty))
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
                    ) {
                        if (!showHistory) {
                            if (thisMonthSummary != null) {
                                item(key = "summary") {
                                    PeriodSummaryCard(
                                        label = formatMonth(YearMonth.now(), locale),
                                        detail = stringResource(
                                            R.string.history_cash_flows,
                                            formatAmount(thisMonthSummary.inflow),
                                            formatAmount(thisMonthSummary.outflow),
                                        ),
                                        total = formatAmount(thisMonthSummary.net),
                                        accent = if ((thisMonthSummary.net.toDoubleOrNull() ?: 0.0) < 0) lossColor() else profitColor(),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                            }
                            items(state.entries, key = { it.id }) { entry ->
                                LedgerRow(
                                    entry = entry,
                                    // Only a hand-made entry can be removed
                                    // here; one created for an expense, sale,
                                    // invoice, restock or debt is corrected
                                    // through that record instead.
                                    onDelete = if (entry.isManual) {
                                        { entryToDelete = entry }
                                    } else null,
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        } else {
                            earlierMonths.forEach { month ->
                                val isOpen = month.month in expandedMonths
                                val net = month.net.toDoubleOrNull() ?: 0.0
                                item(key = "m-${month.month}") {
                                    MonthGroupHeader(
                                        label = formatMonth(
                                            runCatching { YearMonth.parse(month.month) }.getOrDefault(YearMonth.now()),
                                            locale,
                                        ),
                                        detail = stringResource(
                                            R.string.history_cash_flows,
                                            formatAmount(month.inflow),
                                            formatAmount(month.outflow),
                                        ),
                                        total = formatAmount(month.net),
                                        expanded = isOpen,
                                        accent = if (net < 0) lossColor() else profitColor(),
                                        onToggle = {
                                            if (isOpen) {
                                                expandedMonths = expandedMonths - month.month
                                                viewModel.forgetMonth(month.month)
                                            } else {
                                                expandedMonths = expandedMonths + month.month
                                                viewModel.loadMonth(month.month)
                                            }
                                        },
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                                if (isOpen) {
                                    val rows = state.monthEntries[month.month]
                                    if (rows == null) {
                                        item(key = "loading-${month.month}") {
                                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(Modifier.height(24.dp))
                                            }
                                        }
                                    } else {
                                        items(rows, key = { it.id }) { entry ->
                                            LedgerRow(
                                                entry = entry,
                                                onDelete = if (entry.isManual) {
                                                    { entryToDelete = entry }
                                                } else null,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddEntryDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cash_register_add_entry))
        }
    }

    if (showSetBalanceDialog) {
        SetBalanceDialog(
            currentBalance = state.balance,
            onDismiss = { showSetBalanceDialog = false },
            onConfirm = { value, note -> viewModel.setBalance(value, note); showSetBalanceDialog = false },
        )
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(stringResource(R.string.cash_register_remove_entry_title)) },
            text = { Text(stringResource(R.string.cash_register_remove_entry_message, formatAmount(entry.amount))) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteEntry(entry.id); entryToDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
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
private fun LedgerRow(entry: CashRegisterEntryDto, onDelete: (() -> Unit)? = null) {
    val amountValue = entry.amount.toDoubleOrNull() ?: 0.0
    val color = if (amountValue < 0) lossColor() else profitColor()
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.note ?: "", style = MaterialTheme.typography.bodyMedium)
                Text(formatIsoDateTime(entry.createdAt), style = MaterialTheme.typography.bodySmall)
            }
            Text(formatAmount(entry.amount), color = color, style = MaterialTheme.typography.titleMedium)
            onDelete?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
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
