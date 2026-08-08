package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import et.windows.server.SplitModeDto
import et.windows.server.TripExpenseDto
import et.windows.server.TripParticipantDto

/**
 * No separate "Paid by" chooser — "Who chipped in" defaults to 100% on
 * the first participant and is the single source of truth for who paid;
 * the submitted `paidByParticipantId` is derived from whichever
 * contributor ends up with the largest share. Also used to edit an
 * existing expense, when [expenseToEdit] is non-null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTripExpenseDialog(
    participants: List<TripParticipantDto>,
    currency: String,
    expenseToEdit: TripExpenseDto? = null,
    onDismiss: () -> Unit,
    onSubmit: (amountMinorUnits: Long, paidByParticipantId: String, occurredAt: Long, note: String, beneficiarySplit: SplitModeDto?, contributionSplit: SplitModeDto?) -> Unit,
) {
    var amountText by remember {
        mutableStateOf(expenseToEdit?.let { (it.amount.minorUnits / 100.0).toString() } ?: "")
    }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    val payerFallback = expenseToEdit?.paidByParticipantId ?: participants.firstOrNull()?.id
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && payerFallback != null

    val candidates = participants.map { SplitCandidate(it.id, it.displayName) }

    var beneficiaryAmounts by remember { mutableStateOf<Map<String, Long>?>(null) }
    var contributionAmounts by remember { mutableStateOf<Map<String, Long>?>(null) }
    var showBeneficiaryEditor by remember { mutableStateOf(false) }
    var showContributionEditor by remember { mutableStateOf(false) }

    val effectiveBeneficiaries = beneficiaryAmounts
        ?: equalSplitMinorUnits(amountMinorUnits ?: 0L, participants.map { it.id })
    val effectiveContributions = contributionAmounts
        ?: (payerFallback?.let { mapOf(it to (amountMinorUnits ?: 0L)) } ?: emptyMap())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expenseToEdit == null) "Add Activity Expense" else "Edit Activity Expense") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
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
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Who's it for", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showBeneficiaryEditor = true }) {
                        Text(summarizeSplit(effectiveBeneficiaries, candidates, amountMinorUnits ?: 0L))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Who chipped in", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showContributionEditor = true }) {
                        Text(summarizeSplit(effectiveContributions, candidates, amountMinorUnits ?: 0L))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = {
                    val contributions = effectiveContributions
                    val paidBy = contributions.maxByOrNull { it.value }?.key ?: payerFallback!!
                    onSubmit(
                        amountMinorUnits!!,
                        paidBy,
                        occurredAt,
                        note,
                        SplitModeDto(type = "EXACT", exactAmountsMinorUnits = effectiveBeneficiaries),
                        SplitModeDto(type = "EXACT", exactAmountsMinorUnits = contributions),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showBeneficiaryEditor) {
        SplitEditorDialog(
            title = "Who's it for",
            candidates = candidates,
            totalAmountMinorUnits = amountMinorUnits ?: 0L,
            currency = currency,
            initialAmounts = effectiveBeneficiaries,
            onDismiss = { showBeneficiaryEditor = false },
            onSave = { beneficiaryAmounts = it; showBeneficiaryEditor = false },
        )
    }
    if (showContributionEditor) {
        SplitEditorDialog(
            title = "Who chipped in",
            candidates = candidates,
            totalAmountMinorUnits = amountMinorUnits ?: 0L,
            currency = currency,
            initialAmounts = effectiveContributions,
            onDismiss = { showContributionEditor = false },
            onSave = { contributionAmounts = it; showContributionEditor = false },
        )
    }
}
