package com.supermarket.inventory.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.MarginItemDto
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.data.remote.dto.TopProductItemDto
import com.supermarket.inventory.ui.common.PieChart
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.common.formatPercent
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.common.topSlicesWithOther
import com.supermarket.inventory.ui.theme.LossRed
import com.supermarket.inventory.ui.theme.ProfitGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state = viewModel.uiState
    var showDatePicker by remember { mutableStateOf(false) }
    val uncategorizedLabel = stringResource(R.string.stats_uncategorized)
    val otherLabel = stringResource(R.string.stats_other)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                SegmentedButton(
                    selected = state.period == StatsPeriod.DAY,
                    onClick = { viewModel.onPeriodChange(StatsPeriod.DAY) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.stats_period_day)) }
                SegmentedButton(
                    selected = state.period == StatsPeriod.MONTH,
                    onClick = { viewModel.onPeriodChange(StatsPeriod.MONTH) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.stats_period_month)) }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    val pattern = if (state.period == StatsPeriod.MONTH) "MMMM yyyy" else "dd MMM yyyy"
                    Text(state.selectedDate.format(DateTimeFormatter.ofPattern(pattern)))
                }
            }

            if (state.isLoading) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    item { PeriodSummaryCard(state) }
                    item { Spacer(Modifier.height(16.dp)) }
                    if (state.topProducts.isNotEmpty()) {
                        item {
                            Column {
                                val categoryData = state.topProducts
                                    .groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: uncategorizedLabel }
                                    .map { (category, items) -> category to items.sumOf { it.revenue.toDoubleOrNull() ?: 0.0 } }
                                Text(stringResource(R.string.stats_by_category), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                                PieChart(topSlicesWithOther(categoryData, 6, otherLabel), modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(20.dp))

                                val itemData = state.topProducts.map { it.name to (it.revenue.toDoubleOrNull() ?: 0.0) }
                                Text(stringResource(R.string.stats_by_item), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                                PieChart(topSlicesWithOther(itemData, 6, otherLabel), modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                    item { SortRow(state, viewModel) }
                    item { Text(stringResource(R.string.stats_top_products), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(state.topProducts) { TopProductRow(it) }
                    item { Spacer(Modifier.height(16.dp)) }
                    item { Text(stringResource(R.string.stats_top_margins), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(state.margins) { MarginRow(it) }
                    if (state.period == StatsPeriod.DAY) {
                        item { Spacer(Modifier.height(16.dp)) }
                        item { Text(stringResource(R.string.sales_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(state.salesForDay) { SaleRow(it) }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = state.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.onDateSelected(date)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun PeriodSummaryCard(state: StatsUiState) {
    val profit = state.periodProfit.toDoubleOrNull() ?: 0.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.dashboard_revenue), style = MaterialTheme.typography.bodySmall)
                    Text(formatAmount(state.periodRevenue), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(stringResource(R.string.dashboard_profit), style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatAmount(state.periodProfit),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (profit < 0) LossRed else ProfitGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortRow(state: StatsUiState, viewModel: StatsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.sortBy == StatsSort.QUANTITY,
            onClick = { viewModel.onSortChange(StatsSort.QUANTITY) },
            label = { Text(stringResource(R.string.stats_sort_quantity)) },
        )
        FilterChip(
            selected = state.sortBy == StatsSort.PROFIT,
            onClick = { viewModel.onSortChange(StatsSort.PROFIT) },
            label = { Text(stringResource(R.string.stats_sort_profit)) },
        )
    }
}

@Composable
private fun TopProductRow(item: TopProductItemDto) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.stats_quantity_sold, formatQuantity(item.quantitySold.toString())), style = MaterialTheme.typography.bodySmall)
            }
            Text(formatAmount(item.profit), style = MaterialTheme.typography.bodyLarge, color = ProfitGreen)
        }
    }
}

@Composable
private fun MarginRow(item: MarginItemDto) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.stats_margin_percent, formatPercent(item.marginPercent)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SaleRow(sale: SaleDto) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatIsoDateTime(sale.createdAt), style = MaterialTheme.typography.bodyMedium)
                Text(formatAmount(sale.totalAmount), style = MaterialTheme.typography.bodyMedium)
            }
            Divider(Modifier.padding(vertical = 4.dp))
            sale.items.forEach { item ->
                Text(
                    "${item.product?.name ?: item.productId} x${formatQuantity(item.quantity)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
