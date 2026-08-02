package et.core.domain

import et.core.model.Subcategory

/**
 * Soft-deletes a subcategory (sets [Subcategory.isArchived]) rather than
 * physically removing it, so existing expenses that reference it by id
 * stay intact — it just stops showing up as a choice for new expenses.
 */
class ArchiveSubcategory(private val repository: Repository) {
    suspend operator fun invoke(subcategoryId: String): Subcategory? {
        val subcategory = repository.subcategoryById(subcategoryId) ?: return null
        val archived = subcategory.copy(isArchived = true)
        repository.saveSubcategory(archived)
        return archived
    }
}
