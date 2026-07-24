package et.core.domain

import et.core.model.Money
import et.core.model.Settlement

/**
 * Records a real settle-up payment. Does not move any money itself — this
 * is a bookkeeping confirmation that a payment suggested by
 * [DebtSimplification] (or any other amount the members agree on) actually
 * happened.
 */
class SettleUp(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        tripId: String,
        fromParticipantId: String,
        toParticipantId: String,
        amount: Money,
        settledAt: Long,
        note: String = "",
    ): Settlement {
        val settlement = Settlement(
            id = idGenerator.newId(),
            tripId = tripId,
            fromParticipantId = fromParticipantId,
            toParticipantId = toParticipantId,
            amount = amount,
            settledAt = settledAt,
            note = note,
        )
        repository.saveSettlement(settlement)
        return settlement
    }
}
