package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.BudgetEvaluation
import et.android.kharcha.ui.theme.Amber
import et.android.kharcha.ui.theme.Rose
import et.android.kharcha.ui.theme.Teal

/** Once spent crosses this fraction of the allocation, the "remaining" figure turns red — a distinct, tighter warning than the OK/NEARING/OVER status color driving the bar/track (80%), specifically for this figure row. */
private const val REMAINING_DANGER_THRESHOLD = 0.9

/**
 * One progress bar: label, a color-coded track (green OK / amber NEARING /
 * red OVER), and directly underneath it the Spent (amber) / Remaining
 * (green, red past 90% spent) / Total (neutral) figures — the phone-sized
 * condensed form of the Windows app's weekly chart. [evaluation] is
 * computed entirely on-device (see data/BudgetMath.kt) — no server
 * involved.
 */
@Composable
fun BudgetBar(label: String, evaluation: BudgetEvaluation?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (evaluation == null) {
            Text("No budget set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val progress = if (evaluation.allocatedMinorUnits <= 0) {
                1f
            } else {
                (evaluation.spentMinorUnits.toFloat() / evaluation.allocatedMinorUnits.toFloat()).coerceIn(0f, 1f)
            }
            LinearProgressIndicator(
                progress = { progress },
                color = statusColor(evaluation.status),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            BudgetFigureRow(
                spentMinorUnits = evaluation.spentMinorUnits,
                allocatedMinorUnits = evaluation.allocatedMinorUnits,
                currency = evaluation.currency,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Spent (amber) / Remaining (green, red past 90%) / Total (neutral) — shared by [BudgetBar] and anywhere else a budget figure needs the same breakdown. */
@Composable
fun BudgetFigureRow(spentMinorUnits: Long, allocatedMinorUnits: Long, currency: String, modifier: Modifier = Modifier) {
    val remainingMinorUnits = allocatedMinorUnits - spentMinorUnits
    val spentFraction = if (allocatedMinorUnits <= 0) 1.0 else spentMinorUnits.toDouble() / allocatedMinorUnits.toDouble()
    val remainingColor = if (spentFraction >= REMAINING_DANGER_THRESHOLD) Rose else Teal
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BudgetFigure("Spent", formatMoney(spentMinorUnits, currency), Amber, Modifier.weight(1f))
        BudgetFigure("Remaining", formatMoney(kotlin.math.abs(remainingMinorUnits), currency), remainingColor, Modifier.weight(1f))
        BudgetFigure("Total", formatMoney(allocatedMinorUnits, currency), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
    }
}

@Composable
private fun BudgetFigure(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
