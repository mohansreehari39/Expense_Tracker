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

/**
 * Red when OVER, amber when NEARING, green when OK — overspending is
 * always shown, never blocked. See
 * Design/Core/05-domain-logic.md#budget-status-evaluation.
 */
@Composable
fun BudgetStatusBanner(evaluation: BudgetEvaluationDto, modifier: Modifier = Modifier, caption: String = "this week") {
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
                "${formatMoney(evaluation.spent)} spent of ${formatMoney(evaluation.allocated)} $caption",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.15f),
            )
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
