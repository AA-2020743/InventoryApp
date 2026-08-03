package com.supermarket.inventory.ui.others

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.supermarket.inventory.R
import com.supermarket.inventory.ui.cashregister.CashRegisterTabContent
import com.supermarket.inventory.ui.invoices.AssetsTabContent
import com.supermarket.inventory.ui.invoices.InvoicesTabContent
import com.supermarket.inventory.ui.sales.DeferredSalesTabContent

private enum class OthersTab(val titleRes: Int) {
    INVOICES(R.string.invoices_title),
    ASSETS(R.string.assets_title),
    DEFERRED_SALES(R.string.deferred_sales_title),
    CASH_REGISTER(R.string.cash_register_title),
}

// Replaces the old "Invoices screen + three-dot overflow menu to four other
// full screens" navigation with a single tabbed screen - everything under
// this bottom-nav destination is a sibling view of the same kind of data
// (money owed/spent/held), so switching between them shouldn't cost a
// back-stack entry.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OthersScreen() {
    var selectedTab by remember { mutableStateOf(OthersTab.INVOICES) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_others)) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 12.dp) {
                OthersTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(stringResource(tab.titleRes)) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (selectedTab) {
                    OthersTab.INVOICES -> InvoicesTabContent()
                    OthersTab.ASSETS -> AssetsTabContent()
                    OthersTab.DEFERRED_SALES -> DeferredSalesTabContent()
                    OthersTab.CASH_REGISTER -> CashRegisterTabContent()
                }
            }
        }
    }
}
