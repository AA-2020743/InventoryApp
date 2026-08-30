package com.supermarket.inventory.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.ExpenseDto
import com.supermarket.inventory.data.repository.ExpenseRepository
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val expenses: List<ExpenseDto> = emptyList(),
    // Distinct names already used, offered as dropdown suggestions and as
    // quick-add chips - an expense's name doubles as its category.
    val names: List<String> = emptyList(),
)

@HiltViewModel
class ExpensesViewModel @Inject constructor(private val repository: ExpenseRepository) : ViewModel() {
    var uiState by mutableStateOf(ExpensesUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.getExpenses()) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, expenses = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false)
            }
            when (val namesResult = repository.getNames()) {
                is ApiResult.Success -> uiState = uiState.copy(names = namesResult.data)
                is ApiResult.Error -> Unit
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteExpense(id)
            load()
        }
    }

    suspend fun create(name: String, amount: Double, date: String?) =
        repository.createExpense(name, amount, date, null)

    suspend fun update(id: String, name: String, amount: Double, date: String?, notes: String?) =
        repository.updateExpense(id, name, amount, date, notes)
}

// Expenses always pay out of the cash register immediately - there's no
// "pay from register?" choice or recurrence to configure anymore, just
// what was spent and when. If the register can't cover it in full, the
// server records the shortfall as deficitAmount, surfaced here as a badge.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(viewModel: ExpensesViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    // Set when the add dialog was opened from a quick-add chip, so the
    // dialog starts with that expense name filled in and only the amount is
    // left to enter.
    var prefilledName by remember { mutableStateOf("") }
    var expenseToEdit by remember { mutableStateOf<ExpenseDto?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseDto?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.expenses_title)) }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // One chip per expense name already in use - most expenses
                // are another instance of something spent on before, so
                // these skip straight past retyping the name.
                if (state.names.isNotEmpty()) {
                    Text(
                        stringResource(R.string.expense_quick_add),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.names, key = { it }) { name ->
                            AssistChip(
                                onClick = { prefilledName = name; addError = null; showAddDialog = true },
                                label = { Text(name) },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                // weight(1f) rather than fillMaxSize(): the chip row above
                // already consumed part of the column, so the list takes
                // what's left instead of overflowing past the bottom.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        state.expenses.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.expenses_empty))
                        }
                        else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                            items(state.expenses, key = { it.id }) { expense ->
                                ExpenseRow(expense, onEdit = { expenseToEdit = expense }, onDelete = { expenseToDelete = expense })
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { prefilledName = ""; addError = null; showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.expense_add)) }
        }
    }

    if (showAddDialog) {
        ExpenseDialog(
            title = stringResource(R.string.expense_add),
            initialName = prefilledName,
            initialAmount = "",
            initialDateIso = null,
            nameSuggestions = state.names,
            error = addError,
            onDismiss = { showAddDialog = false },
            onSave = { name, amount, date ->
                viewModel.viewModelScope.launch {
                    when (val result = viewModel.create(name, amount, date)) {
                        is ApiResult.Success -> { viewModel.load(); showAddDialog = false }
                        is ApiResult.Error -> addError = result.message
                    }
                }
            },
        )
    }

    expenseToEdit?.let { expense ->
        ExpenseDialog(
            title = stringResource(R.string.expense_edit),
            initialName = expense.name,
            initialAmount = expense.amount,
            initialDateIso = expense.date,
            nameSuggestions = state.names,
            error = editError,
            onDismiss = { expenseToEdit = null; editError = null },
            onSave = { name, amount, date ->
                viewModel.viewModelScope.launch {
                    when (val result = viewModel.update(expense.id, name, amount, date, expense.notes)) {
                        is ApiResult.Success -> { viewModel.load(); expenseToEdit = null; editError = null }
                        is ApiResult.Error -> editError = result.message
                    }
                }
            },
        )
    }

    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_named_title, expense.name)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(expense.id); expenseToDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { expenseToDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// Not private: reused by StatsScreen's per-period expenses view to edit an
// expense inline without duplicating this row.
@Composable
fun ExpenseRow(expense: ExpenseDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    val deficit = expense.deficitAmount.toDoubleOrNull() ?: 0.0
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(expense.name, style = MaterialTheme.typography.titleMedium)
                Text(formatIsoDate(expense.date), style = MaterialTheme.typography.bodySmall)
                if (deficit > 0) {
                    Text(
                        stringResource(R.string.expense_deficit_badge, formatAmount(expense.deficitAmount)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(formatAmount(expense.amount), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

// Not private: reused by StatsScreen's per-period expenses view to edit an
// expense inline without duplicating this dialog.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    title: String,
    initialName: String,
    initialAmount: String,
    initialDateIso: String?,
    nameSuggestions: List<String>,
    error: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var nameExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(initialAmount) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateMillis by remember {
        mutableStateOf(
            initialDateIso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: System.currentTimeMillis()
        )
    }
    // The name doubles as the expense's category, so past names are offered
    // as suggestions - but as free text, not a closed list: typing a name
    // that doesn't exist yet starts a new grouping, so the owner is never
    // blocked by a missing option.
    val filteredNames = nameSuggestions.filter {
        name.isBlank() || it.contains(name, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = nameExpanded && filteredNames.isNotEmpty(),
                    onExpandedChange = { nameExpanded = it },
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameExpanded = true },
                        label = { Text(stringResource(R.string.expense_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = nameExpanded && filteredNames.isNotEmpty(),
                        onDismissRequest = { nameExpanded = false },
                    ) {
                        filteredNames.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = { name = suggestion; nameExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.expense_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.expense_date),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(formatIsoDate(Instant.ofEpochMilli(dateMillis).toString()))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountValue = amount.toDoubleOrNull()
                if (name.isNotBlank() && amountValue != null) {
                    onSave(name.trim(), amountValue, Instant.ofEpochMilli(dateMillis).toString())
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
