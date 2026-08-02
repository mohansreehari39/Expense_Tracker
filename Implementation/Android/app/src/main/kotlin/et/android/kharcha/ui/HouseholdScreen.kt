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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.BudgetMath
import et.android.kharcha.data.DateRange
import et.android.kharcha.data.LocalRepository
import et.android.kharcha.data.RecordHouseholdSettlementRequest
import et.android.kharcha.data.SuggestedTransferDto
import et.android.kharcha.data.SyncEngine
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun occurredOn(occurredAt: Long): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun inRange(occurredAt: Long, range: DateRange): Boolean {
    val date = occurredOn(occurredAt)
    return !date.isBefore(range.start) && !date.isAfter(range.endInclusive)
}

@Composable
fun HouseholdScreen(repo: LocalRepository, householdId: String, myName: String, household: HouseholdEntity?) {
    val categories by repo.observeCategories(householdId).collectAsState(initial = emptyList())
    val members by repo.observeMembers(householdId).collectAsState(initial = emptyList())
    val expenses by repo.observeHouseholdExpenses(householdId).collectAsState(initial = emptyList())
    var weekIndex by remember { mutableStateOf(0) }
    var showAddExpense by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<HouseholdExpenseEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<HouseholdExpenseEntity?>(null) }
    var balances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var suggestedSettlements by remember { mutableStateOf<List<SuggestedTransferDto>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    LaunchedEffect(householdId) {
        repo.ensureMyMembership(householdId, myName)
    }

    val currentHousehold = household
    val currency = currentHousehold?.currency ?: "INR"
    val myMemberId = members.find { it.isMe }?.id

    suspend fun refreshBalances() {
        val household = currentHousehold
        if (household == null || !household.settlementEnabled) {
            balances = emptyMap()
            suggestedSettlements = emptyList()
            return
        }
        val pairedServerId = household.pairedServerId
        val remoteId = household.remoteId
        val response = if (pairedServerId != null && remoteId != null) {
            val server = repo.pairedServer(pairedServerId)
            val api = server?.let { runCatching { SyncEngine.resolveApiClient(context, it, repo) }.getOrNull() }
            api?.let { runCatching { it.household(remoteId) }.getOrNull() }
        } else {
            null
        }
        if (response != null) {
            // response.balances is keyed by remote member id (the server has no concept of our local ids) — remap to local ids so this map lines up with the same `members` list the unlinked fallback below keys against.
            val currentMembers = repo.members(householdId)
            balances = response.balances.mapNotNull { (remoteMemberId, money) ->
                val localId = currentMembers.find { it.remoteId == remoteMemberId }?.id ?: return@mapNotNull null
                localId to money.minorUnits
            }.toMap()
            suggestedSettlements = response.suggestedSettlements
        } else {
            // Unlinked household, or the server couldn't be reached — fall back to a local, settlement-blind fold.
            balances = repo.householdBalances(householdId)
            suggestedSettlements = emptyList()
        }
    }

    LaunchedEffect(householdId, members, expenses, currentHousehold?.settlementEnabled, currentHousehold?.pairedServerId) {
        refreshBalances()
    }

    val monthExpenses = expenses.filter { occurredOn(it.occurredAt).monthValue == today.monthValue && occurredOn(it.occurredAt).year == today.year }
    val monthEvaluation = currentHousehold?.defaultBudgetMinorUnits?.let {
        BudgetMath.evaluateBudget(it, monthExpenses.sumOf { e -> e.amountMinorUnits }, currency)
    }

    val weeks = remember(today) { BudgetMath.weeksInMonth(today.year, today.monthValue) }
    LaunchedEffect(weeks) {
        val currentIndex = weeks.indexOfFirst { !today.isBefore(it.start) && !today.isAfter(it.endInclusive) }
        weekIndex = if (currentIndex >= 0) currentIndex else 0
    }
    val currentWeek = weeks.getOrNull(weekIndex)
    val weekEvaluation = currentHousehold?.defaultBudgetMinorUnits?.let { budget ->
        currentWeek?.let { week ->
            val spentByWeek = weeks.map { w -> monthExpenses.filter { inRange(it.occurredAt, w) }.sumOf { it.amountMinorUnits } }
            val allocations = BudgetMath.rolloverAdjustedAllocations(budget, today.year, today.monthValue, weeks, spentByWeek, today)
            val allocated = allocations[weekIndex]
            val spent = spentByWeek[weekIndex]
            BudgetMath.evaluateBudget(allocated, spent, currency)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExpense = true }) { Icon(Icons.Filled.Add, contentDescription = "Add Expense") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(currentHousehold?.name ?: "Household", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        BudgetBar("This month", monthEvaluation)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (weekIndex > 0) weekIndex-- }, enabled = weekIndex > 0) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week")
                            }
                            BudgetBar(
                                currentWeek?.let { "Week of ${it.start}" } ?: "This week",
                                weekEvaluation,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { if (weekIndex < weeks.size - 1) weekIndex++ }, enabled = weekIndex < weeks.size - 1) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next week")
                            }
                        }
                    }
                }

                if (currentHousehold?.settlementEnabled == true) {
                    Spacer(Modifier.height(16.dp))
                    Text("Balances", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            members.forEach { member ->
                                val balance = balances[member.id]
                                val label = when {
                                    balance == null || balance == 0L -> "settled up"
                                    balance > 0 -> "is owed ${formatMoney(balance, currency)}"
                                    else -> "owes ${formatMoney(-balance, currency)}"
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(member.displayName)
                                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (suggestedSettlements.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Suggested Settlements", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                suggestedSettlements.forEach { s ->
                                    // s.fromParticipantId/toParticipantId are remote member ids (this list comes straight from the server's response) — never local ids.
                                    val fromName = members.find { it.remoteId == s.fromParticipantId }?.displayName ?: s.fromParticipantId
                                    val toName = members.find { it.remoteId == s.toParticipantId }?.displayName ?: s.toParticipantId
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("$fromName → $toName: ${formatMoney(s.amount)}", modifier = Modifier.weight(1f))
                                        TextButton(onClick = {
                                            scope.launch {
                                                val remoteId = currentHousehold?.remoteId
                                                val pairedServerId = currentHousehold?.pairedServerId
                                                if (remoteId != null && pairedServerId != null) {
                                                    val server = repo.pairedServer(pairedServerId)
                                                    val api = server?.let { runCatching { SyncEngine.resolveApiClient(context, it, repo) }.getOrNull() }
                                                    api?.let {
                                                        runCatching {
                                                            it.recordHouseholdSettlement(
                                                                remoteId,
                                                                RecordHouseholdSettlementRequest(s.fromParticipantId, s.toParticipantId, s.amount.minorUnits, s.amount.currency),
                                                            )
                                                        }
                                                    }
                                                }
                                                refreshBalances()
                                            }
                                        }) { Text("Settle") }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Recent Expenses", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (monthExpenses.isEmpty()) {
                    Text("No expenses recorded this month yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(monthExpenses.sortedByDescending { it.occurredAt }) { expense ->
                val categoryName = categories.find { it.id == expense.categoryId }?.name ?: expense.categoryId
                val paidByName = members.find { it.id == expense.paidByMemberId }?.displayName ?: expense.paidByMemberId
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(categoryName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Paid by $paidByName · ${formatExpenseDate(expense.occurredAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (expense.note.isNotBlank()) {
                                Text(expense.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatMoney(expense.amountMinorUnits, expense.currency), style = MaterialTheme.typography.titleMedium)
                            Row {
                                TextButton(onClick = { expenseToEdit = expense }) { Text("Edit") }
                                TextButton(onClick = { expenseToDelete = expense }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExpense) {
        AddHouseholdExpenseDialog(
            categories = categories,
            members = members,
            currency = currency,
            defaultMemberId = myMemberId,
            onCreateCategory = { name -> repo.addCategory(householdId, name) },
            onGetSubcategories = { categoryId -> repo.subcategories(categoryId) },
            onCreateSubcategory = { categoryId, name -> repo.addSubcategory(categoryId, name) },
            onDismiss = { showAddExpense = false },
            onSubmit = { categoryId, subcategoryId, amountMinorUnits, paidByMemberId, occurredAt, note ->
                scope.launch {
                    repo.recordHouseholdExpense(householdId, categoryId, subcategoryId, amountMinorUnits, currency, paidByMemberId, occurredAt, note)
                    showAddExpense = false
                }
            },
        )
    }

    expenseToEdit?.let { expense ->
        AddHouseholdExpenseDialog(
            categories = categories,
            members = members,
            currency = currency,
            defaultMemberId = myMemberId,
            onCreateCategory = { name -> repo.addCategory(householdId, name) },
            onGetSubcategories = { categoryId -> repo.subcategories(categoryId) },
            onCreateSubcategory = { categoryId, name -> repo.addSubcategory(categoryId, name) },
            expenseToEdit = expense,
            onDismiss = { expenseToEdit = null },
            onSubmit = { categoryId, subcategoryId, amountMinorUnits, paidByMemberId, occurredAt, note ->
                scope.launch {
                    repo.updateHouseholdExpense(
                        expense.copy(
                            categoryId = categoryId,
                            subcategoryId = subcategoryId,
                            amountMinorUnits = amountMinorUnits,
                            paidByMemberId = paidByMemberId,
                            occurredAt = occurredAt,
                            note = note,
                        ),
                    )
                    expenseToEdit = null
                }
            },
        )
    }

    expenseToDelete?.let { expense ->
        ConfirmDialog(
            title = "Delete expense?",
            message = "This removes the ${formatMoney(expense.amountMinorUnits, expense.currency)} expense and can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                scope.launch {
                    repo.deleteHouseholdExpense(expense)
                    expenseToDelete = null
                }
            },
        )
    }
}
