package et.android.kharcha.ui

import androidx.compose.ui.graphics.Color
import et.android.kharcha.data.MoneyDto
import et.android.kharcha.ui.theme.Amber
import et.android.kharcha.ui.theme.Rose
import et.android.kharcha.ui.theme.Teal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatMoney(money: MoneyDto): String {
    val symbol = when (money.currency) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> money.currency + " "
    }
    val amount = money.minorUnits / 100.0
    return "$symbol${String.format(Locale.getDefault(), "%,.2f", amount)}"
}

fun statusColor(status: String): Color = when (status) {
    "OVER" -> Rose
    "NEARING" -> Amber
    else -> Teal
}

fun formatExpenseDate(occurredAtMillis: Long): String =
    Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM"))
