package et.core.domain

import et.core.model.DependentCategory
import et.core.model.HouseholdDependent
import et.core.model.StableIds

/**
 * Adds a household dependent (pet/kid/parent), but never creates a
 * near-duplicate: names are matched case-insensitively (trimmed) against
 * the household's existing dependents *of the same category*, same dedup
 * rule as [AddCategory]. Its id comes from the household, category and
 * name ([StableIds.dependent]) (S3).
 */
class AddHouseholdDependent(
    private val repository: Repository,
) {
    suspend operator fun invoke(householdId: String, name: String, category: DependentCategory): HouseholdDependent {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "dependent name must not be blank" }

        val existing = repository.householdDependents(householdId)
            .find { it.category == category && it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val dependent = HouseholdDependent(id = StableIds.dependent(householdId, category.name, trimmed), householdId = householdId, name = trimmed, category = category)
        repository.saveHouseholdDependent(dependent)
        return dependent
    }
}
