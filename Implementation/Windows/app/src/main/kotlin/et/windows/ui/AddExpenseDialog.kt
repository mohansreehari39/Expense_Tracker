package et.windows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import et.windows.server.CategoryDto
import et.windows.server.MemberDto
import et.windows.server.RecordExpenseRequest

@Composable
fun AddExpenseDialog(
    categories: List<CategoryDto>,
    members: List<MemberDto>,
    currency: String,
    onDismiss: () -> Unit,
    onCreateCategory: suspend (String) -> CategoryDto,
    onCreateMember: suspend (String) -> MemberDto,
    onSubmit: (RecordExpenseRequest) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var localCategories by remember { mutableStateOf(categories) }
    var localMembers by remember { mutableStateOf(members) }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id) }
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = amountMinorUnits != null && amountMinorUnits > 0 && selectedCategoryId != null && selectedMemberId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Household Expense") },
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
                    onCategorySelected = { selectedCategoryId = it.id },
                    onCreateCategory = { name ->
                        val created = onCreateCategory(name)
                        if (localCategories.none { it.id == created.id }) {
                            localCategories = localCategories + created
                        }
                        created
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                MemberPicker(
                    members = localMembers,
                    selectedMemberId = selectedMemberId,
                    onMemberSelected = { selectedMemberId = it.id },
                    onCreateMember = { name ->
                        val created = onCreateMember(name)
                        if (localMembers.none { it.id == created.id }) {
                            localMembers = localMembers + created
                        }
                        created
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
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
                            occurredAt = System.currentTimeMillis(),
                            note = note,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
