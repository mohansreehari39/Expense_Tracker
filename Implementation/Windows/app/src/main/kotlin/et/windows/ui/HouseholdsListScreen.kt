package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import et.windows.server.HouseholdDto
import kotlinx.coroutines.launch

@Composable
fun HouseholdsListScreen(api: ApiClient, onOpenHousehold: (id: String, name: String) -> Unit) {
    var households by remember { mutableStateOf<List<HouseholdDto>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            households = api.households()
            error = null
        } catch (e: Exception) {
            error = e.message ?: e::class.simpleName
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.padding(24.dp).fillMaxSize()) {
        error?.let { Text("Couldn't reach the server: $it", color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Households", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showCreate = true }) { Text("+ New Household") }
        }
        Spacer(Modifier.height(20.dp))

        if (households.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No households yet. Create one to start tracking monthly expenses.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(households) { household ->
                    HouseholdCard(household, onClick = { onOpenHousehold(household.id, household.name) })
                }
            }
        }
    }

    if (showCreate) {
        SimpleTextDialog(
            title = "New Household",
            label = "Household name",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                scope.launch {
                    api.createHousehold(name)
                    showCreate = false
                    reload()
                }
            },
        )
    }
}

@Composable
private fun HouseholdCard(household: HouseholdDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(household.name, style = MaterialTheme.typography.titleLarge)
            val evaluation = household.weekEvaluation
            if (evaluation == null) {
                Text("No budget set yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.height(12.dp))
                BudgetStatusBanner(evaluation)
            }
        }
    }
}
