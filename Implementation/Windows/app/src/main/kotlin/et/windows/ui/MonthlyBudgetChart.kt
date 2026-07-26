package et.windows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import et.windows.server.BudgetEvaluationDto
import et.windows.server.WeekEvaluationDto
import java.time.LocalDate

/**
 * A full-length monthly budget bar with the month's weeks shown as
 * proportionally-sized segments underneath it — the combined width of the
 * weekly segments equals the monthly bar's width, since each week's
 * `allocated` amount (from the API) is already its day-proportional slice
 * of the month. See Design/Core/05-domain-logic.md#weekly-budget-derivation.
 */
@Composable
fun MonthlyBudgetChart(
    monthLabel: String,
    monthlyEvaluation: BudgetEvaluationDto,
    weeks: List<WeekEvaluationDto>,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthColor = statusColor(monthlyEvaluation.status)
    val monthFraction = if (monthlyEvaluation.allocated.minorUnits <= 0) {
        1f
    } else {
        (monthlyEvaluation.spent.minorUnits.toFloat() / monthlyEvaluation.allocated.minorUnits.toFloat()).coerceIn(0f, 1f)
    }

    // Which week's own figures are shown below the segments — defaults to
    // today's week (mirrors the Android app opening on the current week),
    // switchable by clicking any other segment.
    var selectedIndex by remember(weeks) {
        val today = LocalDate.now()
        val current = weeks.indexOfFirst { week -> today >= LocalDate.parse(week.weekStart) && today <= LocalDate.parse(week.weekEnd) }
        mutableStateOf(if (current >= 0) current else weeks.size - 1)
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(monthLabel, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onEditClick, modifier = Modifier.clip(CircleShape)) {
                    Text("✏️", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                statusLabel(monthlyEvaluation.status),
                color = monthColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(4.dp))

        // Full monthly bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(monthColor.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(monthFraction)
                    .clip(RoundedCornerShape(8.dp))
                    .background(monthColor),
            )
        }

        BudgetFigureRow(monthlyEvaluation.spent, monthlyEvaluation.allocated, modifier = Modifier.padding(top = 6.dp))

        Spacer(Modifier.height(6.dp))

        // Weekly segments — widths proportional to each week's share of the month. Click a segment to see its own figures below.
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for ((index, week) in weeks.withIndex()) {
                val weight = week.evaluation.allocated.minorUnits.coerceAtLeast(1).toFloat()
                val weekColor = statusColor(week.evaluation.status)
                val weekFraction = if (week.evaluation.allocated.minorUnits <= 0) {
                    0f
                } else {
                    (week.evaluation.spent.minorUnits.toFloat() / week.evaluation.allocated.minorUnits.toFloat()).coerceIn(0f, 1f)
                }
                Box(
                    Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(weekColor.copy(alpha = 0.15f))
                        .clickable { selectedIndex = index },
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(weekFraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(weekColor),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for ((index, week) in weeks.withIndex()) {
                val weight = week.evaluation.allocated.minorUnits.coerceAtLeast(1).toFloat()
                Box(
                    Modifier.weight(weight).clickable { selectedIndex = index },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        "${dayOfMonth(week.weekStart)}–${dayOfMonth(week.weekEnd)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (index == selectedIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        weeks.getOrNull(selectedIndex)?.let { selectedWeek ->
            Spacer(Modifier.height(4.dp))
            Text(
                "Week of ${dayOfMonth(selectedWeek.weekStart)}–${dayOfMonth(selectedWeek.weekEnd)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BudgetFigureRow(selectedWeek.evaluation.spent, selectedWeek.evaluation.allocated, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun dayOfMonth(isoDate: String): String = isoDate.substringAfterLast('-').trimStart('0').ifEmpty { "0" }
