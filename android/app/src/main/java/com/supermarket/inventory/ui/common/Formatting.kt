package com.supermarket.inventory.ui.common

import java.math.BigDecimal
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

fun formatAmount(value: String, locale: Locale = Locale.getDefault()): String = try {
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    ltrIsolate("${format.format(BigDecimal(value))} $CURRENCY_SUFFIX")
} catch (_: Exception) {
    value
}

fun formatPercent(value: String, locale: Locale = Locale.getDefault()): String = try {
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }
    ltrIsolate(format.format(BigDecimal(value)))
} catch (_: Exception) {
    value
}

fun formatQuantity(value: String, locale: Locale = Locale.getDefault()): String = try {
    val decimal = BigDecimal(value)
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 3
    }
    ltrIsolate(format.format(decimal))
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
