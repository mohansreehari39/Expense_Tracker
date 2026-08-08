package et.windows.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.windows.server.HouseholdDependentDto
import et.windows.server.MemberDto
import et.windows.server.MoneyDto
import kotlinx.coroutines.launch

/**
 * Household-level settings: rename, the *default* monthly budget used for
 * any month that doesn't have its own override (set from the household's
 * main content view — see MonthlyBudgetOverrideDialog), and member
 * management (add/remove). Categories aren't managed here — they're
 * created inline from the Add Expense dropdown and don't need cleanup.
 */
@Composable
fun HouseholdSettingsDialog(
    api: ApiClient,
    householdId: String,
    currentName: String,
    currentDefaultBudget: MoneyDto?,
    currentSettlementEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, defaultBudget: MoneyDto?, settlementEnabled: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var budgetText by remember { mutableStateOf(currentDefaultBudget?.let { (it.minorUnits / 100.0).toString() } ?: "") }
    var settlementEnabled by remember { mutableStateOf(currentSettlementEnabled) }
    var members by remember { mutableStateOf<List<MemberDto>>(emptyList()) }
    var showAddMember by remember { mutableStateOf(false) }
    var dependents by remember { mutableStateOf<List<HouseholdDependentDto>>(emptyList()) }
    var showAddDependent by remember { mutableStateOf(false) }
    var membersExpanded by remember { mutableStateOf(false) }
    var dependentsExpanded by remember { mutableStateOf(false) }
    val currency = currentDefaultBudget?.currency ?: "INR"
    val budgetMinorUnits = budgetText.toDoubleOrNull()?.let { (it * 100).toLong() }
    val budgetTextIsValid = budgetText.isBlank() || (budgetMinorUnits != null && budgetMinorUnits > 0)
    val scope = rememberCoroutineScope()

    suspend fun reloadMembers() {
        val response = api.household(householdId)
        members = response.members
        dependents = response.dependents
    }

    LaunchedEffect(householdId) { reloadMembers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Household Settings") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
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

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Per-person settlement", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Track each member's equal-split balance — who owes whom — instead of a shared pot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settlementEnabled, onCheckedChange = { settlementEnabled = it })
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { membersExpanded = !membersExpanded },
                    ) {
                        Text(if (membersExpanded) "▾" else "▸", modifier = Modifier.width(20.dp))
                        Text("Members (${members.size})", style = MaterialTheme.typography.titleSmall)
                    }
                    TextButton(onClick = { showAddMember = true }) { Text("+ Add") }
                }
                if (membersExpanded) {
                    if (members.isEmpty()) {
                        Text(
                            "No members yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            members.forEach { member ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(member.displayName, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = {
                                        scope.launch {
                                            api.archiveMember(householdId, member.id)
                                            members = members.filterNot { it.id == member.id }
                                        }
                                    }) { Text("✕ Remove") }
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dependentsExpanded = !dependentsExpanded },
                    ) {
                        Text(if (dependentsExpanded) "▾" else "▸", modifier = Modifier.width(20.dp))
                        Text("Dependents (${dependents.size})", style = MaterialTheme.typography.titleSmall)
                    }
                    TextButton(onClick = { showAddDependent = true }) { Text("+ Add") }
                }
                if (dependentsExpanded) {
                    Text(
                        "Pets/kids/parents expenses can be spent on — never chip in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (dependents.isEmpty()) {
                        Text(
                            "No dependents yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            dependents.forEach { dependent ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${dependent.name} (${dependent.category.lowercase()})", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = {
                                        scope.launch {
                                            api.archiveHouseholdDependent(householdId, dependent.id)
                                            dependents = dependents.filterNot { it.id == dependent.id }
                                        }
                                    }) { Text("✕ Remove") }
                                }
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
                    onSave(name.trim(), defaultBudget, settlementEnabled)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showAddMember) {
        val existingIds = remember { members.map { it.id }.toSet() }
        // Computed once per dialog show, not per recomposition — onPollForJoin
        // below mutates state on every poll tick, which would otherwise
        // re-issue a fresh PairingSession secret each time, invalidating the
        // one embedded in the still-displayed QR before it's even scanned.
        val qrPayload = remember { encodeJoinInvite(joinInviteForHousehold(householdId, currentName)) }
        AddPersonDialog(
            title = "Add Member",
            qrPayload = qrPayload,
            onDismiss = { showAddMember = false },
            onPollForJoin = {
                val fresh = api.household(householdId).members
                val newMember = fresh.find { it.id !in existingIds }
                if (newMember != null) members = fresh
                newMember?.displayName
            },
        )
    }

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
                            val created = api.addHouseholdDependent(householdId, dependentName.trim(), dependentCategory)
                            dependents = dependents + created
                            showAddDependent = false
                        }
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDependent = false }) { Text("Cancel") } },
        )
    }
}
