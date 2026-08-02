package et.windows.server

import et.core.domain.AddCategory
import et.core.domain.AddMember
import et.core.domain.AddSubcategory
import et.core.domain.AddTripExpenseWithSplit
import et.core.domain.AddTripParticipant
import et.core.domain.ArchiveCategory
import et.core.domain.ArchiveMember
import et.core.domain.ArchiveSubcategory
import et.core.domain.ArchiveTripParticipant
import et.core.domain.CreateHousehold
import et.core.domain.CreateTrip
import et.core.domain.EditHouseholdExpense
import et.core.domain.EditTripExpenseWithSplit
import et.core.domain.IdGenerator
import et.core.domain.RecordHouseholdExpense
import et.core.domain.RecordHouseholdSettlement
import et.core.domain.Repository
import et.core.domain.SetMonthlyBudget
import et.windows.db.PairedDeviceStore
import java.util.UUID

/** Everything a route handler needs — wired once in [et.windows.Main]. */
class AppServices(
    val repository: Repository,
    val deviceId: String,
    val pairedDevices: PairedDeviceStore,
) {
    private val idGenerator = IdGenerator { UUID.randomUUID().toString() }

    val createHousehold = CreateHousehold(repository, idGenerator)
    val recordHouseholdExpense = RecordHouseholdExpense(repository, idGenerator)
    val editHouseholdExpense = EditHouseholdExpense(repository)
    val setMonthlyBudget = SetMonthlyBudget(repository, idGenerator)
    val addCategory = AddCategory(repository, idGenerator)
    val archiveCategory = ArchiveCategory(repository)
    val addSubcategory = AddSubcategory(repository, idGenerator)
    val archiveSubcategory = ArchiveSubcategory(repository)
    val addMember = AddMember(repository, idGenerator)
    val archiveMember = ArchiveMember(repository)
    val recordHouseholdSettlement = RecordHouseholdSettlement(repository, idGenerator)

    val createTrip = CreateTrip(repository, idGenerator)
    val addTripExpenseWithSplit = AddTripExpenseWithSplit(repository, idGenerator)
    val editTripExpenseWithSplit = EditTripExpenseWithSplit(repository, idGenerator)
    val addTripParticipant = AddTripParticipant(repository, idGenerator)
    val archiveTripParticipant = ArchiveTripParticipant(repository)
}
