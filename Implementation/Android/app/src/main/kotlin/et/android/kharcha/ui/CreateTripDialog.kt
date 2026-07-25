package et.android.kharcha.ui

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

@Composable
fun CreateTripDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, budgetMinorUnits: Long, currency: String, participantNames: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var budgetText by remember { mutableStateOf("") }
    var participantsText by remember { mutableStateOf("") }
    val currency = "INR"
    val budgetMinorUnits = budgetText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val participantNames = participantsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val canSubmit = name.isNotBlank() && budgetMinorUnits != null && budgetMinorUnits > 0 && participantNames.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Activity") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Activity name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Budget ($currency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = participantsText,
                    onValueChange = { participantsText = it },
                    label = { Text("Participants, comma separated") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = { onSubmit(name.trim(), budgetMinorUnits!!, currency, participantNames) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
