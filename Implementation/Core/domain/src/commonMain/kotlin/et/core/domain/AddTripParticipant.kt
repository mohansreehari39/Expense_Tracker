package et.core.domain

import et.core.model.TripParticipant

/**
 * Adds a trip participant, reusing an existing one instead of creating a
 * near-duplicate — matched, strongest first, by the joining phone's
 * [deviceId] (so a person who renamed themselves is still found), then by
 * (trimmed, case-insensitive) name. The match includes archived
 * participants and un-archives on a hit (see [AddMember]'s doc for why:
 * without this, a reinstalled/repaired phone rejoining an activity
 * fragments that person's expense history across two ids). A supplied
 * [deviceId] is recorded on the match, so it self-heals to whichever
 * phone most recently joined as this person.
 */
class AddTripParticipant(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(tripId: String, displayName: String, deviceId: String? = null): TripParticipant {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "participant name must not be blank" }

        val existing = deviceId?.let { repository.tripParticipantByDeviceId(tripId, it) }
            ?: repository.tripParticipantByDisplayName(tripId, trimmed)
        if (existing != null) {
            val merged = existing.copy(isArchived = false, deviceId = deviceId ?: existing.deviceId)
            if (merged != existing) repository.saveTripParticipant(merged)
            return merged
        }

        val participant = TripParticipant(id = idGenerator.newId(), tripId = tripId, displayName = trimmed, deviceId = deviceId)
        repository.saveTripParticipant(participant)
        return participant
    }
}
