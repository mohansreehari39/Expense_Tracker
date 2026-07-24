package et.windows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import et.windows.server.BudgetEvaluationDto
import et.windows.server.MoneyDto

private val okColor = Color(0xFF16A34A)
private val nearingColor = Color(0xFFF59E0B)
private val overColor = Color(0xFFDC2626)

fun statusColor(status: String): Color = when (status) {
    "OVER" -> overColor
    "NEARING" -> nearingColor
    else -> okColor
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
fun BudgetStatusBanner(evaluation: BudgetEvaluationDto) {
    val color = statusColor(evaluation.status)
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(evaluation.status, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Spent ${formatMoney(evaluation.spent)} of ${formatMoney(evaluation.allocated)} this week")
            if (evaluation.status == "OVER") {
                Text("Over by ${formatMoney(evaluation.remainingOrOver.let { it.copy(minorUnits = -it.minorUnits) })}", color = color)
            }
        }
    }
}
