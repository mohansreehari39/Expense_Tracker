package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.BudgetEvaluation
import et.android.kharcha.data.BudgetMath
import et.android.kharcha.data.LocalRepository
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.ui.theme.Rose
import et.android.kharcha.ui.theme.Teal
import java.time.LocalDate

/**
 * Landing page — nothing is selected yet, so this shows each household's
 * monthly budget at a glance plus a rollup of what you're owed / you owe
 * across activities, rather than any single household/activity's detail.
 * Everything here is computed from local Room data (see LocalRepository) —
 * a household/activity that's never touched a server shows up the same way.
 */
@Composable
fun SummaryScreen(
    repo: LocalRepository,
    households: List<HouseholdEntity>,
    activities: List<ActivityEntity>,
    onOpenHousehold: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    var monthEvaluations by remember { mutableStateOf<Map<String, BudgetEvaluation?>>(emptyMap()) }
    var owedToYou by remember { mutableStateOf(0L) }
    var youOwe by remember { mutableStateOf(0L) }
    var owedCurrency by remember { mutableStateOf("INR") }
    var perActivityBalance by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    LaunchedEffect(households) {
        val today = LocalDate.now()
        monthEvaluations = households.associate { household ->
            val budget = household.defaultBudgetMinorUnits
            val evaluation = if (budget == null) {
                null
            } else {
                val spent = repo.householdExpenses(household.id)
                    .filter { expense ->
                        val date = java.time.Instant.ofEpochMilli(expense.occurredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        date.monthValue == today.monthValue && date.year == today.year
                    }
                    .sumOf { it.amountMinorUnits }
                BudgetMath.evaluateBudget(budget, spent, household.currency)
            }
            household.id to evaluation
        }
    }

    LaunchedEffect(activities) {
        var owed = 0L
        var owe = 0L
        val breakdown = mutableMapOf<String, Long>()
        for (activity in activities) {
            val myParticipant = repo.myParticipant(activity.id) ?: continue
            val balance = repo.activityBalances(activity.id)[myParticipant.id] ?: continue
            breakdown[activity.id] = balance
            owedCurrency = activity.currency
            if (balance > 0) owed += balance else owe += -balance
        }
        owedToYou = owed
        youOwe = owe
        perActivityBalance = breakdown
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Households", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        if (households.isEmpty()) {
            item {
                Text(
                    "No households yet — add one from the drawer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(households) { household ->
            Card(onClick = { onOpenHousehold(household.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(household.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    BudgetBar("This month", monthEvaluations[household.id])
                }
            }
        }

        if (owedToYou > 0 || youOwe > 0) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Activities", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("You're owed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatMoney(owedToYou, owedCurrency), color = Teal, style = MaterialTheme.typography.titleMedium)
                            }
                            Column {
                                Text("You owe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatMoney(youOwe, owedCurrency), color = Rose, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (activities.isEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "No activities yet — add one from the drawer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(activities) { activity ->
            Card(onClick = { onOpenActivity(activity.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(activity.name, style = MaterialTheme.typography.titleSmall)
                    val balance = perActivityBalance[activity.id]
                    val caption = when {
                        balance == null -> "Tap to set up who you are in this activity"
                        balance == 0L -> "You're settled up"
                        balance > 0 -> "You're owed ${formatMoney(balance, owedCurrency)}"
                        else -> "You owe ${formatMoney(-balance, owedCurrency)}"
                    }
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
