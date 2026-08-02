package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.windows.server.SplitModeDto

private enum class SplitEditorMode { DEFAULT, EQUAL, PERCENTAGE, EXACT }

/**
 * One person a split can be entered against: [id] is a member/dependent/
 * participant id, [displayName] is what's shown, [excluded] is set for
 * dependents when this editor is used for contributions (never
 * selectable — dependents never chip in).
 */
data class SplitCandidate(val id: String, val displayName: String, val excluded: Boolean = false)

/**
 * Shared "who it's for" / "who chipped in" split entry, both apps'
 * beneficiary and contribution UIs use this same shape (see README's V1
 * "Expense beneficiaries"/"Expense contributors" entries). Percentage
 * entry is UI-only — [onSplitChanged] always emits either `null` (meaning
 * "use the default": equal-split for beneficiaries, 100%-on-payer for
 * contributions) or a [SplitModeDto] already carrying concrete
 * `EXACT`/`PERCENTAGE` values resolved server-side to real money before
 * anything is persisted.
 *
 * [defaultSelectedIds] is who's included when the user hasn't customized
 * anything yet (every active member for beneficiaries, just the payer for
 * contributions).
 */
@Composable
fun SplitEditor(
    label: String,
    candidates: List<SplitCandidate>,
    totalAmountMinorUnits: Long,
    currency: String,
    defaultSelectedIds: Set<String>,
    onSplitChanged: (SplitModeDto?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(SplitEditorMode.DEFAULT) }
    var selectedIds by remember(candidates) { mutableStateOf(defaultSelectedIds) }
    var percentText by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var exactText by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val selectable = candidates.filter { !it.excluded }

    LaunchedEffect(mode, selectedIds, percentText, exactText) {
        onSplitChanged(
            when (mode) {
                SplitEditorMode.DEFAULT -> null
                SplitEditorMode.EQUAL -> SplitModeDto(type = "EQUAL", participantIds = selectedIds.toList())
                SplitEditorMode.PERCENTAGE -> {
                    val percents = selectedIds.associateWith { percentText[it]?.toDoubleOrNull() ?: 0.0 }
                    SplitModeDto(type = "PERCENTAGE", percentages = percents)
                }
                SplitEditorMode.EXACT -> {
                    val amounts = selectedIds.associateWith { id -> ((exactText[id]?.toDoubleOrNull() ?: 0.0) * 100).toLong() }
                    SplitModeDto(type = "EXACT", exactAmountsMinorUnits = amounts)
                }
            },
        )
    }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = mode == SplitEditorMode.DEFAULT, onClick = { mode = SplitEditorMode.DEFAULT }, label = { Text("Default") })
            FilterChip(selected = mode == SplitEditorMode.EQUAL, onClick = { mode = SplitEditorMode.EQUAL }, label = { Text("Equal") })
            FilterChip(selected = mode == SplitEditorMode.PERCENTAGE, onClick = { mode = SplitEditorMode.PERCENTAGE }, label = { Text("Percentage") })
            FilterChip(selected = mode == SplitEditorMode.EXACT, onClick = { mode = SplitEditorMode.EXACT }, label = { Text("Exact amount") })
        }

        if (mode != SplitEditorMode.DEFAULT) {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                items(selectable) { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selectedIds.contains(candidate.id),
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + candidate.id else selectedIds - candidate.id
                            },
                        )
                        Text(candidate.displayName, modifier = Modifier.width(120.dp))
                        if (selectedIds.contains(candidate.id)) {
                            when (mode) {
                                SplitEditorMode.PERCENTAGE -> OutlinedTextField(
                                    value = percentText[candidate.id] ?: "",
                                    onValueChange = { percentText = percentText + (candidate.id to it) },
                                    label = { Text("%") },
                                    modifier = Modifier.width(90.dp),
                                    singleLine = true,
                                )
                                SplitEditorMode.EXACT -> OutlinedTextField(
                                    value = exactText[candidate.id] ?: "",
                                    onValueChange = { exactText = exactText + (candidate.id to it) },
                                    label = { Text(currency) },
                                    modifier = Modifier.width(90.dp),
                                    singleLine = true,
                                )
                                else -> {}
                            }
                        }
                    }
                }
            }
            val validationMessage = when (mode) {
                SplitEditorMode.PERCENTAGE -> {
                    val sum = selectedIds.sumOf { percentText[it]?.toDoubleOrNull() ?: 0.0 }
                    if (kotlin.math.abs(sum - 100.0) > 0.01) "Percentages sum to ${"%.1f".format(sum)}%, must total 100%" else null
                }
                SplitEditorMode.EXACT -> {
                    val sumMinor = selectedIds.sumOf { ((exactText[it]?.toDoubleOrNull() ?: 0.0) * 100).toLong() }
                    if (sumMinor != totalAmountMinorUnits) "Amounts sum to ${sumMinor / 100.0}, expense total is ${totalAmountMinorUnits / 100.0}" else null
                }
                SplitEditorMode.EQUAL -> if (selectedIds.isEmpty()) "Select at least one" else null
                SplitEditorMode.DEFAULT -> null
            }
            if (validationMessage != null) {
                Text(validationMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
