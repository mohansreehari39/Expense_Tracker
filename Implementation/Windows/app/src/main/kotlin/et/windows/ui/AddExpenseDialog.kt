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
import et.windows.server.CategoryDto
import et.windows.server.HouseholdDependentDto
import et.windows.server.HouseholdExpenseDto
import et.windows.server.MemberDto
import et.windows.server.RecordExpenseRequest
import et.windows.server.SplitModeDto
import et.windows.server.SubcategoryDto

/**
 * Also used to edit an existing expense, when [expenseToEdit] is
 * non-null. There's no separate "Paid by" chooser — the "Who chipped in"
 * split defaults to 100% on the first member and is the single source of
 * truth for who paid; [RecordExpenseRequest.paidByMemberId] is derived
 * from whichever contributor ends up with the largest share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    categories: List<CategoryDto>,
    members: List<MemberDto>,
    dependents: List<HouseholdDependentDto>,
    currency: String,
    expenseToEdit: HouseholdExpenseDto? = null,
    onDismiss: () -> Unit,
    onCreateCategory: suspend (String) -> CategoryDto,
    onCreateSubcategory: suspend (categoryId: String, name: String) -> SubcategoryDto,
    onSubmit: (RecordExpenseRequest) -> Unit,
) {
    var amountText by remember {
        mutableStateOf(expenseToEdit?.let { (it.amount.minorUnits / 100.0).toString() } ?: "")
    }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    var localCategories by remember { mutableStateOf(categories) }
    var selectedCategoryId by remember { mutableStateOf(expenseToEdit?.categoryId ?: categories.firstOrNull()?.id) }
    var selectedSubcategoryId by remember { mutableStateOf(expenseToEdit?.subcategoryId) }
    val availableSubcategories = localCategories.find { it.id == selectedCategoryId }?.subcategories ?: emptyList()
    val payerFallback = expenseToEdit?.paidByMemberId ?: members.firstOrNull()?.id
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && selectedCategoryId != null && payerFallback != null

    val beneficiaryCandidates = members.map { SplitCandidate(it.id, it.displayName) } +
        dependents.map { SplitCandidate(it.id, "${it.name} (${it.category.lowercase()})") }
    val contributionCandidates = members.map { SplitCandidate(it.id, it.displayName) }

    var beneficiaryAmounts by remember { mutableStateOf<Map<String, Long>?>(null) }
    var contributionAmounts by remember { mutableStateOf<Map<String, Long>?>(null) }
    var showBeneficiaryEditor by remember { mutableStateOf(false) }
    var showContributionEditor by remember { mutableStateOf(false) }

    val effectiveBeneficiaries = beneficiaryAmounts
        ?: equalSplitMinorUnits(amountMinorUnits ?: 0L, members.map { it.id })
    val effectiveContributions = contributionAmounts
        ?: (payerFallback?.let { mapOf(it to (amountMinorUnits ?: 0L)) } ?: emptyMap())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expenseToEdit == null) "Add Household Expense" else "Edit Household Expense") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(
                    categories = localCategories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = {
                        selectedCategoryId = it.id
                        selectedSubcategoryId = null
                    },
                    onCreateCategory = { name ->
                        val created = onCreateCategory(name)
                        if (localCategories.none { it.id == created.id }) {
                            localCategories = localCategories + created
                        }
                        selectedSubcategoryId = null
                        created
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (selectedCategoryId != null) {
                    SubcategoryPicker(
                        subcategories = availableSubcategories,
                        selectedSubcategoryId = selectedSubcategoryId,
                        onSubcategorySelected = { selectedSubcategoryId = it.id },
                        onCreateSubcategory = { name ->
                            val created = onCreateSubcategory(selectedCategoryId!!, name)
                            localCategories = localCategories.map { c ->
                                if (c.id == selectedCategoryId) c.copy(subcategories = c.subcategories + created) else c
                            }
                            created
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Who's it for", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showBeneficiaryEditor = true }) {
                        Text(summarizeSplit(effectiveBeneficiaries, beneficiaryCandidates, amountMinorUnits ?: 0L))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Who chipped in", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showContributionEditor = true }) {
                        Text(summarizeSplit(effectiveContributions, contributionCandidates, amountMinorUnits ?: 0L))
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
                        RecordExpenseRequest(
                            categoryId = selectedCategoryId!!,
                            subcategoryId = selectedSubcategoryId,
                            amountMinorUnits = amountMinorUnits!!,
                            currency = currency,
                            paidByMemberId = paidBy,
                            occurredAt = occurredAt,
                            note = note,
                            beneficiarySplit = SplitModeDto(type = "EXACT", exactAmountsMinorUnits = effectiveBeneficiaries),
                            contributionSplit = SplitModeDto(type = "EXACT", exactAmountsMinorUnits = contributions),
                        ),
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
            initialAmounts = effectiveBeneficiaries,
            onDismiss = { showBeneficiaryEditor = false },
            onSave = { beneficiaryAmounts = it; showBeneficiaryEditor = false },
        )
    }
    if (showContributionEditor) {
        SplitEditorDialog(
            title = "Who chipped in",
            candidates = contributionCandidates,
            totalAmountMinorUnits = amountMinorUnits ?: 0L,
            currency = currency,
            initialAmounts = effectiveContributions,
            onDismiss = { showContributionEditor = false },
            onSave = { contributionAmounts = it; showContributionEditor = false },
        )
    }
}
