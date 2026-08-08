package et.android.kharcha.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** One person a split can be entered against — a member/dependent/participant id + display name. */
data class SplitCandidate(val id: String, val displayName: String)

private enum class SplitUnit { AMOUNT, PERCENTAGE }

/** Equal-split remainder-to-first rule — mirrors Core's `SplitCalculator.splitEqually`. */
fun equalSplitMinorUnits(totalMinorUnits: Long, ids: List<String>): Map<String, Long> {
    if (ids.isEmpty()) return emptyMap()
    val base = totalMinorUnits / ids.size
    val remainder = totalMinorUnits - base * ids.size
    return ids.mapIndexed { index, id -> id to (base + if (index == 0) remainder else 0) }.toMap()
}

/** A short, human label for a resolved split — used on the collapsed summary row that opens [SplitEditorDialog]. */
fun summarizeSplit(amounts: Map<String, Long>, candidates: List<SplitCandidate>, totalMinorUnits: Long): String {
    if (amounts.isEmpty()) return "Not set"
    if (amounts.size == 1) {
        val (id, amount) = amounts.entries.first()
        val name = candidates.find { it.id == id }?.displayName ?: "?"
        return if (amount == totalMinorUnits) "100% $name" else "$name only"
    }
    val values = amounts.values
    val isEqual = values.maxOrNull()?.let { max -> values.minOrNull()?.let { min -> max - min <= 1 } } ?: true
    return if (isEqual) "Split equally among ${amounts.size}" else "Custom split among ${amounts.size}"
}

private fun formatAmount(minorUnits: Long) = (minorUnits / 100.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
private fun formatPercent(minorUnits: Long, totalMinorUnits: Long): String {
    if (totalMinorUnits <= 0) return "0"
    val percent = minorUnits * 100.0 / totalMinorUnits
    return if (percent == percent.toLong().toDouble()) percent.toLong().toString() else "%.1f".format(percent)
}

/**
 * A dedicated sub-window for editing "who it's for" / "who chipped in" —
 * opened from a summary row on the add-expense form, not shown inline
 * (see README's V1 "Expense beneficiaries"/"Expense contributors"
 * entries). Opens pre-filled with [initialAmounts] (the default split —
 * equal-across-members for beneficiaries, 100%-on-payer for
 * contributions), already showing concrete amounts, not an empty form.
 * A single Amount/Percentage toggle controls the unit of every editable
 * field; percentages are always resolved to concrete minor-units
 * (rounded) before [onSave] is called — this app has no server-side
 * validation step, so that resolution happens right here.
 */
@Composable
fun SplitEditorDialog(
    title: String,
    candidates: List<SplitCandidate>,
    totalAmountMinorUnits: Long,
    currency: String,
    initialAmounts: Map<String, Long>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Long>) -> Unit,
) {
    var unit by remember { mutableStateOf(SplitUnit.AMOUNT) }
    var selectedIds by remember { mutableStateOf(initialAmounts.keys) }
    var amounts by remember { mutableStateOf(initialAmounts) }
    var text by remember { mutableStateOf(initialAmounts.mapValues { (_, v) -> formatAmount(v) }) }

    fun switchUnit(newUnit: SplitUnit) {
        text = selectedIds.associateWith { id ->
            when (newUnit) {
                SplitUnit.AMOUNT -> formatAmount(amounts[id] ?: 0L)
                SplitUnit.PERCENTAGE -> formatPercent(amounts[id] ?: 0L, totalAmountMinorUnits)
            }
        }
        unit = newUnit
    }

    fun onValueChange(id: String, newText: String) {
        text = text + (id to newText)
        val parsed = newText.toDoubleOrNull() ?: 0.0
        val minorUnits = when (unit) {
            SplitUnit.AMOUNT -> (parsed * 100).toLong()
            SplitUnit.PERCENTAGE -> (totalAmountMinorUnits * parsed / 100.0).toLong()
        }
        amounts = amounts + (id to minorUnits)
    }

    val sum = selectedIds.sumOf { amounts[it] ?: 0L }
    val isValid = selectedIds.isNotEmpty() && sum == totalAmountMinorUnits

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SplitUnitToggle(isPercentage = unit == SplitUnit.PERCENTAGE, onToggle = { switchUnit(if (it) SplitUnit.PERCENTAGE else SplitUnit.AMOUNT) })
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    candidates.forEach { candidate ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(candidate.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + candidate.id else selectedIds - candidate.id
                                    if (checked && amounts[candidate.id] == null) {
                                        amounts = amounts + (candidate.id to 0L)
                                        text = text + (candidate.id to "0")
                                    }
                                },
                            )
                            Text(candidate.displayName, modifier = Modifier.width(120.dp))
                            if (selectedIds.contains(candidate.id)) {
                                OutlinedTextField(
                                    value = text[candidate.id] ?: "",
                                    onValueChange = { onValueChange(candidate.id, it) },
                                    label = { Text(if (unit == SplitUnit.AMOUNT) "₹" else "%") },
                                    modifier = Modifier.width(100.dp),
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
                Text(
                    if (isValid) "Total: ₹${formatAmount(sum)}" else "Total: ₹${formatAmount(sum)} of ₹${formatAmount(totalAmountMinorUnits)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(enabled = isValid, onClick = { onSave(selectedIds.associateWith { amounts[it] ?: 0L }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val unitTrackWidth = 56.dp
private val unitTrackHeight = 28.dp
private val unitThumbSize = 22.dp
private val unitThumbInset = 3.dp

/** ₹ (left) / % (right) slide switch — same visual language as the app's dark/light mode switch. */
@Composable
private fun SplitUnitToggle(isPercentage: Boolean, onToggle: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (isPercentage) unitTrackWidth - unitThumbSize - unitThumbInset else unitThumbInset)
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val activeColor = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .width(unitTrackWidth)
            .height(unitTrackHeight)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .clickable { onToggle(!isPercentage) },
    ) {
        Text("₹", modifier = Modifier.align(Alignment.CenterStart).padding(start = 7.dp), style = MaterialTheme.typography.labelSmall, color = dimColor)
        Text("%", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), style = MaterialTheme.typography.labelSmall, color = dimColor)
        Box(
            Modifier
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .size(unitThumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (isPercentage) "%" else "₹", style = MaterialTheme.typography.labelSmall, color = activeColor)
        }
    }
}
