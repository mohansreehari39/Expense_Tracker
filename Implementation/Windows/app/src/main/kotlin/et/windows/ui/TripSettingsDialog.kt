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
import et.windows.server.TripParticipantDto
import kotlinx.coroutines.launch

/** Activity-level settings: rename, budget, and participant management (add/remove). */
@Composable
fun TripSettingsDialog(
    api: ApiClient,
    tripId: String,
    currentName: String,
    currentBudgetMinorUnits: Long,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (name: String, budgetMinorUnits: Long) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var budgetText by remember { mutableStateOf((currentBudgetMinorUnits / 100.0).toString()) }
    var participants by remember { mutableStateOf<List<TripParticipantDto>>(emptyList()) }
    var showAddParticipant by remember { mutableStateOf(false) }
    val budgetMinorUnits = budgetText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val scope = rememberCoroutineScope()

    suspend fun reloadParticipants() {
        participants = api.trip(tripId).participants
    }

    LaunchedEffect(tripId) { reloadParticipants() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activity Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Activity name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Budget ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Participants", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showAddParticipant = true }) { Text("+ Add") }
                }
                if (participants.isEmpty()) {
                    Text(
                        "No participants yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        participants.forEach { participant ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(participant.displayName, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    scope.launch {
                                        api.archiveTripParticipant(tripId, participant.id)
                                        participants = participants.filterNot { it.id == participant.id }
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
                enabled = name.isNotBlank() && budgetMinorUnits != null && budgetMinorUnits > 0,
                onClick = { onSave(name.trim(), budgetMinorUnits!!) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showAddParticipant) {
        AddPersonDialog(
            title = "Add Participant",
            fieldLabel = "Participant name",
            qrPayload = encodeJoinInvite(JoinInvitePayload(kind = "activity", id = tripId, name = currentName)),
            onDismiss = { showAddParticipant = false },
            onAdd = { participantName ->
                api.addTripParticipant(tripId, participantName)
                reloadParticipants()
            },
        )
    }
}
