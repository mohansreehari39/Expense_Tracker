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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.local.CategoryEntity
import et.android.kharcha.data.local.HouseholdDependentEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.SubcategoryEntity
import et.android.kharcha.data.parseAmountMinorUnits
import et.core.domain.SplitDefaults
import et.core.domain.SplitDraft
import et.core.model.Money

/**
 * Also used to edit an existing expense, when [expenseToEdit] is
 * non-null. There's no separate "Paid by" chooser — [defaultMemberId]
 * (whoever's using this device) is who the "Who chipped in" split
 * defaults to 100% on; the actual [HouseholdExpenseEntity.paidByMemberId]
 * stored on the row is derived from whichever contributor ends up with
 * the largest share once that split is edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHouseholdExpenseDialog(
    categories: List<CategoryEntity>,
    members: List<MemberEntity>,
    dependents: List<HouseholdDependentEntity>,
    currency: String,
    defaultMemberId: String?,
    expenseToEdit: HouseholdExpenseEntity? = null,
    /** When editing: the expense's saved splits (id → minor units), loaded by the caller. */
    savedBeneficiaries: Map<String, Long>? = null,
    savedContributions: Map<String, Long>? = null,
    onDismiss: () -> Unit,
    onCreateCategory: suspend (String) -> CategoryEntity,
    onGetSubcategories: suspend (categoryId: String) -> List<SubcategoryEntity>,
    onCreateSubcategory: suspend (categoryId: String, name: String) -> SubcategoryEntity,
    onSubmit: (
        categoryId: String,
        subcategoryId: String?,
        amountMinorUnits: Long,
        paidByMemberId: String,
        occurredAt: Long,
        note: String,
        beneficiaries: List<Pair<String, Long>>,
        contributions: List<Pair<String, Long>>,
    ) -> Unit,
) {
    var amountText by remember { mutableStateOf(expenseToEdit?.let { Money.toPlainString(it.amountMinorUnits) } ?: "") }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    var categories by remember { mutableStateOf(categories) }
    var selectedCategoryId by remember { mutableStateOf(expenseToEdit?.categoryId ?: categories.firstOrNull()?.id) }
    var selectedSubcategoryId by remember { mutableStateOf(expenseToEdit?.subcategoryId) }
    var subcategories by remember { mutableStateOf(emptyList<SubcategoryEntity>()) }
    val payerFallback = defaultMemberId ?: members.firstOrNull()?.id
    val amountMinorUnits = parseAmountMinorUnits(amountText)
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && selectedCategoryId != null && payerFallback != null

    val beneficiaryCandidates = members.map { SplitCandidate(it.id, it.displayName) } +
        dependents.map { SplitCandidate(it.id, "${it.name} (${it.category.lowercase()})") }
    val contributionCandidates = members.map { SplitCandidate(it.id, it.displayName) }

    // null means "still the default" — recomputed live from the current amount; once the user
    // saves from the sub-dialog it becomes a fixed, explicit map (see README's beneficiary/
    // contributor split entry for why: defaults must stay visible/live, not a one-time snapshot).
    // null = still the default, which keeps following the current people and amount.
    // Editing an expense re-opens its saved split instead.
    var beneficiaryDraft by remember(savedBeneficiaries) { mutableStateOf<SplitDraft?>(savedBeneficiaries?.let { saved -> SplitDraft.fromSaved(saved, beneficiaryCandidates.map { it.id }) }) }
    var contributionDraft by remember(savedContributions) { mutableStateOf<SplitDraft?>(savedContributions?.let { saved -> SplitDraft.fromSaved(saved, contributionCandidates.map { it.id }) }) }
    var showBeneficiaryEditor by remember { mutableStateOf(false) }
    var showContributionEditor by remember { mutableStateOf(false) }

    val beneficiarySplit = beneficiaryDraft ?: SplitDefaults.beneficiaries(members.map { it.id })
    val contributionSplit = contributionDraft ?: SplitDefaults.contributions(payerFallback)
    val effectiveBeneficiaries = beneficiarySplit.resolve(amountMinorUnits ?: 0L)
    val effectiveContributions = contributionSplit.resolve(amountMinorUnits ?: 0L)
    val splitsValid = amountMinorUnits != null && beneficiarySplit.isValid(amountMinorUnits) && contributionSplit.isValid(amountMinorUnits)

    LaunchedEffect(selectedCategoryId) {
        subcategories = selectedCategoryId?.let { onGetSubcategories(it) } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expenseToEdit == null) "Add Household Expense" else "Edit Expense") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($currency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                CategoryPicker(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = {
                        selectedCategoryId = it.id
                        selectedSubcategoryId = null
                    },
                    onCreateCategory = { name ->
                        val created = onCreateCategory(name)
                        if (categories.none { it.id == created.id }) {
                            categories = categories + created
                        }
                        selectedSubcategoryId = null
                        created
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (selectedCategoryId != null) {
                    SubcategoryPicker(
                        subcategories = subcategories,
                        selectedSubcategoryId = selectedSubcategoryId,
                        onSubcategorySelected = { selectedSubcategoryId = it.id },
                        onCreateSubcategory = { name ->
                            val created = onCreateSubcategory(selectedCategoryId!!, name)
                            subcategories = subcategories + created
                            created
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                DateField(label = "Date", occurredAtMillis = occurredAt, onDateSelected = { occurredAt = it }, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SplitSummaryRow(
                    label = "Who's it for",
                    summary = summarizeSplit(effectiveBeneficiaries, beneficiaryCandidates, amountMinorUnits ?: 0L),
                    onClick = { showBeneficiaryEditor = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SplitSummaryRow(
                    label = "Who chipped in",
                    summary = summarizeSplit(effectiveContributions, contributionCandidates, amountMinorUnits ?: 0L),
                    onClick = { showContributionEditor = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit && splitsValid,
                onClick = {
                    val contributions = effectiveContributions
                    val paidBy = SplitDefaults.payerOf(contributions, payerFallback)!!
                    onSubmit(
                        selectedCategoryId!!,
                        selectedSubcategoryId,
                        amountMinorUnits!!,
                        paidBy,
                        occurredAt,
                        note,
                        effectiveBeneficiaries.toList(),
                        contributions.toList(),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showBeneficiaryEditor) {
        SplitEditorDialog(
            title = "Who's it for",
            candidates = beneficiaryCandidates,
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
            candidates = contributionCandidates,
            totalAmountMinorUnits = amountMinorUnits ?: 0L,
            currency = currency,
            initialDraft = contributionSplit,
            onDismiss = { showContributionEditor = false },
            onSave = { contributionDraft = it; showContributionEditor = false },
        )
    }
}

/** A collapsed row showing the current split's summary, tap to open [SplitEditorDialog]. */
@Composable
private fun SplitSummaryRow(label: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = onClick) { Text(summary) }
    }
}
