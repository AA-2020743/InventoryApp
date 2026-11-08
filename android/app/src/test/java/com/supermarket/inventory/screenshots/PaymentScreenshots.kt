package com.supermarket.inventory.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ThemeMode
import com.supermarket.inventory.data.remote.dto.SupplierInvoiceDto
import com.supermarket.inventory.ui.common.AmountField
import com.supermarket.inventory.ui.common.PaymentConfirmContent
import com.supermarket.inventory.ui.common.PaymentSide
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.invoices.InvoiceRow
import com.supermarket.inventory.ui.theme.InventoryAppTheme
import com.supermarket.inventory.ui.theme.profitColor
import com.supermarket.inventory.ui.theme.warningColor
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The payment confirmation and the invoice rows that open it, drawn with
// made-up figures - how the sheet reads in each language and theme, with a
// fixed amount, a typed part, and a part the till can't cover.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PaymentScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            InventoryAppTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(top = 16.dp)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun InvoicePay(supplier: String, owed: String, till: String, typed: String?, arabicName: Boolean = true) {
        val owedAmount = BigDecimal(owed)
        val amount = if (typed == null) owedAmount else BigDecimal(typed)
        val tillAmount = BigDecimal(till)
        PaymentConfirmContent(
            question = if (typed == null) {
                stringResource(R.string.invoice_pay_confirm_full, formatAmount(owed), supplier)
            } else {
                stringResource(R.string.invoice_pay_confirm_part, supplier)
            },
            from = PaymentSide(Icons.Filled.PointOfSale, stringResource(R.string.cash_register_title), stringResource(R.string.pay_confirm_till_caption), tillAmount, -1, MaterialTheme.colorScheme.primary),
            to = PaymentSide(Icons.Filled.LocalShipping, supplier, stringResource(R.string.pay_confirm_supplier_caption), owedAmount, -1, warningColor()),
            accent = MaterialTheme.colorScheme.primary,
            slideLabel = stringResource(R.string.pay_confirm_slide_pay),
            amount = amount,
            maxAmount = owedAmount,
            amountField = if (typed == null) null else @Composable { AmountField(typed, owedAmount, enabled = true, onChange = {}) },
            problem = if (amount > tillAmount) stringResource(R.string.pay_confirm_till_short, formatAmount(till)) else null,
            error = null,
            busy = false,
            done = false,
            onSlide = {},
            onCancel = {},
        )
    }

    @Composable
    private fun Collect(customer: String, owed: String, till: String, typed: String?) {
        val owedAmount = BigDecimal(owed)
        PaymentConfirmContent(
            question = if (typed == null) {
                stringResource(R.string.collect_confirm_full, formatAmount(owed), customer)
            } else {
                stringResource(R.string.collect_confirm_part, customer)
            },
            from = PaymentSide(Icons.Filled.Person, customer, stringResource(R.string.pay_confirm_customer_caption), owedAmount, -1, warningColor()),
            to = PaymentSide(Icons.Filled.PointOfSale, stringResource(R.string.cash_register_title), stringResource(R.string.pay_confirm_till_caption), BigDecimal(till), 1, profitColor()),
            accent = profitColor(),
            slideLabel = stringResource(R.string.pay_confirm_slide_collect),
            amount = if (typed == null) owedAmount else BigDecimal(typed),
            maxAmount = owedAmount,
            amountField = if (typed == null) null else @Composable { AmountField(typed, owedAmount, enabled = true, onChange = {}) },
            problem = null,
            error = null,
            busy = false,
            done = false,
            onSlide = {},
            onCancel = {},
        )
    }

    private fun invoice(id: String, supplier: String, amount: String, paid: String, status: String, due: String, paidAt: String? = null) =
        SupplierInvoiceDto(
            id = id, supplierId = supplier, supplier = null, invoiceNumber = null, amount = amount,
            issueDate = "2026-10-01T00:00:00.000Z", dueDate = due, status = status, paidAt = paidAt,
            deficitAmount = "0", notes = null, imageUrl = null, createdAt = "2026-10-01T00:00:00.000Z",
            amountFromLines = true, amountPaid = paid,
        )

    @Composable
    private fun Rows(names: List<String>) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            listOf(
                invoice("1", names[0], "3300.00", "1200.00", "PENDING", "2026-10-20T00:00:00.000Z"),
                invoice("2", names[1], "1850.50", "0", "PENDING", "2026-12-05T00:00:00.000Z"),
                invoice("3", names[2], "960.00", "400.00", "PAID", "2026-11-01T00:00:00.000Z", paidAt = "2026-11-03T10:00:00.000Z"),
            ).forEach { inv ->
                InvoiceRow(inv, onPay = {}, onUndoPayments = {}, onEdit = {}, onDelete = {}, onViewImage = {}, onViewLinkedStock = {})
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    private val arSuppliers = listOf("شركة النور للتوريدات", "جهينة", "الأهرام للمشروبات")
    private val enSuppliers = listOf("Al-Nour Supplies", "Juhayna", "Al-Ahram Drinks")

    @Test @Config(qualifiers = AR) fun pay_full_ar_dark() = shoot("pay_full_ar_dark", true) { InvoicePay(arSuppliers[0], "2100.00", "18500.00", null) }
    @Test @Config(qualifiers = AR) fun pay_part_ar_dark() = shoot("pay_part_ar_dark", true) { InvoicePay(arSuppliers[0], "2100.00", "18500.00", "1000") }
    @Test @Config(qualifiers = AR) fun pay_short_ar_light() = shoot("pay_short_ar_light", false) { InvoicePay(arSuppliers[0], "2100.00", "1200.00", "2000") }
    @Test @Config(qualifiers = EN) fun pay_full_en_light() = shoot("pay_full_en_light", false) { InvoicePay(enSuppliers[0], "2100.00", "18500.00", null) }
    @Test @Config(qualifiers = AR) fun collect_full_ar_light() = shoot("collect_full_ar_light", false) { Collect("أم يوسف", "246.69", "18500.00", null) }
    @Test @Config(qualifiers = EN) fun collect_part_en_light() = shoot("collect_part_en_light", false) { Collect("Um Youssef", "246.69", "18500.00", "246.69") }
    @Test @Config(qualifiers = AR) fun invoice_rows_ar_dark() = shoot("invoice_rows_ar_dark", true) { Rows(arSuppliers) }
    @Test @Config(qualifiers = EN) fun invoice_rows_en_light() = shoot("invoice_rows_en_light", false) { Rows(enSuppliers) }

    companion object {
        const val AR = "ar-ldrtl-w411dp-h1100dp-xhdpi"
        const val EN = "en-ldltr-w411dp-h1100dp-xhdpi"
    }
}
