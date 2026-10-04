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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.contains
import et.android.kharcha.data.LocalRepository
import et.android.kharcha.data.SyncEngine
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.core.domain.DateRange
import et.core.domain.WeeklyBudget
import et.core.domain.evaluateBudget
import et.core.model.Money
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import et.android.kharcha.ui.theme.kharcha
import androidx.compose.material.icons.outlined.Home
import et.core.domain.SuggestedTransfer

private fun occurredOn(occurredAt: Long): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun inRange(occurredAt: Long, range: DateRange): Boolean = occurredOn(occurredAt) in range

@Composable
fun HouseholdScreen(
    repo: LocalRepository,
    householdId: String,
    myName: String,
    household: HouseholdEntity?,
    chrome: SpaceChrome,
    addRequested: Boolean,
    onAddHandled: () -> Unit,
) {
    val categories by repo.observeCategories(householdId).collectAsState(initial = emptyList())
    val members by repo.observeMembers(householdId).collectAsState(initial = emptyList())
    val dependents by repo.observeDependents(householdId).collectAsState(initial = emptyList())
    val expenses by repo.observeHouseholdExpenses(householdId).collectAsState(initial = emptyList())
    var weekIndex by remember { mutableStateOf(0) }
    var showAddExpense by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<HouseholdExpenseEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<HouseholdExpenseEntity?>(null) }
    var balances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var suggestedSettlements by remember { mutableStateOf<List<SuggestedTransfer>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    LaunchedEffect(householdId) {
        repo.ensureMyMembership(householdId, myName)
    }

    // The bottom bar's + button.
    LaunchedEffect(addRequested) {
        if (addRequested) {
            showAddExpense = true
            onAddHandled()
        }
    }

    val currentHousehold = household
    val currency = currentHousehold?.currency ?: "INR"
    val myMemberId = members.find { it.isMe }?.id

    // Recorded payments; refreshes balances when one is added here or arrives by sync.
    val settlements by repo.observeHouseholdSettlements(householdId).collectAsState(initial = emptyList())
    // The Windows app can override the budget for a single month; that wins over the default.
    val monthOverride by repo.observeMonthBudgetOverride(householdId, today.year, today.monthValue).collectAsState(initial = null)
    val monthBudget = monthOverride?.totalMinorUnits ?: currentHousehold?.defaultBudgetMinorUnits

    suspend fun refreshBalances() {
        val sheet = if (currentHousehold?.settlementEnabled == true) repo.householdBalanceSheet(householdId) else null
        balances = sheet?.balances ?: emptyMap()
        suggestedSettlements = sheet?.suggestions ?: emptyList()
    }

    var settleTarget by remember { mutableStateOf<SuggestedTransfer?>(null) }

    settleTarget?.let { s ->
        SettleDialog(
            fromName = members.find { it.id == s.fromParticipantId }?.displayName ?: "?",
            toName = members.find { it.id == s.toParticipantId }?.displayName ?: "?",
            owedMinorUnits = s.amount.minorUnits,
            currency = s.amount.currency,
            onDismiss = { settleTarget = null },
            onConfirm = { amountMinorUnits ->
                settleTarget = null
                // Recorded on this phone (works offline) and synced like any other change.
                scope.launch { repo.recordHouseholdSettlement(householdId, s.fromParticipantId, s.toParticipantId, amountMinorUnits, s.amount.currency) }
            },
        )
    }

    LaunchedEffect(householdId, members, expenses, settlements, currentHousehold?.settlementEnabled) {
        refreshBalances()
    }

    val monthExpenses = expenses.filter { occurredOn(it.occurredAt).monthValue == today.monthValue && occurredOn(it.occurredAt).year == today.year }
    val monthEvaluation = monthBudget?.let {
        evaluateBudget(Money(it, currency), Money(monthExpenses.sumOf { e -> e.amountMinorUnits }, currency))
    }

    val weeks = remember(today) { WeeklyBudget.weeksInMonth(today.year, today.monthValue) }
    LaunchedEffect(weeks) {
        val currentIndex = weeks.indexOfFirst { today in it }
        weekIndex = if (currentIndex >= 0) currentIndex else 0
    }
    // Every week's figures (rollover applied), so each chip can show what's left in it.
    val weekEvaluations = monthBudget?.let { budget ->
        val spentByWeek = weeks.map { w -> monthExpenses.filter { inRange(it.occurredAt, w) }.sumOf { it.amountMinorUnits } }
        val allocations = WeeklyBudget.rolloverAdjustedAllocations(
            Money(budget, currency),
            today.year,
            today.monthValue,
            weeks,
            spentByWeek.map { Money(it, currency) },
            today.toKotlinLocalDate(),
        )
        weeks.indices.map { i -> evaluateBudget(allocations[i], Money(spentByWeek[i], currency)) }
    }
    val weekEvaluation = weekEvaluations?.getOrNull(weekIndex)
    val weekRanges = weeks.map { "${it.start.dayOfMonth}–${it.endInclusive.dayOfMonth}" }
    val monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SpaceTopBar(
                name = currentHousehold?.name ?: "Household",
                icon = Icons.Outlined.Home,
                myName = chrome.myName,
                onSwitchSpace = chrome.onSwitchSpace,
                onSettings = chrome.onSettings,
                onOpenMe = chrome.onOpenMe,
            )
        }
        item { BudgetHero("Left this month · $monthName", monthEvaluation) }

        if (weekEvaluations != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeekChips(
                        ranges = weekRanges,
                        amounts = weekEvaluations.map { compactMoney(it.allocated.minorUnits - it.spent.minorUnits, currency) },
                        selected = weekIndex,
                        onSelect = { weekIndex = it },
                    )
                    weekEvaluation?.let { week ->
                        val left = week.allocated.minorUnits - week.spent.minorUnits
                        Text(
                            if (left >= 0) {
                                "Days ${weekRanges[weekIndex]}: ${formatMoney(left, currency)} left of ${formatMoney(week.allocated.minorUnits, currency)}"
                            } else {
                                "Days ${weekRanges[weekIndex]}: over by ${formatMoney(-left, currency)}"
                            },
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = if (left >= 0) kharcha.muted else kharcha.over,
                        )
                        ProgressTrack(
                            if (week.allocated.minorUnits <= 0) 1f else week.spent.minorUnits.toFloat() / week.allocated.minorUnits,
                            statusColor(week.status),
                            height = 6.dp,
                        )
                    }
                }
            }
        }

        if (currentHousehold?.settlementEnabled == true) {
            item {
                SectionCard(title = "Balances", trailing = "settlement on") {
                    members.forEach { member ->
                        val balance = balances[member.id]
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(member.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when {
                                    balance == null || balance == 0L -> "settled up"
                                    balance > 0 -> "is owed ${formatMoney(balance, currency)}"
                                    else -> "owes ${formatMoney(-balance, currency)}"
                                },
                                style = MaterialTheme.typography.bodyMedium.tabular(),
                                color = when {
                                    balance == null || balance == 0L -> kharcha.muted
                                    balance > 0 -> kharcha.ok
                                    else -> kharcha.warn
                                },
                            )
                        }
                    }
                    suggestedSettlements.forEach { s ->
                        val fromName = members.find { it.id == s.fromParticipantId }?.displayName ?: "?"
                        val toName = members.find { it.id == s.toParticipantId }?.displayName ?: "?"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("$fromName pays $toName ${formatMoney(s.amount.minorUnits, s.amount.currency)}", style = MaterialTheme.typography.bodyMedium.tabular(), modifier = Modifier.weight(1f))
                            TonalPill("Settle", onClick = { settleTarget = s })
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "This month", trailing = "${monthExpenses.size} ${if (monthExpenses.size == 1) "expense" else "expenses"}") {
                if (monthExpenses.isEmpty()) {
                    Text("No expenses yet this month. Tap + to add one.", style = MaterialTheme.typography.bodyMedium, color = kharcha.muted)
                }
                monthExpenses.sortedByDescending { it.occurredAt }.forEach { expense ->
                    val categoryName = categories.find { it.id == expense.categoryId }?.name ?: "Expense"
                    val paidByName = members.find { it.id == expense.paidByMemberId }?.displayName ?: "someone"
                    ListRow(
                        icon = categoryIcon(categoryName),
                        title = categoryName,
                        subtitle = listOf("${formatExpenseDate(expense.occurredAt)} · Paid by $paidByName", expense.note).filter { it.isNotBlank() }.joinToString("\n"),
                        trailing = formatMoney(expense.amountMinorUnits, expense.currency),
                        menu = listOf("Edit" to { expenseToEdit = expense }, "Delete" to { expenseToDelete = expense }),
                    )
                }
            }
        }
    }

    if (showAddExpense) {
        AddHouseholdExpenseDialog(
            categories = categories,
            members = members,
            dependents = dependents,
            currency = currency,
            defaultMemberId = myMemberId,
            onCreateCategory = { name -> repo.addCategory(householdId, name) },
            onGetSubcategories = { categoryId -> repo.subcategories(categoryId) },
            onCreateSubcategory = { categoryId, name -> repo.addSubcategory(categoryId, name) },
            onDismiss = { showAddExpense = false },
            onSubmit = { categoryId, subcategoryId, amountMinorUnits, paidByMemberId, occurredAt, note, beneficiaries, contributions ->
                scope.launch {
                    repo.recordHouseholdExpense(
                        householdId, categoryId, subcategoryId, amountMinorUnits, currency, paidByMemberId, occurredAt, note,
                        beneficiaries, contributions,
                    )
                    showAddExpense = false
                }
            },
        )
    }

    expenseToEdit?.let { expense ->
        // The saved "who's it for" / "who chipped in" split, so editing re-opens it instead of the default.
        val savedSplits by produceState<Pair<Map<String, Long>, Map<String, Long>>?>(null, expense.id) {
            value = repo.householdExpenseBeneficiaries(expense.id).associate { (it.memberId ?: it.dependentId!!) to it.amountMinorUnits } to repo.householdExpenseContributions(expense.id).associate { it.memberId to it.amountMinorUnits }
        }
        AddHouseholdExpenseDialog(
            categories = categories,
            members = members,
            dependents = dependents,
            currency = currency,
            defaultMemberId = myMemberId,
            onCreateCategory = { name -> repo.addCategory(householdId, name) },
            onGetSubcategories = { categoryId -> repo.subcategories(categoryId) },
            onCreateSubcategory = { categoryId, name -> repo.addSubcategory(categoryId, name) },
            expenseToEdit = expense,
            savedBeneficiaries = savedSplits?.first?.takeIf { it.isNotEmpty() },
            savedContributions = savedSplits?.second?.takeIf { it.isNotEmpty() },
            onDismiss = { expenseToEdit = null },
            onSubmit = { categoryId, subcategoryId, amountMinorUnits, paidByMemberId, occurredAt, note, beneficiaries, contributions ->
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
                        beneficiaries,
                        contributions,
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
