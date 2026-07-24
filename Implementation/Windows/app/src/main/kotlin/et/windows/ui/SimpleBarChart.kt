package et.windows.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class BarEntry(val label: String, val value: Long, val color: Color, val valueText: String)

/**
 * Small dependency-free horizontal bar chart (no charting library needed
 * for a handful of categories/participants). Bars are proportional to the
 * largest [BarEntry.value] in the list; zero/negative-only lists render as
 * empty bars rather than crashing.
 */
@Composable
fun SimpleBarChart(entries: List<BarEntry>, modifier: Modifier = Modifier) {
    val maxValue = entries.maxOfOrNull { kotlin.math.abs(it.value) }?.coerceAtLeast(1) ?: 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (entry in entries) {
            val fraction = (kotlin.math.abs(entry.value).toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                    Text(entry.valueText, style = MaterialTheme.typography.bodyMedium)
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    drawRect(color = entry.color.copy(alpha = 0.15f))
                    drawRect(color = entry.color, size = size.copy(width = size.width * fraction))
                }
            }
        }
    }
}
