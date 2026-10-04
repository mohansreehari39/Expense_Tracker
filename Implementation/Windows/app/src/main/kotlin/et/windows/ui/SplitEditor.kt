package et.windows.ui

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
import et.core.model.Money
import et.core.domain.SplitDefaults
import et.core.domain.SplitDraft

/**
 * One person a split can be entered against: [id] is a member/dependent/
 * participant id, [displayName] is what's shown, [excluded] is set for
 * dependents when this editor is used for contributions (never
 * selectable — dependents never chip in).
 */
data class SplitCandidate(val id: String, val displayName: String, val excluded: Boolean = false)

private enum class SplitUnit { AMOUNT, PERCENTAGE }

/** Collapsed-row label for a split — Core's [SplitDefaults.summarize], with names from [candidates]. */
fun summarizeSplit(amounts: Map<String, Long>, candidates: List<SplitCandidate>, totalMinorUnits: Long): String =
    SplitDefaults.summarize(amounts, { id -> candidates.find { it.id == id }?.displayName ?: "?" }, totalMinorUnits)

private fun formatPercent(minorUnits: Long, totalMinorUnits: Long): String {
    if (totalMinorUnits <= 0) return "0"
    val percent = minorUnits * 100.0 / totalMinorUnits
    return if (percent == percent.toLong().toDouble()) percent.toLong().toString() else "%.2f".format(percent)
}

/**
 * A dedicated sub-window for editing "who it's for" / "who chipped in",
 * opened from a summary row on the add-expense form. All split behavior
 * is Core's [SplitDraft], shared by both apps: ticking or unticking
 * someone re-spreads the "auto" shares; an amount the user types stays as
 * typed and only the rest is re-spread (clearing a field puts that person
 * back on auto); percentages always resolve to exact minor units, so the
 * total always adds up. The DB never stores a percentage.
 */
@Composable
fun SplitEditorDialog(
    title: String,
    candidates: List<SplitCandidate>,
    totalAmountMinorUnits: Long,
    currency: String,
    initialDraft: SplitDraft,
    onDismiss: () -> Unit,
    onSave: (SplitDraft) -> Unit,
) {
    val selectable = candidates.filter { !it.excluded }
    val order = selectable.map { it.id }
    var unit by remember { mutableStateOf(SplitUnit.AMOUNT) }
    var draft by remember { mutableStateOf(initialDraft) }
    // Raw text of the fields being typed in; every other field shows its live resolved value.
    var typed by remember { mutableStateOf(emptyMap<String, String>()) }
    val amounts = draft.resolve(totalAmountMinorUnits)
    val sum = amounts.values.sum()
    val isValid = draft.isValid(totalAmountMinorUnits)

    fun shown(id: String): String = when (unit) {
        SplitUnit.AMOUNT -> Money.toPlainString(amounts[id] ?: 0L)
        SplitUnit.PERCENTAGE -> formatPercent(amounts[id] ?: 0L, totalAmountMinorUnits)
    }

    fun onValueChange(id: String, newText: String) {
        typed = typed + (id to newText)
        draft = when {
            newText.isBlank() -> draft.unlock(id)
            unit == SplitUnit.AMOUNT -> draft.lockAmount(id, Money.parseMinorUnits(newText) ?: 0L)
            else -> draft.lockPercent(id, newText.toDoubleOrNull() ?: 0.0)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SplitUnitToggle(
                    isPercentage = unit == SplitUnit.PERCENTAGE,
                    onToggle = {
                        unit = if (it) SplitUnit.PERCENTAGE else SplitUnit.AMOUNT
                        typed = emptyMap()
                    },
                )
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    selectable.forEach { candidate ->
                        val isSelected = candidate.id in draft.selected
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    draft = draft.toggle(candidate.id, order)
                                    typed = typed - candidate.id
                                },
                            )
                            Text(candidate.displayName, modifier = Modifier.width(120.dp))
                            if (isSelected) {
                                val symbol = if (unit == SplitUnit.AMOUNT) "₹" else "%"
                                OutlinedTextField(
                                    value = typed[candidate.id] ?: shown(candidate.id),
                                    onValueChange = { onValueChange(candidate.id, it) },
                                    label = { Text(if (draft.isLocked(candidate.id)) symbol else "$symbol auto") },
                                    placeholder = { Text(shown(candidate.id)) },
                                    modifier = Modifier.width(110.dp),
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
                Text(
                    if (isValid) "Total: ₹${Money.toPlainString(sum)}" else "Total: ₹${Money.toPlainString(sum)} of ₹${Money.toPlainString(totalAmountMinorUnits)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(enabled = isValid, onClick = { onSave(draft) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val unitTrackWidth = 56.dp
private val unitTrackHeight = 28.dp
private val unitThumbSize = 22.dp
private val unitThumbInset = 3.dp

/** ₹ (left) / % (right) slide switch — same visual language as [ThemeToggleSwitch]. */
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
