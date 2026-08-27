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
    val categories: List<String> = emptyList(),
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
            when (val catResult = repository.getCategories()) {
                is ApiResult.Success -> uiState = uiState.copy(categories = catResult.data)
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

    suspend fun create(name: String, amount: Double, category: String?, date: String?) =
        repository.createExpense(name, amount, category, date, null)

    suspend fun update(id: String, name: String, amount: Double, category: String?, date: String?, notes: String?) =
        repository.updateExpense(id, name, amount, category, date, notes)
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
    // Set when the add dialog was opened from a category quick-add chip, so
    // the dialog starts with that category filled in and only the name and
    // amount are left to enter.
    var prefilledCategory by remember { mutableStateOf("") }
    var expenseToEdit by remember { mutableStateOf<ExpenseDto?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseDto?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.expenses_title)) }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // One chip per category already in use - most expenses are
                // another instance of something spent on before, so these
                // skip straight past picking/retyping the category.
                if (state.categories.isNotEmpty()) {
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
                        items(state.categories, key = { it }) { category ->
                            AssistChip(
                                onClick = { prefilledCategory = category; addError = null; showAddDialog = true },
                                label = { Text(category) },
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
                onClick = { prefilledCategory = ""; addError = null; showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.expense_add)) }
        }
    }

    if (showAddDialog) {
        ExpenseDialog(
            title = stringResource(R.string.expense_add),
            initialName = "",
            initialAmount = "",
            initialCategory = prefilledCategory,
            initialDateIso = null,
            categorySuggestions = state.categories,
            error = addError,
            onDismiss = { showAddDialog = false },
            onSave = { name, amount, category, date ->
                viewModel.viewModelScope.launch {
                    when (val result = viewModel.create(name, amount, category, date)) {
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
            initialCategory = expense.category ?: "",
            initialDateIso = expense.date,
            categorySuggestions = state.categories,
            error = editError,
            onDismiss = { expenseToEdit = null; editError = null },
            onSave = { name, amount, category, date ->
                viewModel.viewModelScope.launch {
                    when (val result = viewModel.update(expense.id, name, amount, category, date, expense.notes)) {
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
                Text(
                    expense.category?.takeIf { it.isNotBlank() }
                        ?.let { "$it · ${formatIsoDate(expense.date)}" }
                        ?: formatIsoDate(expense.date),
                    style = MaterialTheme.typography.bodySmall,
                )
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
    initialCategory: String,
    initialDateIso: String?,
    categorySuggestions: List<String>,
    error: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double, String?, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var amount by remember { mutableStateOf(initialAmount) }
    var category by remember { mutableStateOf(initialCategory) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateMillis by remember {
        mutableStateOf(
            initialDateIso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: System.currentTimeMillis()
        )
    }
    // Free text with suggestions rather than a closed list: typing a name
    // that doesn't exist yet creates that category, same as products and
    // other sales, so the owner is never blocked by a missing option.
    val filteredCategories = categorySuggestions.filter {
        category.isBlank() || it.contains(category, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.expense_name)) }, singleLine = true)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.expense_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded && filteredCategories.isNotEmpty(),
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it; categoryExpanded = true },
                        label = { Text(stringResource(R.string.expense_category)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = categoryExpanded && filteredCategories.isNotEmpty(),
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        filteredCategories.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = { category = suggestion; categoryExpanded = false },
                            )
                        }
                    }
                }
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
                    onSave(
                        name,
                        amountValue,
                        category.trim().ifBlank { null },
                        Instant.ofEpochMilli(dateMillis).toString(),
                    )
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
