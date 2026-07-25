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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.windows.server.CategoryDto
import et.windows.server.MoneyDto
import kotlinx.coroutines.launch

/**
 * Household-level settings: rename, the *default* monthly budget used for
 * any month that doesn't have its own override (set from the household's
 * main content view — see MonthlyBudgetOverrideDialog), and category
 * management (rename is not offered — only add/remove; adding happens
 * inline from the Add Expense dropdown instead, this is just for cleanup).
 */
@Composable
fun HouseholdSettingsDialog(
    api: ApiClient,
    householdId: String,
    currentName: String,
    currentDefaultBudget: MoneyDto?,
    onDismiss: () -> Unit,
    onSave: (name: String, defaultBudget: MoneyDto?) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var budgetText by remember { mutableStateOf(currentDefaultBudget?.let { (it.minorUnits / 100.0).toString() } ?: "") }
    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    val currency = currentDefaultBudget?.currency ?: "INR"
    val budgetMinorUnits = budgetText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val budgetTextIsValid = budgetText.isBlank() || (budgetMinorUnits != null && budgetMinorUnits > 0)
    val scope = rememberCoroutineScope()

    LaunchedEffect(householdId) {
        categories = api.household(householdId).categories
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Household Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Household name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Default monthly budget ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !budgetTextIsValid,
                )
                Text(
                    "Applies to any month without its own override. Leave blank for no default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    "Categories",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                if (categories.isEmpty()) {
                    Text(
                        "None yet — add one from the Add Expense dialog.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        categories.forEach { category ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(category.name, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    scope.launch {
                                        api.archiveCategory(householdId, category.id)
                                        categories = categories.filterNot { it.id == category.id }
                                    }
                                }) { Text("✕ Remove") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && budgetTextIsValid,
                onClick = {
                    val defaultBudget = budgetMinorUnits?.let { MoneyDto(it, currency) }
                    onSave(name.trim(), defaultBudget)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
