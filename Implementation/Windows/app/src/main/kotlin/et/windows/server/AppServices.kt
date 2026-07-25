package et.windows.server

import et.core.domain.AddCategory
import et.core.domain.AddMember
import et.core.domain.AddTripExpenseWithSplit
import et.core.domain.ArchiveCategory
import et.core.domain.CreateHousehold
import et.core.domain.CreateTrip
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

    val createHousehold = CreateHousehold(repository, idGenerator)
    val recordHouseholdExpense = RecordHouseholdExpense(repository, idGenerator)
    val setMonthlyBudget = SetMonthlyBudget(repository, idGenerator)
    val addCategory = AddCategory(repository, idGenerator)
    val archiveCategory = ArchiveCategory(repository)
    val addMember = AddMember(repository, idGenerator)

    val createTrip = CreateTrip(repository, idGenerator)
    val addTripExpenseWithSplit = AddTripExpenseWithSplit(repository, idGenerator)
}
