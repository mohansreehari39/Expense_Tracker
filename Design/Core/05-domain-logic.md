# Domain Logic (`core-domain`)

## Weekly budget derivation

```kotlin
fun weekAllocation(monthlyBudget: MonthlyBudget, week: DateRange): Long {
    val monthDays = daysInMonth(monthlyBudget.year, monthlyBudget.month)
    val overlapDays = week.overlapDaysWith(monthlyBudget.monthRange())
    return monthlyBudget.totalAmount * overlapDays / monthDays
}
```

Weeks are Mon–Sun (`kotlinx-datetime` `DayOfWeek.MONDAY` as anchor).
`overlapDaysWith` handles partial weeks at month boundaries as described in
[Arch/02-data-model.md](../../Arch/02-data-model.md#weekly-budget-is-derived-not-stored).

## Budget status evaluation

```kotlin
enum class BudgetStatus { OK, NEARING, OVER }

data class BudgetEvaluation(
    val status: BudgetStatus,
    val allocated: Long,
    val spent: Long,
    val remainingOrOver: Long, // negative when OVER
)

fun evaluateBudget(
    allocated: Long,
    spent: Long,
    nearingThreshold: Double = 0.8,
): BudgetEvaluation {
    val status = when {
        spent >= allocated -> BudgetStatus.OVER
        spent >= allocated * nearingThreshold -> BudgetStatus.NEARING
        else -> BudgetStatus.OK
    }
    return BudgetEvaluation(status, allocated, spent, allocated - spent)
}
```

Same function serves both household-weekly (`allocated` =
`weekAllocation(...)`) and trip-overall (`allocated` = `trip.budgetAmount`)
call sites — the caller decides what "allocated" and "spent" mean; the
threshold/highlight logic itself doesn't know or care which domain it's
evaluating, per [Arch/04-android-app-architecture.md](../../Arch/04-android-app-architecture.md#budget-alerting-weekly-for-household-overall-for-trips).
`nearingThreshold` defaults to 80% but is a parameter, not a constant, so a
future per-household setting doesn't require touching this function.

## Trip settlement (debt simplification)

Standard greedy algorithm, same one Splitwise uses conceptually:
1. Compute each participant's net balance = `sum(paid as payer across
   splits)` − `sum(owed across splits)` − `sum(settlements sent)` +
   `sum(settlements received)`.
2. Split participants into creditors (positive net) and debtors (negative
   net), each as a max-heap.
3. Repeatedly match the largest creditor with the largest debtor, settle
   the smaller of the two amounts, push remainder back onto the heap,
   until all balances are ~0.
4. Output: a minimal list of suggested `{from, to, amount}` transfers — a
   *suggestion* for the settle-up screen, not a `Settlement` record itself
   (those are only created once a member confirms a real payment happened).

## Split validation

`AddTripExpenseWithSplit` use case validates, before producing an
`Operation`:
- Equal split: divides evenly, remainder (from integer division) assigned
  to the first participant deterministically (avoids fractional-currency
  drift).
- Exact-amount split: sum of `share_amount` must equal the expense total
  exactly, or the use case rejects it.
- Percentage split: sum of `share_percent` must equal 100 (within a small
  epsilon), converted to amounts at save time, same remainder rule as
  equal split.
- Weighted split: amounts computed proportional to `share_weight`, same
  remainder rule.
