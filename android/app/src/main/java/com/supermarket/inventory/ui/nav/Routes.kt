package com.supermarket.inventory.ui.nav

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val INVENTORY = "inventory"
    const val PRODUCT_FORM = "product_form?productId={productId}&barcode={barcode}"
    const val SCAN = "scan/{purpose}"
    const val SALES = "sales"
    const val EXPENSES = "expenses"
    const val INVOICES = "invoices"
    const val EDIT_SALE = "edit_sale/{saleId}"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    fun editSale(saleId: String) = "edit_sale/$saleId"

    fun productForm(productId: String? = null, barcode: String? = null): String {
        val p = productId?.let { "productId=$it" } ?: ""
        val b = barcode?.let { "barcode=$it" } ?: ""
        val query = listOf(p, b).filter { it.isNotEmpty() }.joinToString("&")
        return if (query.isEmpty()) "product_form" else "product_form?$query"
    }

    fun scan(purpose: String) = "scan/$purpose"
}

object ScanPurpose {
    const val ADD_PRODUCT = "add_product"
    const val SELL = "sell"
}
