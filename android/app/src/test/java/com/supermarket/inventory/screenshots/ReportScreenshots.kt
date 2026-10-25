package com.supermarket.inventory.screenshots

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.squareup.moshi.Moshi
import com.supermarket.inventory.data.ThemeMode
import com.supermarket.inventory.data.remote.dto.CostTrendDto
import com.supermarket.inventory.data.remote.dto.CreditReportDto
import com.supermarket.inventory.data.remote.dto.PulseDto
import com.supermarket.inventory.data.remote.dto.SellThroughDto
import com.supermarket.inventory.data.remote.dto.StockBatchesDto
import com.supermarket.inventory.data.remote.dto.SuppliersReportDto
import com.supermarket.inventory.ui.stats.CostTrendTab
import com.supermarket.inventory.ui.stats.CreditTab
import com.supermarket.inventory.ui.stats.PulseTab
import com.supermarket.inventory.ui.stats.ReportState
import com.supermarket.inventory.ui.stats.SellThroughTab
import com.supermarket.inventory.ui.stats.SuppliersTab
import com.supermarket.inventory.ui.theme.InventoryAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The report screens drawn with real data (src/test/resources/screenshots,
// captured from a seeded server) and saved as images under
// build/outputs/roborazzi - Arabic dark, Arabic light and English light.
// These record what a screen looks like; they don't compare against a
// stored copy, so nothing fails on a change of design.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ReportScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private val moshi = Moshi.Builder().build()

    private fun <T> load(type: Class<T>, name: String): T =
        moshi.adapter(type).fromJson(javaClass.getResource("/screenshots/$name.json")!!.readText())!!

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            InventoryAppTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun pulse() = ReportState(data = load(PulseDto::class.java, "pulse"))
    private fun costs() = ReportState(data = load(CostTrendDto::class.java, "cost-trend"))
    private fun suppliers() = ReportState(data = load(SuppliersReportDto::class.java, "suppliers"))
    private fun credit() = ReportState(data = load(CreditReportDto::class.java, "credit"))
    private fun pace() = ReportState(data = load(SellThroughDto::class.java, "sell-through"))
    private fun batches() = ReportState(data = load(StockBatchesDto::class.java, "batches"), key = javaClass.getResource("/screenshots/batches-product.txt")!!.readText().trim())

    @Composable private fun Pulse() = PulseTab(pulse(), onRetry = {})
    @Composable private fun Costs() = CostTrendTab(costs(), window = 6, onWindowChange = {}, onRetry = {})
    @Composable private fun Suppliers() = SuppliersTab(suppliers(), window = 6, onWindowChange = {}, onRetry = {})
    @Composable private fun Credit() = CreditTab(credit(), onRetry = {})
    @Composable private fun Pace() = SellThroughTab(pace(), paceDays = 30, onPaceChange = {}, batches = batches(), onOpenProduct = {}, onRetry = {})

    @Test @Config(qualifiers = AR) fun pulse_ar_dark() = shoot("pulse_ar_dark", true) { Pulse() }
    @Test @Config(qualifiers = AR) fun pulse_ar_light() = shoot("pulse_ar_light", false) { Pulse() }
    @Test @Config(qualifiers = EN) fun pulse_en_light() = shoot("pulse_en_light", false) { Pulse() }

    @Test @Config(qualifiers = AR) fun costs_ar_dark() = shoot("costs_ar_dark", true) { Costs() }
    @Test @Config(qualifiers = AR) fun costs_ar_light() = shoot("costs_ar_light", false) { Costs() }
    @Test @Config(qualifiers = EN) fun costs_en_light() = shoot("costs_en_light", false) { Costs() }

    @Test @Config(qualifiers = AR) fun suppliers_ar_dark() = shoot("suppliers_ar_dark", true) { Suppliers() }
    @Test @Config(qualifiers = AR) fun suppliers_ar_light() = shoot("suppliers_ar_light", false) { Suppliers() }
    @Test @Config(qualifiers = EN) fun suppliers_en_light() = shoot("suppliers_en_light", false) { Suppliers() }

    @Test @Config(qualifiers = AR) fun credit_ar_dark() = shoot("credit_ar_dark", true) { Credit() }
    @Test @Config(qualifiers = AR) fun credit_ar_light() = shoot("credit_ar_light", false) { Credit() }
    @Test @Config(qualifiers = EN) fun credit_en_light() = shoot("credit_en_light", false) { Credit() }

    @Test @Config(qualifiers = AR) fun pace_ar_dark() = shoot("pace_ar_dark", true) { Pace() }
    @Test @Config(qualifiers = AR) fun pace_ar_light() = shoot("pace_ar_light", false) { Pace() }
    @Test @Config(qualifiers = EN) fun pace_en_light() = shoot("pace_en_light", false) { Pace() }

    companion object {
        // A typical phone, tall enough to show a long report in one image.
        const val AR = "ar-ldrtl-w411dp-h2600dp-xhdpi"
        const val EN = "en-ldltr-w411dp-h2600dp-xhdpi"
    }
}
