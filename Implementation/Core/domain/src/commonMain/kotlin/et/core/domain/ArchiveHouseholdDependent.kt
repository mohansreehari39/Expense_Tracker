package et.core.domain

import et.core.model.HouseholdDependent

/** Soft-deletes a household dependent, mirroring [ArchiveCategory]. */
class ArchiveHouseholdDependent(private val repository: Repository) {
    suspend operator fun invoke(dependentId: String): HouseholdDependent? {
        val dependent = repository.householdDependentById(dependentId) ?: return null
        val archived = dependent.copy(isArchived = true)
        repository.saveHouseholdDependent(archived)
        return archived
    }
}
