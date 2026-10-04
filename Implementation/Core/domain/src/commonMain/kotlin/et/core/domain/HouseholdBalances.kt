package et.core.domain

import et.core.model.HouseholdExpense
import et.core.model.HouseholdExpenseBeneficiary
import et.core.model.HouseholdExpenseContribution
import et.core.model.HouseholdSettlement
import et.core.model.Money

/**
 * Opt-in per-member net balance for a household — same shape as
 * [TripBalances]. Only meaningful when a household has
 * `settlementEnabled` — see [et.core.model.Household.settlementEnabled].
 * Positive = this member is owed money overall; negative = they owe.
 *
 * Per expense, whoever chipped in ([contributions]) is credited what they
 * paid, and each member it was for ([beneficiaries]) is debited their
 * share. A dependent (pet/kid/parent) never pays, so a dependent's share
 * is itself split equally across [memberIds] — every member carries part
 * of it. An expense with no contribution rows credits its full amount to
 * [HouseholdExpense.paidByMemberId]; one with no beneficiary rows (only
 * possible for rows written before splits were always stored) is split
 * equally across [memberIds]. All equal splits go through
 * [SplitCalculator], so remainders land the same way everywhere.
 */
object HouseholdBalances {
    fun netBalances(
        memberIds: List<String>,
        expenses: List<HouseholdExpense>,
        beneficiaries: List<HouseholdExpenseBeneficiary>,
        contributions: List<HouseholdExpenseContribution>,
        settlements: List<HouseholdSettlement>,
        currency: String,
    ): Map<String, Money> {
        if (memberIds.isEmpty()) return emptyMap()
        val balance = memberIds.associateWith { 0L }.toMutableMap()
        fun add(id: String, minorUnits: Long) {
            balance[id] = balance.getOrDefault(id, 0L) + minorUnits
        }
        fun chargeEquallyToMembers(amount: Money) {
            SplitCalculator.computeSplits(amount, SplitMode.Equal(memberIds))
                .forEach { (id, share) -> add(id, -share.minorUnits) }
        }

        val beneficiariesByExpense = beneficiaries.groupBy { it.householdExpenseId }
        val contributionsByExpense = contributions.groupBy { it.householdExpenseId }
        for (expense in expenses) {
            val paid = contributionsByExpense[expense.id]
            if (paid.isNullOrEmpty()) {
                add(expense.paidByMemberId, expense.amount.minorUnits)
            } else {
                paid.forEach { add(it.memberId, it.amount.minorUnits) }
            }

            val owed = beneficiariesByExpense[expense.id]
            if (owed.isNullOrEmpty()) {
                chargeEquallyToMembers(expense.amount)
            } else {
                for (beneficiary in owed) {
                    val memberId = beneficiary.memberId
                    if (memberId != null) {
                        add(memberId, -beneficiary.amount.minorUnits)
                    } else {
                        chargeEquallyToMembers(beneficiary.amount)
                    }
                }
            }
        }

        // fromMemberId is the debtor paying cash, toMemberId the creditor
        // receiving it (SuggestedTransfer's debtor→creditor direction): both
        // balances move toward zero.
        for (settlement in settlements) {
            add(settlement.fromMemberId, settlement.amount.minorUnits)
            add(settlement.toMemberId, -settlement.amount.minorUnits)
        }
        return balance.mapValues { Money(it.value, currency) }
    }
}
