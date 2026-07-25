package et.core.domain

import et.core.model.Category

/**
 * Soft-deletes a category (sets [Category.isArchived]) rather than
 * physically removing it, so existing expenses that reference it by id
 * stay intact — it just stops showing up as a choice for new expenses.
 */
class ArchiveCategory(private val repository: Repository) {
    suspend operator fun invoke(categoryId: String): Category? {
        val category = repository.categoryById(categoryId) ?: return null
        val archived = category.copy(isArchived = true)
        repository.saveCategory(archived)
        return archived
    }
}
