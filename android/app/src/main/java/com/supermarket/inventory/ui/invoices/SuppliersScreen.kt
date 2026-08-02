package com.supermarket.inventory.ui.invoices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.SupplierDto
import com.supermarket.inventory.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SuppliersUiState(
    val isLoading: Boolean = true,
    val suppliers: List<SupplierDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SuppliersViewModel @Inject constructor(private val repository: SupplierRepository) : ViewModel() {
    var uiState by androidx.compose.runtime.mutableStateOf(SuppliersUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = repository.getSuppliers()) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, suppliers = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false)
            }
        }
    }

    suspend fun create(name: String, contactInfo: String?) = repository.createSupplier(name, contactInfo)

    suspend fun update(id: String, name: String, contactInfo: String?) = repository.updateSupplier(id, name, contactInfo)

    fun delete(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteSupplier(id)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> uiState = uiState.copy(error = result.message)
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }
}

// Reached from the Invoices tab's "Suppliers" button as a full-screen
// overlay (not a nav destination) so managing suppliers - the less
// frequent operation - doesn't need its own back-stack entry; adding one
// on the fly happens inline in the Add Invoice dialog instead.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSuppliersDialog(onDismiss: () -> Unit, viewModel: SuppliersViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    var supplierToEdit by remember { mutableStateOf<SupplierDto?>(null) }
    var supplierToDelete by remember { mutableStateOf<SupplierDto?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.suppliers_title)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel)) } },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.supplier_add)) }
            },
        ) { padding ->
            if (state.isLoading) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(state.suppliers, key = { it.id }) { supplier ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(supplier.name, style = MaterialTheme.typography.titleMedium)
                                    supplier.contactInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                IconButton(onClick = { supplierToEdit = supplier }) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                                }
                                IconButton(onClick = { supplierToDelete = supplier }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SupplierDialog(
            title = stringResource(R.string.supplier_add),
            initialName = "",
            initialContact = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, contact ->
                viewModel.viewModelScope.launch {
                    viewModel.create(name, contact)
                    viewModel.load()
                }
                showAddDialog = false
            },
        )
    }

    supplierToEdit?.let { supplier ->
        SupplierDialog(
            title = stringResource(R.string.supplier_edit),
            initialName = supplier.name,
            initialContact = supplier.contactInfo ?: "",
            onDismiss = { supplierToEdit = null },
            onSave = { name, contact ->
                viewModel.viewModelScope.launch {
                    viewModel.update(supplier.id, name, contact)
                    viewModel.load()
                }
                supplierToEdit = null
            },
        )
    }

    supplierToDelete?.let { supplier ->
        AlertDialog(
            onDismissRequest = { supplierToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_named_title, supplier.name)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(supplier.id); supplierToDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { supplierToDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SupplierDialog(
    title: String,
    initialName: String,
    initialContact: String,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var contact by remember { mutableStateOf(initialContact) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.supplier_name)) }, singleLine = true)
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text(stringResource(R.string.supplier_contact)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(name, contact.ifBlank { null })
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
