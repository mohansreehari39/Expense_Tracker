package et.windows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import et.windows.server.MoneyDto

/**
 * Household-level settings: rename, and the *default* monthly budget used
 * for any month that doesn't have its own override (set from the
 * household's main content view — see MonthlyBudgetOverrideDialog).
 */
@Composable
fun HouseholdSettingsDialog(
    currentName: String,
    currentDefaultBudget: MoneyDto?,
    onDismiss: () -> Unit,
    onSave: (name: String, defaultBudget: MoneyDto?) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var budgetText by remember { mutableStateOf(currentDefaultBudget?.let { (it.minorUnits / 100.0).toString() } ?: "") }
    val currency = currentDefaultBudget?.currency ?: "INR"
    val budgetMinorUnits = budgetText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val budgetTextIsValid = budgetText.isBlank() || (budgetMinorUnits != null && budgetMinorUnits > 0)

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
