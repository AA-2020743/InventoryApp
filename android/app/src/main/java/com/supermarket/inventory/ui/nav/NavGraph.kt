package com.supermarket.inventory.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.supermarket.inventory.R
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.ui.dashboard.DashboardScreen
import com.supermarket.inventory.ui.expenses.ExpensesScreen
import com.supermarket.inventory.ui.inventory.InventoryListScreen
import com.supermarket.inventory.ui.inventory.InventoryReportScreen
import com.supermarket.inventory.ui.inventory.ProductFormScreen
import com.supermarket.inventory.ui.login.LoginScreen
import com.supermarket.inventory.ui.others.OthersScreen
import com.supermarket.inventory.ui.sales.EditSaleScreen
import com.supermarket.inventory.ui.sales.SalesScreen
import com.supermarket.inventory.ui.sales.SellFab
import com.supermarket.inventory.ui.scan.BarcodeScannerScreen
import com.supermarket.inventory.ui.settings.SettingsScreen
import com.supermarket.inventory.ui.spoilage.SpoiledProductScreen
import com.supermarket.inventory.ui.stats.StatsScreen

private const val SCANNED_BARCODE_KEY = "scanned_barcode"

private data class BottomTab(val route: String, val labelRes: Int, val icon: ImageVector)

/** Reads a one-shot scan result posted by [BarcodeScannerScreen] into this entry's SavedStateHandle. */
@Composable
private fun NavBackStackEntry.scannedBarcodeState() =
    savedStateHandle.getStateFlow<String?>(SCANNED_BARCODE_KEY, null).collectAsState()

private fun NavBackStackEntry.clearScannedBarcode() {
    savedStateHandle[SCANNED_BARCODE_KEY] = null
}

@Composable
fun InventoryNavHost(sessionManager: SessionManager) {
    val token by sessionManager.token.collectAsState()

    if (token == null) {
        LoginScreen()
        return
    }

    val navController = rememberNavController()
    val tabs = listOf(
        BottomTab(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard),
        BottomTab(Routes.INVENTORY, R.string.nav_inventory, Icons.Filled.Inventory2),
        BottomTab(Routes.EXPENSES, R.string.nav_expenses, Icons.Filled.Payments),
        BottomTab(Routes.INVOICES, R.string.nav_others, Icons.Filled.Receipt),
        BottomTab(Routes.STATS, R.string.nav_stats, Icons.Filled.BarChart),
    )

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        val backStackEntry by navController.currentBackStackEntryAsState()
        // Sell no longer has its own bottom tab - this is the only entry
        // point into selling, surfaced just on the main Dashboard summary
        // so it doesn't clutter the other tabs. Jumps straight into
        // scanning (or, as a fallback, searching).
        val sellFabVisible = backStackEntry?.destination?.route == Routes.DASHBOARD

        Box(Modifier.padding(padding).fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onViewInventory = { navController.navigate(Routes.INVENTORY) },
                        onViewInvoices = { navController.navigate(Routes.INVOICES) },
                    )
                }
                composable(Routes.INVENTORY) { backStackEntry ->
                    val scannedBarcode by backStackEntry.scannedBarcodeState()
                    InventoryListScreen(
                        onAddProduct = { navController.navigate(Routes.productForm()) },
                        onEditProduct = { id -> navController.navigate(Routes.productForm(productId = id)) },
                        onAddProductWithBarcode = { barcode -> navController.navigate(Routes.productForm(barcode = barcode)) },
                        onScanToAdd = { navController.navigate(Routes.scan(ScanPurpose.ADD_PRODUCT)) },
                        onOpenReport = { navController.navigate(Routes.INVENTORY_REPORT) },
                        scannedBarcode = scannedBarcode,
                        onScannedBarcodeConsumed = { backStackEntry.clearScannedBarcode() },
                    )
                }
                composable(Routes.INVENTORY_REPORT) {
                    InventoryReportScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.PRODUCT_FORM,
                    arguments = listOf(
                        navArgument("productId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("barcode") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { backStackEntry ->
                    val scannedBarcode by backStackEntry.scannedBarcodeState()
                    ProductFormScreen(
                        onScanBarcode = { navController.navigate(Routes.scan(ScanPurpose.ADD_PRODUCT)) },
                        scannedBarcode = scannedBarcode,
                        onScannedBarcodeConsumed = { backStackEntry.clearScannedBarcode() },
                        onDone = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.SCAN,
                    arguments = listOf(navArgument("purpose") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val purpose = backStackEntry.arguments?.getString("purpose") ?: ScanPurpose.SELL
                    BarcodeScannerScreen(
                        purpose = purpose,
                        onBarcodeScanned = { barcode ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(SCANNED_BARCODE_KEY, barcode)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.SALES,
                    arguments = listOf(navArgument("focusSearch") { type = NavType.BoolType; defaultValue = false }),
                ) { backStackEntry ->
                    val scannedBarcode by backStackEntry.scannedBarcodeState()
                    val focusSearch = backStackEntry.arguments?.getBoolean("focusSearch") ?: false
                    SalesScreen(
                        onScan = { navController.navigate(Routes.scan(ScanPurpose.SELL)) },
                        scannedBarcode = scannedBarcode,
                        onScannedBarcodeConsumed = { backStackEntry.clearScannedBarcode() },
                        focusSearchOnOpen = focusSearch,
                    )
                }
                composable(Routes.EXPENSES) { ExpensesScreen() }
                composable(Routes.INVOICES) {
                    OthersScreen(onEditSale = { saleId -> navController.navigate(Routes.editSale(saleId)) })
                }
                composable(
                    route = Routes.EDIT_SALE,
                    arguments = listOf(navArgument("saleId") { type = NavType.StringType }),
                ) { EditSaleScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.STATS) {
                    StatsScreen(onEditSale = { saleId -> navController.navigate(Routes.editSale(saleId)) })
                }
                composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.SPOILED_PRODUCT) { backStackEntry ->
                    val scannedBarcode by backStackEntry.scannedBarcodeState()
                    SpoiledProductScreen(
                        onScan = { navController.navigate(Routes.scan(ScanPurpose.SPOIL)) },
                        scannedBarcode = scannedBarcode,
                        onScannedBarcodeConsumed = { backStackEntry.clearScannedBarcode() },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            SellFab(
                visible = sellFabVisible,
                onScan = {
                    navController.navigate(Routes.sales()) { launchSingleTop = true }
                    navController.navigate(Routes.scan(ScanPurpose.SELL))
                },
                onSearch = {
                    navController.navigate(Routes.sales(focusSearch = true)) { launchSingleTop = true }
                },
                onSpoil = {
                    navController.navigate(Routes.SPOILED_PRODUCT)
                },
            )
        }
    }
}
