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
import androidx.compose.ui.platform.LocalContext
import et.android.kharcha.data.LocalRepository
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.ui.theme.Rose
import et.android.kharcha.ui.theme.Teal
import et.android.kharcha.data.BalanceLoader
import et.android.kharcha.data.BalanceView
import et.core.domain.BudgetEvaluation
import et.core.domain.evaluateBudget
import et.core.model.Money
import java.time.LocalDate
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import et.android.kharcha.ui.theme.kharcha
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color

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
    myName: String,
    onOpenHousehold: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenMe: () -> Unit,
) {
    var monthEvaluations by remember { mutableStateOf<Map<String, BudgetEvaluation?>>(emptyMap()) }
    var owedToYou by remember { mutableStateOf(0L) }
    var youOwe by remember { mutableStateOf(0L) }
    var owedCurrency by remember { mutableStateOf("INR") }
    var perActivityBalance by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val context = LocalContext.current

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
                evaluateBudget(Money(budget, household.currency), Money(spent, household.currency))
            }
            household.id to evaluation
        }
    }

    // Same BalanceLoader as the activity screen, so the two can never show
    // different dues for the same activity. Local figures go up first (instant),
    // then get replaced by the server's settlement-aware ones once it answers.
    LaunchedEffect(activities) {
        val loader = BalanceLoader(context, repo)
        suspend fun publish(viewFor: suspend (ActivityEntity) -> BalanceView) {
            var owed = 0L
            var owe = 0L
            val breakdown = mutableMapOf<String, Long>()
            for (activity in activities) {
                val myParticipant = repo.myParticipant(activity.id) ?: continue
                val balance = viewFor(activity).balances[myParticipant.id] ?: continue
                breakdown[activity.id] = balance
                owedCurrency = activity.currency
                if (balance > 0) owed += balance else owe += -balance
            }
            owedToYou = owed
            youOwe = owe
            perActivityBalance = breakdown
        }
        publish { loader.localForActivity(it) }
        if (activities.any { it.pairedServerId != null }) publish { loader.forActivity(it) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitleBar("Summary", myName = myName, onOpenMe = onOpenMe) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OwedTile("You're owed", formatMoney(owedToYou, owedCurrency), kharcha.ok, Modifier.weight(1f))
                OwedTile("You owe", formatMoney(youOwe, owedCurrency), kharcha.warn, Modifier.weight(1f))
            }
        }

        item { SectionLabel("Households") }
        if (households.isEmpty()) {
            item { Text("No households yet. Open Spaces to create or join one.", style = MaterialTheme.typography.bodyMedium, color = kharcha.muted) }
        }
        items(households) { household ->
            Surface(onClick = { onOpenHousehold(household.id) }, shape = MaterialTheme.shapes.large, color = Color.Transparent) {
                SectionCard(title = household.name, trailing = "This month") {
                    val evaluation = monthEvaluations[household.id]
                    if (evaluation == null) {
                        Text("No monthly budget set", style = MaterialTheme.typography.bodySmall, color = kharcha.muted)
                    } else {
                        val left = evaluation.allocated.minorUnits - evaluation.spent.minorUnits
                        Text(
                            if (left >= 0) "${formatMoney(left, household.currency)} left of ${formatMoney(evaluation.allocated.minorUnits, household.currency)}"
                            else "Over by ${formatMoney(-left, household.currency)}",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = if (left >= 0) MaterialTheme.colorScheme.onSurface else kharcha.over,
                        )
                        ProgressTrack(
                            if (evaluation.allocated.minorUnits <= 0) 1f else evaluation.spent.minorUnits.toFloat() / evaluation.allocated.minorUnits,
                            statusColor(evaluation.status),
                            height = 6.dp,
                        )
                    }
                }
            }
        }

        item { SectionLabel("Activities") }
        if (activities.isEmpty()) {
            item { Text("No activities yet. Trips and events go here.", style = MaterialTheme.typography.bodyMedium, color = kharcha.muted) }
        }
        item {
            if (activities.isNotEmpty()) {
                SectionCard {
                    activities.forEachIndexed { index, activity ->
                        val balance = perActivityBalance[activity.id]
                        Surface(onClick = { onOpenActivity(activity.id) }, color = Color.Transparent, shape = MaterialTheme.shapes.medium) {
                            ListRow(
                                icon = Icons.Outlined.Luggage,
                                title = activity.name,
                                subtitle = when {
                                    balance == null -> "Open it to set up who you are"
                                    balance == 0L -> "You're settled up"
                                    balance > 0 -> "You're owed ${formatMoney(balance, owedCurrency)}"
                                    else -> "You owe ${formatMoney(-balance, owedCurrency)}"
                                },
                                tileBackground = activityTileColor(index),
                                tileTint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwedTile(label: String, amount: String, color: Color, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = kharcha.muted)
            Text(amount, style = MaterialTheme.typography.titleLarge.tabular(), color = color)
        }
    }
}
