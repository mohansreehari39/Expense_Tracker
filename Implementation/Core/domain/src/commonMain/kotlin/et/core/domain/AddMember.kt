package et.core.domain

import et.core.model.Member

/**
 * Adds a household member by name, deduplicated the same way as
 * [AddCategory] — the (not-yet-built) QR device-pairing flow will later
 * fill in [Member.deviceId] for a member added this way.
 */
class AddMember(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(householdId: String, displayName: String): Member {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "member name must not be blank" }

        val existing = repository.members(householdId).find { it.displayName.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val member = Member(id = idGenerator.newId(), householdId = householdId, displayName = trimmed)
        repository.saveMember(member)
        return member
    }
}
