package et.core.domain

import et.core.model.HouseholdSettlement
import et.core.model.Money

/**
 * Records a real settle-up payment between two household members. Does not
 * move any money itself — this is a bookkeeping confirmation that a payment
 * suggested by [DebtSimplification] (or any other amount the members agree
 * on) actually happened. The household equivalent of [SettleUp].
 */
class RecordHouseholdSettlement(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        householdId: String,
        fromMemberId: String,
        toMemberId: String,
        amount: Money,
        settledAt: Long,
        note: String = "",
    ): HouseholdSettlement {
        val settlement = HouseholdSettlement(
            id = idGenerator.newId(),
            householdId = householdId,
            fromMemberId = fromMemberId,
            toMemberId = toMemberId,
            amount = amount,
            settledAt = settledAt,
            note = note,
        )
        repository.saveHouseholdSettlement(settlement)
        return settlement
    }
}
