package com.supermarket.inventory.ui.common

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatAmount(value: String, locale: Locale = Locale.getDefault()): String = try {
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    format.format(BigDecimal(value))
} catch (_: Exception) {
    value
}

fun formatQuantity(value: String, locale: Locale = Locale.getDefault()): String = try {
    val decimal = BigDecimal(value)
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 3
    }
    format.format(decimal)
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
