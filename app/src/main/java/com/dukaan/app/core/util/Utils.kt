package com.dukaan.app.core.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object CurrencyFormatter {
    private val indianFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun formatPaise(paise: Long): String {
        val rupees = paise / 100.0
        return indianFormat.format(rupees)
    }

    fun formatRupees(rupees: Double): String = indianFormat.format(rupees)

    fun paiseToPretty(paise: Long): String {
        val rupees = paise / 100.0
        return when {
            rupees >= 10_00_000 -> "?%.2f L".format(rupees / 1_00_000)
            rupees >= 1_000 -> "?%.2f K".format(rupees / 1000)
            else -> "?%.2f".format(rupees)
        }
    }

    fun rupeesToPaise(rupeesText: String): Long {
        return try {
            val cleaned = rupeesText.replace(",", "").trim()
            (cleaned.toDouble() * 100).toLong()
        } catch (e: NumberFormatException) { 0L }
    }
}

object DateFormatter {
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale("en", "IN"))
    private val shortFormat = SimpleDateFormat("dd/MM/yy", Locale("en", "IN"))
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale("en", "IN"))
    private val fullFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("en", "IN"))
    private val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun formatDate(timestamp: Long): String = displayFormat.format(Date(timestamp))
    fun formatShortDate(timestamp: Long): String = shortFormat.format(Date(timestamp))
    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))
    fun formatFull(timestamp: Long): String = fullFormat.format(Date(timestamp))
    fun formatForInput(timestamp: Long): String = inputFormat.format(Date(timestamp))

    fun startOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun startOfMonth(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun daysAgo(days: Int): Long {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return startOfDay(cal.timeInMillis)
    }
}

object UuidGenerator {
    fun generate(): String = UUID.randomUUID().toString()
}

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Throwable? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

fun Double.formatQuantity(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString()
    else "%.2f".format(this)