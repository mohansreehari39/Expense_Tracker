package et.windows.server

import et.core.domain.IdGenerator
import et.core.domain.RecordHouseholdExpense
import et.core.domain.Repository
import et.core.domain.SetMonthlyBudget
import java.util.UUID

/** Everything a route handler needs — wired once in [et.windows.Main]. */
class AppServices(
    val repository: Repository,
    val deviceId: String,
) {
    private val idGenerator = IdGenerator { UUID.randomUUID().toString() }

    val recordHouseholdExpense = RecordHouseholdExpense(repository, idGenerator)
    val setMonthlyBudget = SetMonthlyBudget(repository, idGenerator)
}
