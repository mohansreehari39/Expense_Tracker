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

/**
 * Sets this specific month's budget, overriding the household's default
 * (see HouseholdSettingsDialog) for that month only. Distinct dialog from
 * the household-level default on purpose — the user was explicit that
 * "update budget" next to a week was confusing when it was actually
 * setting the month, so this now always shows which month it applies to.
 */
@Composable
fun MonthlyBudgetOverrideDialog(
    monthLabel: String,
    currency: String,
    currentEffectiveAmountMinorUnits: Long?,
    isOverride: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (amountMinorUnits: Long) -> Unit,
) {
    var amountText by remember {
        mutableStateOf(currentEffectiveAmountMinorUnits?.let { (it / 100.0).toString() } ?: "")
    }
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$monthLabel Budget") },
        text = {
            Column {
                val hint = when {
                    isOverride -> "This month has its own budget, overriding the household default."
                    currentEffectiveAmountMinorUnits != null -> "Currently using the household default. Saving here overrides it for $monthLabel only."
                    else -> "No default budget is set for this household yet. This will apply to $monthLabel only."
                }
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Budget for $monthLabel ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = amountMinorUnits != null && amountMinorUnits > 0,
                onClick = { onSubmit(amountMinorUnits!!) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
