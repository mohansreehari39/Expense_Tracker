package et.android.kharcha.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.local.HouseholdDependentEntity
import et.android.kharcha.data.local.MemberEntity
import kotlinx.coroutines.launch

/** Rename/re-budget an existing household and remove members — the default monthly budget only, matching Windows' Household Settings dialog. Per-month overrides and adding members remain Windows-only for now. */
@Composable
fun HouseholdSettingsDialog(
    currentName: String,
    currentBudgetMinorUnits: Long?,
    currentCurrency: String,
    currentSettlementEnabled: Boolean,
    members: List<MemberEntity>,
    dependents: List<HouseholdDependentEntity>,
    onDismiss: () -> Unit,
    onSubmit: (name: String, budgetMinorUnits: Long?, currency: String, settlementEnabled: Boolean) -> Unit,
    onRemoveMember: (String) -> Unit,
    onAddDependent: suspend (name: String, category: String) -> HouseholdDependentEntity,
    onRemoveDependent: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var budgetText by remember { mutableStateOf(currentBudgetMinorUnits?.let { (it / 100.0).let { v -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString() } } ?: "") }
    var settlementEnabled by remember { mutableStateOf(currentSettlementEnabled) }
    var showAddDependent by remember { mutableStateOf(false) }
    val budgetMinorUnits = budgetText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val canSubmit = name.isNotBlank() && (budgetText.isBlank() || budgetMinorUnits != null)
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Household Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Household name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Default monthly budget ($currentCurrency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Per-person settlement", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Track each member's equal-split balance — who owes whom.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settlementEnabled, onCheckedChange = { settlementEnabled = it })
                }

                Text("Members", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                if (members.isEmpty()) {
                    Text("No members yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        members.forEach { member ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(member.displayName, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { onRemoveMember(member.id) }) { Text("✕ Remove") }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Dependents", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Pets/kids/parents expenses can be spent on — never chip in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showAddDependent = true }) { Text("+ Add") }
                }
                if (dependents.isEmpty()) {
                    Text("No dependents yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        dependents.forEach { dependent ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${dependent.name} (${dependent.category.lowercase()})", style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { onRemoveDependent(dependent.id) }) { Text("✕ Remove") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = { onSubmit(name.trim(), budgetMinorUnits, currentCurrency, settlementEnabled) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showAddDependent) {
        var dependentName by remember { mutableStateOf("") }
        var dependentCategory by remember { mutableStateOf("PET") }
        AlertDialog(
            onDismissRequest = { showAddDependent = false },
            title = { Text("Add Dependent") },
            text = {
                Column {
                    OutlinedTextField(
                        value = dependentName,
                        onValueChange = { dependentName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PET" to "Pet", "KID" to "Kid", "PARENT" to "Parent").forEach { (value, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = dependentCategory == value,
                                onClick = { dependentCategory = value },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = dependentName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            onAddDependent(dependentName.trim(), dependentCategory)
                            showAddDependent = false
                        }
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDependent = false }) { Text("Cancel") } },
        )
    }
}
