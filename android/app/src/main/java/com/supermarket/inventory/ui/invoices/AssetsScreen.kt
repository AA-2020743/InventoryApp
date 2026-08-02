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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.remote.dto.AssetDto
import com.supermarket.inventory.data.repository.AssetRepository
import com.supermarket.inventory.ui.common.formatAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssetsUiState(val isLoading: Boolean = true, val assets: List<AssetDto> = emptyList())

@HiltViewModel
class AssetsViewModel @Inject constructor(private val repository: AssetRepository) : ViewModel() {
    var uiState by androidx.compose.runtime.mutableStateOf(AssetsUiState())
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            when (val result = repository.getAssets()) {
                is ApiResult.Success -> uiState = uiState.copy(isLoading = false, assets = result.data)
                is ApiResult.Error -> uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            load()
        }
    }

    suspend fun create(name: String, value: Double, category: String?) = repository.create(name, value, category)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(onBack: () -> Unit, viewModel: AssetsViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assets_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.asset_add)) }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(state.assets, key = { it.id }) { asset ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(asset.name, style = MaterialTheme.typography.titleMedium)
                                asset.category?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            Text(formatAmount(asset.value), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { viewModel.delete(asset.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAssetDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, value, category ->
                viewModel.viewModelScope.launch {
                    viewModel.create(name, value, category)
                    viewModel.load()
                }
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddAssetDialog(onDismiss: () -> Unit, onSave: (String, Double, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.asset_add)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.asset_name)) }, singleLine = true)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.asset_value)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.asset_category)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val valueAmount = value.toDoubleOrNull()
                if (name.isNotBlank() && valueAmount != null) onSave(name, valueAmount, category.ifBlank { null })
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
