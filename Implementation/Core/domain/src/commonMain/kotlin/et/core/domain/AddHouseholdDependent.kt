package et.core.domain

import et.core.model.DependentCategory
import et.core.model.HouseholdDependent

/**
 * Adds a household dependent (pet/kid/parent), but never creates a
 * near-duplicate: names are matched case-insensitively (trimmed) against
 * the household's existing dependents *of the same category*, same dedup
 * rule as [AddCategory].
 */
class AddHouseholdDependent(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(householdId: String, name: String, category: DependentCategory): HouseholdDependent {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "dependent name must not be blank" }

        val existing = repository.householdDependents(householdId)
            .find { it.category == category && it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val dependent = HouseholdDependent(id = idGenerator.newId(), householdId = householdId, name = trimmed, category = category)
        repository.saveHouseholdDependent(dependent)
        return dependent
    }
}
