package com.supermarket.inventory.ui.nav

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val INVENTORY = "inventory"
    const val INVENTORY_REPORT = "inventory_report"
    const val PRODUCT_FORM = "product_form?productId={productId}&barcode={barcode}"
    const val SCAN = "scan/{purpose}"
    // focusSearch: set when navigating here from the floating sell button's
    // "search" fallback action, so the screen can jump straight to the
    // manual-entry/search field with the keyboard already up.
    const val SALES = "sales?focusSearch={focusSearch}"
    const val EXPENSES = "expenses"
    const val INVOICES = "invoices"
    const val EDIT_SALE = "edit_sale/{saleId}"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    // Reached only from the floating sell button's "spoiled product" action -
    // its own search/scan bar removes stock and books the cost as an expense.
    const val SPOILED_PRODUCT = "spoiled_product"

    fun editSale(saleId: String) = "edit_sale/$saleId"

    fun productForm(productId: String? = null, barcode: String? = null): String {
        val p = productId?.let { "productId=$it" } ?: ""
        val b = barcode?.let { "barcode=$it" } ?: ""
        val query = listOf(p, b).filter { it.isNotEmpty() }.joinToString("&")
        return if (query.isEmpty()) "product_form" else "product_form?$query"
    }

    fun scan(purpose: String) = "scan/$purpose"

    fun sales(focusSearch: Boolean = false) = "sales?focusSearch=$focusSearch"
}

object ScanPurpose {
    const val ADD_PRODUCT = "add_product"
    const val SELL = "sell"
    const val SPOIL = "spoil"
}
