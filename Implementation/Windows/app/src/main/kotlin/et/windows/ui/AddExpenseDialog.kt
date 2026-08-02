package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import et.windows.server.CategoryDto
import et.windows.server.HouseholdDependentDto
import et.windows.server.HouseholdExpenseDto
import et.windows.server.MemberDto
import et.windows.server.RecordExpenseRequest
import et.windows.server.SplitModeDto
import et.windows.server.SubcategoryDto

/** Also used to edit an existing expense, when [expenseToEdit] is non-null. */
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
    var selectedMemberId by remember { mutableStateOf(expenseToEdit?.paidByMemberId ?: members.firstOrNull()?.id) }
    var beneficiarySplit by remember { mutableStateOf<SplitModeDto?>(null) }
    var contributionSplit by remember { mutableStateOf<SplitModeDto?>(null) }
    val availableSubcategories = localCategories.find { it.id == selectedCategoryId }?.subcategories ?: emptyList()
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && selectedCategoryId != null && selectedMemberId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expenseToEdit == null) "Add Household Expense" else "Edit Household Expense") },
        text = {
            Column {
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
                Text("Paid by")
                if (members.isEmpty()) {
                    Text(
                        "No members yet — add some from the household settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(members) { member ->
                            FilterChip(
                                selected = selectedMemberId == member.id,
                                onClick = { selectedMemberId = member.id },
                                label = { Text(member.displayName) },
                            )
                        }
                    }
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
                SplitEditor(
                    label = "Who's it for",
                    candidates = members.map { SplitCandidate(it.id, it.displayName) } +
                        dependents.map { SplitCandidate(it.id, "${it.name} (${it.category.lowercase()})") },
                    totalAmountMinorUnits = amountMinorUnits ?: 0L,
                    currency = currency,
                    defaultSelectedIds = members.map { it.id }.toSet(),
                    onSplitChanged = { beneficiarySplit = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SplitEditor(
                    label = "Who chipped in",
                    candidates = members.map { SplitCandidate(it.id, it.displayName) },
                    totalAmountMinorUnits = amountMinorUnits ?: 0L,
                    currency = currency,
                    defaultSelectedIds = setOfNotNull(selectedMemberId),
                    onSplitChanged = { contributionSplit = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = {
                    onSubmit(
                        RecordExpenseRequest(
                            categoryId = selectedCategoryId!!,
                            subcategoryId = selectedSubcategoryId,
                            amountMinorUnits = amountMinorUnits!!,
                            currency = currency,
                            paidByMemberId = selectedMemberId!!,
                            occurredAt = occurredAt,
                            note = note,
                            beneficiarySplit = beneficiarySplit,
                            contributionSplit = contributionSplit,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
