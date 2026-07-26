package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import et.windows.server.BudgetEvaluationDto
import et.windows.server.MoneyDto

fun statusColor(status: String): Color = when (status) {
    "OVER" -> Rose
    "NEARING" -> Amber
    else -> Teal
}

fun statusLabel(status: String): String = when (status) {
    "OVER" -> "Over budget"
    "NEARING" -> "Nearing limit"
    else -> "On track"
}

fun formatMoney(m: MoneyDto): String {
    val whole = m.minorUnits / 100
    val fraction = kotlin.math.abs(m.minorUnits % 100)
    return "${m.currency} $whole.${fraction.toString().padStart(2, '0')}"
}

/** Once spent crosses this fraction of the allocation, the "Remaining" figure turns red — a distinct, tighter warning than the OK/NEARING/OVER status color driving the bar/track (80%), specifically for this figure row. */
private const val REMAINING_DANGER_THRESHOLD = 0.9

/** Spent (amber) / Remaining (green, red past 90%) / Total (neutral) — shown directly under every budget bar/track, on both the monthly chart and this banner. */
@Composable
fun BudgetFigureRow(spent: MoneyDto, allocated: MoneyDto, modifier: Modifier = Modifier) {
    val remaining = allocated.minorUnits - spent.minorUnits
    val spentFraction = if (allocated.minorUnits <= 0) 1.0 else spent.minorUnits.toDouble() / allocated.minorUnits.toDouble()
    val remainingColor = if (spentFraction >= REMAINING_DANGER_THRESHOLD) Rose else Teal
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BudgetFigure("Spent", formatMoney(spent), Amber, Modifier.weight(1f))
        BudgetFigure("Remaining", formatMoney(MoneyDto(kotlin.math.abs(remaining), allocated.currency)), remainingColor, Modifier.weight(1f))
        BudgetFigure("Total", formatMoney(allocated), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
    }
}

@Composable
private fun BudgetFigure(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Red when OVER, amber when NEARING, green when OK — overspending is
 * always shown, never blocked. See
 * Design/Core/05-domain-logic.md#budget-status-evaluation.
 */
@Composable
fun BudgetStatusBanner(evaluation: BudgetEvaluationDto, modifier: Modifier = Modifier, caption: String = "This week's budget") {
    val color = statusColor(evaluation.status)
    val fraction = if (evaluation.allocated.minorUnits <= 0) {
        1f
    } else {
        (evaluation.spent.minorUnits.toFloat() / evaluation.allocated.minorUnits.toFloat()).coerceIn(0f, 1f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
                Text(statusLabel(evaluation.status), color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.15f),
            )
            BudgetFigureRow(evaluation.spent, evaluation.allocated, modifier = Modifier.padding(top = 8.dp))
            if (evaluation.status == "OVER") {
                Text(
                    "Over by ${formatMoney(evaluation.remainingOrOver.let { it.copy(minorUnits = -it.minorUnits) })}",
                    color = color,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
