package et.core.domain

import et.core.model.Subcategory

/**
 * Adds a subcategory under a category, but never creates a near-duplicate:
 * names are matched case-insensitively (trimmed) against the category's
 * existing subcategories, same dedup rule as [AddCategory].
 */
class AddSubcategory(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(categoryId: String, name: String): Subcategory {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "subcategory name must not be blank" }

        val existing = repository.subcategories(categoryId).find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val subcategory = Subcategory(id = idGenerator.newId(), categoryId = categoryId, name = trimmed)
        repository.saveSubcategory(subcategory)
        return subcategory
    }
}
