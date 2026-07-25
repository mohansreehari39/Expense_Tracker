package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import et.android.kharcha.data.CategoryDto
import et.android.kharcha.data.HouseholdExpenseDto
import et.android.kharcha.data.MemberDto
import et.android.kharcha.data.RecordExpenseRequest

/** Also used to edit an existing expense, when [expenseToEdit] is non-null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHouseholdExpenseDialog(
    categories: List<CategoryDto>,
    members: List<MemberDto>,
    currency: String,
    defaultMemberId: String?,
    expenseToEdit: HouseholdExpenseDto? = null,
    onDismiss: () -> Unit,
    onSubmit: (RecordExpenseRequest) -> Unit,
) {
    var amountText by remember { mutableStateOf(expenseToEdit?.let { (it.amount.minorUnits / 100.0).toString() } ?: "") }
    var note by remember { mutableStateOf(expenseToEdit?.note ?: "") }
    var occurredAt by remember { mutableStateOf(expenseToEdit?.occurredAt ?: System.currentTimeMillis()) }
    var selectedCategoryId by remember { mutableStateOf(expenseToEdit?.categoryId ?: categories.firstOrNull()?.id) }
    var selectedMemberId by remember { mutableStateOf(expenseToEdit?.paidByMemberId ?: defaultMemberId ?: members.firstOrNull()?.id) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && selectedCategoryId != null && selectedMemberId != null

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

                ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { selectedCategoryId = category.id; categoryMenuExpanded = false },
                            )
                        }
                    }
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
                onClick = {
                    onSubmit(
                        RecordExpenseRequest(
                            categoryId = selectedCategoryId!!,
                            amountMinorUnits = amountMinorUnits!!,
                            currency = currency,
                            paidByMemberId = selectedMemberId!!,
                            occurredAt = occurredAt,
                            note = note,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
