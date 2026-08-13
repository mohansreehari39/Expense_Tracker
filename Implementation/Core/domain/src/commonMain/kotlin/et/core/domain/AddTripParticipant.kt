package et.core.domain

import et.core.model.TripParticipant

/**
 * Adds a trip participant by name, deduplicated the same way as
 * [AddMember]/[AddCategory] — reuses an existing participant with the
 * same (trimmed, case-insensitive) name instead of creating a
 * near-duplicate. The match includes archived participants and
 * un-archives on a hit (see [AddMember]'s doc for why: without this, a
 * reinstalled/repaired phone rejoining an activity fragments that
 * person's expense history across two ids).
 */
class AddTripParticipant(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(tripId: String, displayName: String): TripParticipant {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "participant name must not be blank" }

        val existing = repository.tripParticipantByDisplayName(tripId, trimmed)
        if (existing != null) {
            if (!existing.isArchived) return existing
            val reactivated = existing.copy(isArchived = false)
            repository.saveTripParticipant(reactivated)
            return reactivated
        }

        val participant = TripParticipant(id = idGenerator.newId(), tripId = tripId, displayName = trimmed)
        repository.saveTripParticipant(participant)
        return participant
    }
}
