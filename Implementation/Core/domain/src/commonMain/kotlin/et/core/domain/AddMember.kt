package et.core.domain

import et.core.model.Member
import et.core.model.StableIds

/**
 * Adds a household member, or finds the one already there — archived ones
 * included, so "remove member, then rejoin" (the normal recovery after
 * reinstalling a phone) reuses the same id and un-archives it instead of
 * splitting that person's expense history across two ids.
 *
 * Matching (Test/Sync/corner-cases.md, S4), strongest first:
 * 1. [deviceId] matches — the same app install joining again.
 * 2. Name + age + email + mobile all match — the same person on a new
 *    phone. With all four known, the member's id is derived from them
 *    ([StableIds.person]), so this is an id lookup, and two phones that
 *    add the same person offline also end up with one member.
 *
 * A name alone never matches: two different people can share a name.
 * Someone added with any of the four missing (e.g. typed in by name on
 * Windows) gets a random id and is never merged automatically.
 *
 * On a match, details passed in *this* call replace the ones on file
 * (keeping those on file where this call has none), so [deviceId] follows
 * whichever phone most recently joined as this person.
 */
class AddMember(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        householdId: String,
        displayName: String,
        deviceId: String? = null,
        email: String? = null,
        phone: String? = null,
        age: Int? = null,
    ): Member {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "member name must not be blank" }

        val stableId = StableIds.person(householdId, trimmed, age, email, phone)
        val existing = deviceId?.let { repository.memberByDeviceId(householdId, it) }
            ?: stableId?.let { repository.memberById(it) }

        if (existing != null) {
            val merged = existing.copy(
                isArchived = false,
                deviceId = deviceId ?: existing.deviceId,
                email = email ?: existing.email,
                phone = phone ?: existing.phone,
                age = age ?: existing.age,
            )
            if (merged != existing) repository.saveMember(merged)
            return merged
        }

        val member = Member(
            id = stableId ?: idGenerator.newId(),
            householdId = householdId,
            displayName = trimmed,
            deviceId = deviceId,
            email = email,
            phone = phone,
            age = age,
        )
        repository.saveMember(member)
        return member
    }
}
