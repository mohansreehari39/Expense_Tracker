package et.core.domain

import et.core.model.StableIds
import et.core.model.Subcategory

/**
 * Adds a subcategory under a category, but never creates a near-duplicate:
 * names are matched case-insensitively (trimmed) against the category's
 * existing subcategories, same dedup rule as [AddCategory].
 * Its id comes from the category and name ([StableIds.subcategory]) (S3).
 */
class AddSubcategory(
    private val repository: Repository,
) {
    suspend operator fun invoke(categoryId: String, name: String): Subcategory {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "subcategory name must not be blank" }

        val existing = repository.subcategories(categoryId).find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val subcategory = Subcategory(id = StableIds.subcategory(categoryId, trimmed), categoryId = categoryId, name = trimmed)
        repository.saveSubcategory(subcategory)
        return subcategory
    }
}
