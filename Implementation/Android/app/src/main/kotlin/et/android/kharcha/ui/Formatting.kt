package et.android.kharcha.ui

import androidx.compose.ui.graphics.Color
import et.android.kharcha.data.BudgetStatus
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
    return "$symbol${String.format(Locale.getDefault(), "%,.2f", amount)}"
}

fun formatMoney(money: MoneyDto): String = formatMoney(money.minorUnits, money.currency)

fun statusColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.OVER -> Rose
    BudgetStatus.NEARING -> Amber
    BudgetStatus.OK -> Teal
}

fun formatExpenseDate(occurredAtMillis: Long): String =
    Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM"))
