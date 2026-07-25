package et.core.domain

import et.core.model.Category

/**
 * Adds a category, but never creates a near-duplicate: names are matched
 * case-insensitively (trimmed) against the household's existing
 * categories, so e.g. "Eating out" reuses the existing "Eating Out"
 * instead of creating a second, near-identical category.
 */
class AddCategory(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(householdId: String, name: String): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "category name must not be blank" }

        val existing = repository.categories(householdId).find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val category = Category(id = idGenerator.newId(), householdId = householdId, name = trimmed, icon = "")
        repository.saveCategory(category)
        return category
    }
}
