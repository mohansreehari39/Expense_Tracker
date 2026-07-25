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
import et.android.kharcha.data.BudgetEvaluationDto

/**
 * One progress bar: label, remaining/over caption, and a color-coded track
 * (green OK / amber NEARING / red OVER) — the phone-sized condensed form
 * of the Windows app's weekly chart, since there's no room for a full
 * per-week breakdown on a small screen.
 */
@Composable
fun BudgetBar(label: String, evaluation: BudgetEvaluationDto?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (evaluation != null) {
                val caption = if (evaluation.remainingOrOver.minorUnits >= 0) {
                    "${formatMoney(evaluation.remainingOrOver)} left"
                } else {
                    "${formatMoney(evaluation.remainingOrOver.copy(minorUnits = -evaluation.remainingOrOver.minorUnits))} over"
                }
                Text(caption, style = MaterialTheme.typography.bodySmall, color = statusColor(evaluation.status))
            }
        }
        if (evaluation == null) {
            Text("No budget set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val progress = if (evaluation.allocated.minorUnits <= 0) {
                1f
            } else {
                (evaluation.spent.minorUnits.toFloat() / evaluation.allocated.minorUnits.toFloat()).coerceIn(0f, 1f)
            }
            LinearProgressIndicator(
                progress = { progress },
                color = statusColor(evaluation.status),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Text(
                "${formatMoney(evaluation.spent)} of ${formatMoney(evaluation.allocated)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
