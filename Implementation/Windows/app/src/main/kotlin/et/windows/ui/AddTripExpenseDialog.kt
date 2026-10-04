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
import et.core.model.Money
import et.core.domain.SplitDefaults
import et.core.domain.SplitDraft

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
        mutableStateOf(expenseToEdit?.let { Money.toPlainString(it.amount.minorUnits) } ?: "")
    }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    val payerFallback = expenseToEdit?.paidByParticipantId ?: participants.firstOrNull()?.id
    val amountMinorUnits = Money.parseMinorUnits(amountText)
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && payerFallback != null

    val candidates = participants.map { SplitCandidate(it.id, it.displayName) }

    // null = still the default, which keeps following the current people and amount.
    // Editing an expense re-opens its saved split instead.
    var beneficiaryDraft by remember { mutableStateOf<SplitDraft?>(expenseToEdit?.beneficiaries?.takeIf { it.isNotEmpty() }?.associate { it.participantId to it.amount.minorUnits }?.let { saved -> SplitDraft.fromSaved(saved, candidates.map { it.id }) }) }
    var contributionDraft by remember { mutableStateOf<SplitDraft?>(expenseToEdit?.contributions?.takeIf { it.isNotEmpty() }?.associate { it.participantId to it.amount.minorUnits }?.let { saved -> SplitDraft.fromSaved(saved, candidates.map { it.id }) }) }
    var showBeneficiaryEditor by remember { mutableStateOf(false) }
    var showContributionEditor by remember { mutableStateOf(false) }

    val beneficiarySplit = beneficiaryDraft ?: SplitDefaults.beneficiaries(participants.map { it.id })
    val contributionSplit = contributionDraft ?: SplitDefaults.contributions(payerFallback)
    val effectiveBeneficiaries = beneficiarySplit.resolve(amountMinorUnits ?: 0L)
    val effectiveContributions = contributionSplit.resolve(amountMinorUnits ?: 0L)
    val splitsValid = amountMinorUnits != null && beneficiarySplit.isValid(amountMinorUnits) && contributionSplit.isValid(amountMinorUnits)

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
                enabled = canSubmit && splitsValid,
                onClick = {
                    val contributions = effectiveContributions
                    val paidBy = SplitDefaults.payerOf(contributions, payerFallback)!!
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
            initialDraft = beneficiarySplit,
            onDismiss = { showBeneficiaryEditor = false },
            onSave = { beneficiaryDraft = it; showBeneficiaryEditor = false },
        )
    }
    if (showContributionEditor) {
        SplitEditorDialog(
            title = "Who chipped in",
            candidates = candidates,
            totalAmountMinorUnits = amountMinorUnits ?: 0L,
            currency = currency,
            initialDraft = contributionSplit,
            onDismiss = { showContributionEditor = false },
            onSave = { contributionDraft = it; showContributionEditor = false },
        )
    }
}
