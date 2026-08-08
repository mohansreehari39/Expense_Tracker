package et.android.kharcha.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.local.ParticipantEntity

/** Rename/re-budget an existing activity and remove participants, matching Windows' Activity Settings dialog. Adding participants remains Windows-only for now. */
@Composable
fun ActivitySettingsDialog(
    currentName: String,
    currentBudgetMinorUnits: Long,
    currentCurrency: String,
    participants: List<ParticipantEntity>,
    onDismiss: () -> Unit,
    onSubmit: (name: String, budgetMinorUnits: Long, currency: String) -> Unit,
    onRemoveParticipant: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var budgetText by remember {
        mutableStateOf((currentBudgetMinorUnits / 100.0).let { v -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString() })
    }
    val budgetMinorUnits = budgetText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = name.isNotBlank() && budgetMinorUnits != null && budgetMinorUnits > 0
    var participantsExpanded by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activity Settings") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
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
                    label = { Text("Budget ($currentCurrency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp).clickable { participantsExpanded = !participantsExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (participantsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    Text("Participants (${participants.size})", style = MaterialTheme.typography.titleSmall)
                }
                if (participantsExpanded) {
                    if (participants.isEmpty()) {
                        Text("No participants yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            participants.forEach { participant ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(participant.displayName, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { onRemoveParticipant(participant.id) }) { Text("✕ Remove") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = { onSubmit(name.trim(), budgetMinorUnits!!, currentCurrency) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
