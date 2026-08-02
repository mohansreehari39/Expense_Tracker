package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.local.CategoryEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.SubcategoryEntity

/** Also used to edit an existing expense, when [expenseToEdit] is non-null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHouseholdExpenseDialog(
    categories: List<CategoryEntity>,
    members: List<MemberEntity>,
    currency: String,
    defaultMemberId: String?,
    expenseToEdit: HouseholdExpenseEntity? = null,
    onDismiss: () -> Unit,
    onCreateCategory: suspend (String) -> CategoryEntity,
    onGetSubcategories: suspend (categoryId: String) -> List<SubcategoryEntity>,
    onCreateSubcategory: suspend (categoryId: String, name: String) -> SubcategoryEntity,
    onSubmit: (categoryId: String, subcategoryId: String?, amountMinorUnits: Long, paidByMemberId: String, occurredAt: Long, note: String) -> Unit,
) {
    var amountText by remember { mutableStateOf(expenseToEdit?.let { (it.amountMinorUnits / 100.0).toString() } ?: "") }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    var categories by remember { mutableStateOf(categories) }
    var selectedCategoryId by remember { mutableStateOf(expenseToEdit?.categoryId ?: categories.firstOrNull()?.id) }
    var selectedSubcategoryId by remember { mutableStateOf(expenseToEdit?.subcategoryId) }
    var subcategories by remember { mutableStateOf(emptyList<SubcategoryEntity>()) }
    var selectedMemberId by remember { mutableStateOf(expenseToEdit?.paidByMemberId ?: defaultMemberId ?: members.firstOrNull()?.id) }
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && selectedCategoryId != null && selectedMemberId != null

    LaunchedEffect(selectedCategoryId) {
        subcategories = selectedCategoryId?.let { onGetSubcategories(it) } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expenseToEdit == null) "Add Household Expense" else "Edit Expense") },
        text = {
            Column {
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

                Text("Paid by", style = MaterialTheme.typography.labelMedium)
                if (members.isEmpty()) {
                    Text(
                        "No members yet — add some from Settings.",
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

                DateField(label = "Date", occurredAtMillis = occurredAt, onDateSelected = { occurredAt = it }, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = { onSubmit(selectedCategoryId!!, selectedSubcategoryId, amountMinorUnits!!, selectedMemberId!!, occurredAt, note) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
