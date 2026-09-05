package com.supermarket.inventory.ui.sales

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.supermarket.inventory.R
import com.supermarket.inventory.data.remote.dto.SaleDto
import com.supermarket.inventory.ui.common.EmptyState
import com.supermarket.inventory.ui.common.MonthGroupHeader
import com.supermarket.inventory.ui.common.PeriodSummaryCard
import com.supermarket.inventory.ui.common.PeriodTabs
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDateTime
import com.supermarket.inventory.ui.common.formatMonth
import com.supermarket.inventory.ui.common.formatQuantity
import com.supermarket.inventory.ui.theme.profitColor
import java.time.YearMonth
import java.util.Locale

// Every sale the shop has rung up, browsable.
//
// Until now a past sale could only be reached through Statistics, by
// landing on the exact day it happened - fine for reviewing a day, useless
// for "what did we sell that customer last month" or for finding a receipt
// to correct. This is the same fold as everywhere else: the month in
// progress open, older months as one card each carrying their count and
// takings, a month's receipts fetched only when it's opened.
@Composable
fun ReceiptsTabContent(
    onEditSale: (String) -> Unit,
    viewModel: ReceiptsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    val locale = Locale.getDefault()
    var showHistory by remember { mutableStateOf(false) }
    var expandedMonths by remember { mutableStateOf(emptySet<String>()) }

    val thisMonthKey = YearMonth.now().toString()
    val thisMonth = state.months.firstOrNull { it.month == thisMonthKey }
    val earlierMonths = state.months.filter { it.month != thisMonthKey }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (earlierMonths.isNotEmpty()) {
                PeriodTabs(
                    currentText = stringResource(
                        R.string.history_tab_current,
                        formatMonth(YearMonth.now(), locale),
                        thisMonth?.count ?: state.currentMonthSales.size,
                    ),
                    historyText = stringResource(
                        R.string.history_tab_earlier,
                        earlierMonths.sumOf { it.count },
                    ),
                    showHistory = showHistory,
                    onChange = { showHistory = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.months.isEmpty() -> EmptyState(
                    icon = Icons.Filled.ReceiptLong,
                    title = stringResource(R.string.receipts_empty),
                    hint = stringResource(R.string.receipts_empty_hint),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp),
                ) {
                    if (!showHistory) {
                        item(key = "summary") {
                            PeriodSummaryCard(
                                label = formatMonth(YearMonth.now(), locale),
                                detail = stringResource(R.string.receipts_count, thisMonth?.count ?: 0),
                                total = formatAmount(thisMonth?.revenue ?: "0"),
                                accent = profitColor(),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        if (state.currentMonthSales.isEmpty()) {
                            item(key = "empty-month") {
                                Text(
                                    stringResource(R.string.history_nothing_this_month),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.currentMonthSales, key = { it.id }) { sale ->
                            ReceiptRow(sale, onClick = { onEditSale(sale.id) })
                            Spacer(Modifier.height(6.dp))
                        }
                    } else {
                        item(key = "summary-history") {
                            PeriodSummaryCard(
                                label = stringResource(R.string.history_earlier_label),
                                detail = stringResource(
                                    R.string.receipts_count,
                                    earlierMonths.sumOf { it.count },
                                ),
                                total = formatAmount(
                                    earlierMonths.sumOf { it.revenue.toDoubleOrNull() ?: 0.0 }.toString()
                                ),
                                accent = profitColor(),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        earlierMonths.forEach { month ->
                            val isOpen = month.month in expandedMonths
                            item(key = "m-${month.month}") {
                                MonthGroupHeader(
                                    label = formatMonth(
                                        runCatching { YearMonth.parse(month.month) }.getOrDefault(YearMonth.now()),
                                        locale,
                                    ),
                                    detail = stringResource(R.string.receipts_count, month.count),
                                    total = formatAmount(month.revenue),
                                    expanded = isOpen,
                                    accent = profitColor(),
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
                                val rows = state.monthSales[month.month]
                                if (rows == null) {
                                    item(key = "loading-${month.month}") {
                                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(Modifier.height(24.dp))
                                        }
                                    }
                                } else {
                                    items(rows, key = { it.id }) { sale ->
                                        ReceiptRow(sale, onClick = { onEditSale(sale.id) })
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
}

// One receipt: when it was rung up, who it was for if anyone, what was on
// it, and what it came to. Tapping opens it for correction.
@Composable
private fun ReceiptRow(sale: SaleDto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(formatIsoDateTime(sale.createdAt), style = MaterialTheme.typography.bodyMedium)
                    val customer = sale.customerName?.takeIf { it.isNotBlank() }
                    if (customer != null) {
                        Text(
                            customer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (sale.paymentStatus == "DEFERRED") {
                        Text(
                            stringResource(R.string.deferred_sales_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(formatAmount(sale.totalAmount), style = MaterialTheme.typography.titleSmall)
            }
            if (sale.items.isNotEmpty()) {
                Divider(Modifier.padding(vertical = 4.dp))
                // The first few lines are enough to recognise a receipt;
                // the rest are on the sale itself, one tap away.
                sale.items.take(4).forEach { item ->
                    Text(
                        "${item.product?.name ?: item.productId} × ${formatQuantity(item.quantity)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sale.items.size > 4) {
                    Text(
                        stringResource(R.string.receipts_more_items, sale.items.size - 4),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
