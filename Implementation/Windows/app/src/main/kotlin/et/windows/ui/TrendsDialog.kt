package et.windows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.windows.server.MoneyDto
import et.windows.server.SpendingTrendResponse
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private const val TREND_MONTHS = 6

/**
 * Windows-only feature (see README V1 "spending trends") — last 6 months'
 * total spend over time, plus a category breakdown aggregated across that
 * whole range, so recurring habits show up rather than just one month's
 * snapshot. Never exposed on Android; there's no equivalent screen there.
 */
@Composable
fun TrendsDialog(api: ApiClient, householdId: String, currency: String, onDismiss: () -> Unit) {
    var trend by remember { mutableStateOf<SpendingTrendResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(householdId) {
        runCatching { api.spendingTrend(householdId, TREND_MONTHS) }
            .onSuccess { trend = it }
            .onFailure { error = it.message ?: it::class.simpleName }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spending Trends") },
        text = {
            val current = trend
            when {
                error != null -> Text("Couldn't load trends: $error", color = MaterialTheme.colorScheme.error)
                current == null -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                else -> Column {
                    Text("Total spend, last $TREND_MONTHS months", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val monthEntries = current.months.mapIndexed { index, month ->
                        BarEntry(
                            label = monthLabel(month.year, month.month),
                            value = month.total.minorUnits,
                            color = chartPalette[index % chartPalette.size],
                            valueText = formatMoney(month.total),
                        )
                    }
                    SimpleBarChart(monthEntries, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(20.dp))
                    Text("By category, last $TREND_MONTHS months combined", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val totalsByCategory = current.months
                        .flatMap { it.byCategory.entries }
                        .groupBy({ it.key }, { it.value.minorUnits })
                        .mapValues { (_, amounts) -> amounts.sum() }
                        .toList()
                        .sortedByDescending { it.second }
                    val categoryEntries = totalsByCategory.mapIndexed { index, (categoryId, minorUnits) ->
                        BarEntry(
                            label = current.categoryNames[categoryId] ?: "Other",
                            value = minorUnits,
                            color = chartPalette[index % chartPalette.size],
                            valueText = formatMoney(MoneyDto(minorUnits, currency)),
                        )
                    }
                    if (categoryEntries.isEmpty()) {
                        Text("No expenses in this range yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SimpleBarChart(categoryEntries, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun monthLabel(year: Int, month: Int): String {
    val name = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$name '${year.toString().takeLast(2)}"
}

private val chartPalette = listOf(Indigo, Teal, Amber, Rose, IndigoDark)
