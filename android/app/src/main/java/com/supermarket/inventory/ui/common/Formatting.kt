package com.supermarket.inventory.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.supermarket.inventory.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Egyptian Pound abbreviation, used as a plain suffix rather than a locale
// currency symbol since this app targets one specific currency, not a
// locale-derived one.
private const val CURRENCY_SUFFIX = "LE"

// Forces a run of Western digits/sign/currency text to render as one fixed
// left-to-right unit even when it sits inside an RTL (Arabic) paragraph -
// without this, a negative amount's "-" visually jumps to the wrong side
// (e.g. "67,341.40-" instead of "-67,341.40") because plain digits/minus
// have no strong directionality of their own and the bidi algorithm
// resolves them against the surrounding RTL text instead.
private const val LRI = "⁦" // Left-to-Right Isolate
private const val PDI = "⁩" // Pop Directional Isolate
private fun ltrIsolate(text: String) = "$LRI$text$PDI"

// Half up, like every amount the server rounds and stores: NumberFormat's own
// default is half-even, which shows an exact 1.005 as 1.00 where the stored
// figure would be 1.01.
private fun numberFormat(locale: Locale, minFraction: Int, maxFraction: Int): NumberFormat =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = minFraction
        maximumFractionDigits = maxFraction
        roundingMode = RoundingMode.HALF_UP
    }

fun formatAmount(value: String, locale: Locale = Locale.getDefault()): String = try {
    ltrIsolate("${numberFormat(locale, 2, 2).format(BigDecimal(value))} $CURRENCY_SUFFIX")
} catch (_: Exception) {
    value
}

// An amount that is a change: "+170.00 LE" or "−1,800.00 LE", the sign kept
// inside the same left-to-right run as the figure. Put in front of an
// isolated amount from outside, the sign belongs to the surrounding Arabic
// text and lands on the far side of it - "1,800.00 LE-".
fun formatSignedAmount(value: String, locale: Locale = Locale.getDefault()): String = try {
    val decimal = BigDecimal(value)
    val sign = when (decimal.signum()) {
        1 -> "+"
        -1 -> "−"
        else -> ""
    }
    ltrIsolate("$sign${numberFormat(locale, 2, 2).format(decimal.abs())} $CURRENCY_SUFFIX")
} catch (_: Exception) {
    value
}

// "6,000.00 LE → 7,800.00 LE" as one left-to-right run, whatever the
// language around it. Left to the surrounding Arabic, the two amounts swap
// sides while the arrow doesn't turn, so a rise from 0 to 50 read "50 → 0".
fun formatAmountChange(from: String, to: String, locale: Locale = Locale.getDefault()): String = try {
    val format = numberFormat(locale, 2, 2)
    ltrIsolate("${format.format(BigDecimal(from))} → ${format.format(BigDecimal(to))} $CURRENCY_SUFFIX")
} catch (_: Exception) {
    "$from → $to"
}

fun formatPercent(value: String, locale: Locale = Locale.getDefault()): String = try {
    ltrIsolate(numberFormat(locale, 0, 1).format(BigDecimal(value)))
} catch (_: Exception) {
    value
}

fun formatQuantity(value: String, locale: Locale = Locale.getDefault()): String = try {
    ltrIsolate(numberFormat(locale, 0, 3).format(BigDecimal(value)))
} catch (_: Exception) {
    value
}

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val displayDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

fun formatIsoDate(iso: String, locale: Locale = Locale.getDefault()): String = try {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(displayDateFormatter.withLocale(locale))
} catch (_: Exception) {
    iso
}

fun formatIsoDateTime(iso: String, locale: Locale = Locale.getDefault()): String = try {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(displayDateTimeFormatter.withLocale(locale))
} catch (_: Exception) {
    iso
}

// The server composes a spoilage write-off's expense name itself
// ("Spoiled: <product>") so the write-off and the stock movement that
// caused it can find each other again later. That name is storage, not
// display: rendered raw it left an English word sitting in an otherwise
// Arabic list.
//
// This translates it where it is shown and leaves the stored name alone -
// the backend still matches on the English form, and so does the undo.
private const val SPOILAGE_NAME_PREFIX = "Spoiled: "

@Composable
fun expenseDisplayName(name: String): String =
    if (name.startsWith(SPOILAGE_NAME_PREFIX)) {
        stringResource(R.string.expense_spoiled_name, name.removePrefix(SPOILAGE_NAME_PREFIX))
    } else {
        name
    }
