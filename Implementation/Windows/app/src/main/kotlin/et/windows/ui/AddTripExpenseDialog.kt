package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import et.windows.server.TripExpenseDto
import et.windows.server.TripParticipantDto

/**
 * v0: always splits equally among every participant — see Routes.kt's
 * /trips/{id}/expenses handler. Also used to edit an existing expense, when
 * [expenseToEdit] is non-null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTripExpenseDialog(
    participants: List<TripParticipantDto>,
    currency: String,
    expenseToEdit: TripExpenseDto? = null,
    onDismiss: () -> Unit,
    onSubmit: (amountMinorUnits: Long, paidByParticipantId: String, occurredAt: Long, note: String) -> Unit,
) {
    var amountText by remember {
        mutableStateOf(expenseToEdit?.let { (it.amount.minorUnits / 100.0).toString() } ?: "")
    }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    var paidBy by remember { mutableStateOf(expenseToEdit?.paidByParticipantId ?: participants.firstOrNull()?.id) }
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && paidBy != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expenseToEdit == null) "Add Activity Expense" else "Edit Activity Expense") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Paid by")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(participants) { participant ->
                        FilterChip(
                            selected = paidBy == participant.id,
                            onClick = { paidBy = participant.id },
                            label = { Text(participant.displayName) },
                        )
                    }
                }
                Text("Split equally among all ${participants.size} participants", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                DateField(
                    label = "Date",
                    occurredAtMillis = occurredAt,
                    onDateSelected = { occurredAt = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(enabled = canSubmit, onClick = { onSubmit(amountMinorUnits!!, paidBy!!, occurredAt, note) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
