package com.example.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CurrencyFormatter {

    fun roundFinancialAmount(amount: Double): Double {
        return try {
            java.math.BigDecimal.valueOf(amount)
                .setScale(2, java.math.RoundingMode.HALF_EVEN)
                .toDouble()
        } catch (_: Exception) {
            amount
        }
    }

    fun formatInr(amount: Double, includeDecimals: Boolean = true): String {
        return try {
            val rounded = roundFinancialAmount(amount)
            val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("en").setRegion("IN").build())
            val pattern = if (includeDecimals) "##,##,##0.00" else "##,##,##0"
            val formatter = DecimalFormat(pattern, symbols)
            "₹ " + formatter.format(rounded)
        } catch (e: Exception) {
            "₹ " + String.format(Locale.ENGLISH, "%.2f", amount)
        }
    }

    fun formatInrCompact(amount: Double): String {
        val rounded = roundFinancialAmount(amount)
        return when {
            rounded >= 10000000 -> String.format(Locale.ENGLISH, "₹%.2f Cr", rounded / 10000000.0)
            rounded >= 100000 -> String.format(Locale.ENGLISH, "₹%.2f L", rounded / 100000.0)
            rounded >= 1000 -> String.format(Locale.ENGLISH, "₹%.1f K", rounded / 1000.0)
            else -> formatInr(rounded, includeDecimals = false)
        }
    }
}

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val shortDisplayFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)

    @Synchronized
    fun getCurrentDate(): String = isoFormat.format(Date())

    @Synchronized
    fun formatForDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            displayFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    @Synchronized
    fun formatShortDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            shortDisplayFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    @Synchronized
    fun formatMonthHeader(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            monthFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    @Synchronized
    fun formatDateTime(timestamp: Long): String {
        return try {
            dateTimeFormat.format(Date(timestamp))
        } catch (_: Exception) {
            timestamp.toString()
        }
    }
}

