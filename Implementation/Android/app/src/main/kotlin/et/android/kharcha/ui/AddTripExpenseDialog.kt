package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.data.local.ParticipantEntity

/**
 * Default: splits equally among every participant, 100% paid by whoever's
 * selected — both overridable via the split editors below. Also used to
 * edit, when [expenseToEdit] is non-null.
 */
@Composable
fun AddTripExpenseDialog(
    participants: List<ParticipantEntity>,
    currency: String,
    defaultParticipantId: String?,
    expenseToEdit: ActivityExpenseEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        amountMinorUnits: Long,
        paidByParticipantId: String,
        occurredAt: Long,
        note: String,
        beneficiaries: List<Pair<String, Long>>?,
        contributions: List<Pair<String, Long>>?,
    ) -> Unit,
) {
    var amountText by remember { mutableStateOf(expenseToEdit?.let { (it.amountMinorUnits / 100.0).toString() } ?: "") }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    var paidBy by remember { mutableStateOf(expenseToEdit?.paidByParticipantId ?: defaultParticipantId ?: participants.firstOrNull()?.id) }
    var beneficiarySplit by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var contributionSplit by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && paidBy != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expenseToEdit == null) "Add Activity Expense" else "Edit Expense") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($currency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Paid by", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(participants) { participant ->
                        FilterChip(
                            selected = paidBy == participant.id,
                            onClick = { paidBy = participant.id },
                            label = { Text(participant.displayName) },
                        )
                    }
                }
                DateField(label = "Date", occurredAtMillis = occurredAt, onDateSelected = { occurredAt = it }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SplitEditor(
                    label = "Who's it for",
                    candidates = participants.map { SplitCandidate(it.id, it.displayName) },
                    totalAmountMinorUnits = amountMinorUnits ?: 0L,
                    currency = currency,
                    defaultSelectedIds = participants.map { it.id }.toSet(),
                    onSplitChanged = { beneficiarySplit = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SplitEditor(
                    label = "Who chipped in",
                    candidates = participants.map { SplitCandidate(it.id, it.displayName) },
                    totalAmountMinorUnits = amountMinorUnits ?: 0L,
                    currency = currency,
                    defaultSelectedIds = setOfNotNull(paidBy),
                    onSplitChanged = { contributionSplit = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = { onSubmit(amountMinorUnits!!, paidBy!!, occurredAt, note, beneficiarySplit, contributionSplit) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
