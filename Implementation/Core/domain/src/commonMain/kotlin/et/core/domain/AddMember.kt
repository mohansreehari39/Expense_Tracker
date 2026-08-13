package et.core.domain

import et.core.model.Member

/**
 * Adds a household member, matched against existing members (including
 * archived ones — unlike the active-only [Repository.members] list) in
 * order of confidence, so "remove member, then rejoin" — the normal
 * recovery path after reinstalling/repairing a phone — reuses the same
 * id and un-archives it instead of always minting a fresh one. Without
 * this, a rejoin silently fragments that person's expense history across
 * two member ids that look like two different people, since old expenses
 * keep pointing at the now-archived original.
 *
 * Match precedence, strongest first:
 * 1. [deviceId] exactly matches an existing member — the same physical
 *    device/app-install joining again (e.g. a background re-sync, or a
 *    repeat join before a reinstall). Most reliable: nothing else can
 *    coincidentally share a deviceId.
 * 2. Same [displayName] (case-insensitive) AND at least one of
 *    [email]/[phone] also matches — the "reinstalled/repaired, so I have
 *    a new deviceId, but I'm recognizably still me" case this feature
 *    exists for. Requiring a contact-detail match too (not name alone)
 *    avoids silently merging two unrelated people who happen to share a
 *    display name (a real, if less likely, scenario for household
 *    members like "Unnati").
 * 3. Same [displayName] alone — kept as the last-resort fallback for
 *    members with no [email]/[phone] on file (e.g. added before those
 *    fields existed, or added manually with just a name from the Windows
 *    UI, never via a device join). Weaker, but strictly better than
 *    fragmenting every such member's history on every rejoin.
 *
 * On any match, [deviceId]/[email]/[phone] passed in *this* call take over
 * as the current value (falling back to whatever's already on file only
 * when this call didn't supply one) — so [deviceId] in particular
 * self-heals to whichever device most recently joined as this person,
 * rather than staying pinned to a now-uninstalled device forever after a
 * reinstall.
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
    ): Member {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "member name must not be blank" }

        val byDevice = deviceId?.let { repository.memberByDeviceId(householdId, it) }
        val byName = repository.memberByDisplayName(householdId, trimmed)
        val byNameAndContact = byName?.takeIf {
            (email != null && email == it.email) || (phone != null && phone == it.phone)
        }
        val existing = byDevice ?: byNameAndContact ?: byName

        if (existing != null) {
            val merged = existing.copy(
                isArchived = false,
                deviceId = deviceId ?: existing.deviceId,
                email = email ?: existing.email,
                phone = phone ?: existing.phone,
            )
            if (merged != existing) repository.saveMember(merged)
            return merged
        }

        val member = Member(id = idGenerator.newId(), householdId = householdId, displayName = trimmed, deviceId = deviceId, email = email, phone = phone)
        repository.saveMember(member)
        return member
    }
}
