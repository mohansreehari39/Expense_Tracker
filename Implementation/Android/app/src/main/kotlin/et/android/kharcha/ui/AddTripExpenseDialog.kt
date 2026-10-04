package et.android.kharcha.ui

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
import et.android.kharcha.data.parseAmountMinorUnits
import et.core.domain.SplitDefaults
import et.core.domain.SplitDraft
import et.core.model.Money

/**
 * No separate "Paid by" chooser — [defaultParticipantId] (whoever's using
 * this device) is who the "Who chipped in" split defaults to 100% on; the
 * actual [ActivityExpenseEntity.paidByParticipantId] stored on the row is
 * derived from whichever contributor ends up with the largest share.
 * Also used to edit, when [expenseToEdit] is non-null.
 */
@Composable
fun AddTripExpenseDialog(
    participants: List<ParticipantEntity>,
    currency: String,
    defaultParticipantId: String?,
    expenseToEdit: ActivityExpenseEntity? = null,
    /** When editing: the expense's saved splits (id → minor units), loaded by the caller. */
    savedBeneficiaries: Map<String, Long>? = null,
    savedContributions: Map<String, Long>? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        amountMinorUnits: Long,
        paidByParticipantId: String,
        occurredAt: Long,
        note: String,
        beneficiaries: List<Pair<String, Long>>,
        contributions: List<Pair<String, Long>>,
    ) -> Unit,
) {
    var amountText by remember { mutableStateOf(expenseToEdit?.let { Money.toPlainString(it.amountMinorUnits) } ?: "") }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    val payerFallback = expenseToEdit?.paidByParticipantId ?: defaultParticipantId ?: participants.firstOrNull()?.id
    val amountMinorUnits = parseAmountMinorUnits(amountText)
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && payerFallback != null

    val candidates = participants.map { SplitCandidate(it.id, it.displayName) }

    // null = still the default, which keeps following the current people and amount.
    // Editing an expense re-opens its saved split instead.
    var beneficiaryDraft by remember(savedBeneficiaries) { mutableStateOf<SplitDraft?>(savedBeneficiaries?.let { saved -> SplitDraft.fromSaved(saved, candidates.map { it.id }) }) }
    var contributionDraft by remember(savedContributions) { mutableStateOf<SplitDraft?>(savedContributions?.let { saved -> SplitDraft.fromSaved(saved, candidates.map { it.id }) }) }
    var showBeneficiaryEditor by remember { mutableStateOf(false) }
    var showContributionEditor by remember { mutableStateOf(false) }

    val beneficiarySplit = beneficiaryDraft ?: SplitDefaults.beneficiaries(participants.map { it.id })
    val contributionSplit = contributionDraft ?: SplitDefaults.contributions(payerFallback)
    val effectiveBeneficiaries = beneficiarySplit.resolve(amountMinorUnits ?: 0L)
    val effectiveContributions = contributionSplit.resolve(amountMinorUnits ?: 0L)
    val splitsValid = amountMinorUnits != null && beneficiarySplit.isValid(amountMinorUnits) && contributionSplit.isValid(amountMinorUnits)

    ExpenseSheet(
        title = if (expenseToEdit == null) "New activity expense" else "Edit expense",
        amountText = amountText,
        onAmountChange = { amountText = it },
        currency = currency,
        saveEnabled = canSubmit && splitsValid,
        onDismiss = onDismiss,
        onSave = {
            val contributions = effectiveContributions
            val paidBy = SplitDefaults.payerOf(contributions, payerFallback)!!
            onSubmit(amountMinorUnits!!, paidBy, occurredAt, note, effectiveBeneficiaries.toList(), contributions.toList())
        },
    ) {
        DateField(label = "Date", occurredAtMillis = occurredAt, onDateSelected = { occurredAt = it }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SplitSummaryRow(label = "Who's it for", summary = summarizeSplit(effectiveBeneficiaries, candidates, amountMinorUnits ?: 0L), onClick = { showBeneficiaryEditor = true })
        SplitSummaryRow(label = "Who chipped in", summary = summarizeSplit(effectiveContributions, candidates, amountMinorUnits ?: 0L), onClick = { showContributionEditor = true })
    }

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
