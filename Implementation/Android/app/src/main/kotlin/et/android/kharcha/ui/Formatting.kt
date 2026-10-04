package et.android.kharcha.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import et.core.domain.BudgetStatus
import et.android.kharcha.data.MoneyDto
import et.android.kharcha.ui.theme.Amber
import et.android.kharcha.ui.theme.Rose
import et.android.kharcha.ui.theme.Teal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatMoney(minorUnits: Long, currency: String): String {
    val symbol = when (currency) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> "$currency "
    }
    val amount = minorUnits / 100.0
    // Whole amounts read cleaner without ".00"; paise are always shown when present.
    val pattern = if (minorUnits % 100 == 0L) "%,.0f" else "%,.2f"
    return "$symbol${String.format(Locale.getDefault(), pattern, amount)}"
}

fun formatMoney(money: MoneyDto): String = formatMoney(money.minorUnits, money.currency)

@Composable
fun statusColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.OVER -> Rose
    BudgetStatus.NEARING -> Amber
    BudgetStatus.OK -> Teal
}

fun formatExpenseDate(occurredAtMillis: Long): String =
    Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM"))

/** Short form for tight spaces like week chips: ₹3,678 below ₹10k, then ₹13.5k, ₹1.2L. */
fun compactMoney(minorUnits: Long, currency: String): String {
    val whole = minorUnits / 100
    val sign = if (whole < 0) "-" else ""
    val abs = kotlin.math.abs(whole)
    val symbol = formatMoney(0, currency).takeWhile { !it.isDigit() }
    return when {
        abs >= 100_000 -> "$sign$symbol${trimDecimal(abs / 100_000.0)}L"
        abs >= 10_000 -> "$sign$symbol${trimDecimal(abs / 1_000.0)}k"
        else -> "$sign$symbol${String.format(Locale.getDefault(), "%,d", abs)}"
    }
}

private fun trimDecimal(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
