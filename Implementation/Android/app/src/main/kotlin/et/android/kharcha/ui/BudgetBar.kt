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
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.BudgetEvaluation

/**
 * One progress bar: label, remaining/over caption, and a color-coded track
 * (green OK / amber NEARING / red OVER) — the phone-sized condensed form
 * of the Windows app's weekly chart, since there's no room for a full
 * per-week breakdown on a small screen. [evaluation] is computed entirely
 * on-device (see data/BudgetMath.kt) — no server involved.
 */
@Composable
fun BudgetBar(label: String, evaluation: BudgetEvaluation?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (evaluation != null) {
                val caption = if (evaluation.remainingOrOverMinorUnits >= 0) {
                    "${formatMoney(evaluation.remainingOrOverMinorUnits, evaluation.currency)} left"
                } else {
                    "${formatMoney(-evaluation.remainingOrOverMinorUnits, evaluation.currency)} over"
                }
                Text(caption, style = MaterialTheme.typography.bodySmall, color = statusColor(evaluation.status))
            }
        }
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
            Text(
                "${formatMoney(evaluation.spentMinorUnits, evaluation.currency)} of ${formatMoney(evaluation.allocatedMinorUnits, evaluation.currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
