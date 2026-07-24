package et.windows.ui

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

@Composable
fun SetBudgetDialog(
    currency: String,
    onDismiss: () -> Unit,
    onSubmit: (amountMinorUnits: Long, currency: String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    val amountMinorUnits = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set This Month's Budget") },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Total budget ($currency)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                enabled = amountMinorUnits != null && amountMinorUnits > 0,
                onClick = { onSubmit(amountMinorUnits!!, currency) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
