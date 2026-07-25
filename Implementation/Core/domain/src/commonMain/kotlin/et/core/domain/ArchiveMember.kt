package et.core.domain

import et.core.model.Member

/**
 * Soft-deletes a member (sets [Member.isArchived]) rather than physically
 * removing it, so existing expenses that reference it by id stay intact —
 * it just stops showing up as a choice for new expenses.
 */
class ArchiveMember(private val repository: Repository) {
    suspend operator fun invoke(memberId: String): Member? {
        val member = repository.memberById(memberId) ?: return null
        val archived = member.copy(isArchived = true)
        repository.saveMember(archived)
        return archived
    }
}
