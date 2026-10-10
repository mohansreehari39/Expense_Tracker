package et.core.domain

import et.core.model.StableIds
import et.core.model.TripParticipant

/**
 * Adds an activity participant, or finds the one already there — the same
 * matching as [AddMember] (see its doc): by the joining phone's
 * [deviceId], else by name + age + email + mobile all matching (through the
 * derived id, [StableIds.person]). A name alone never matches; guests
 * added by name only are never merged automatically. Archived participants
 * are found too and un-archived.
 */
class AddTripParticipant(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        tripId: String,
        displayName: String,
        deviceId: String? = null,
        age: Int? = null,
        email: String? = null,
        phone: String? = null,
    ): TripParticipant {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "participant name must not be blank" }

        val stableId = StableIds.person(tripId, trimmed, age, email, phone)
        val existing = deviceId?.let { repository.tripParticipantByDeviceId(tripId, it) }
            ?: stableId?.let { repository.tripParticipantById(it) }
        if (existing != null) {
            val merged = existing.copy(
                isArchived = false,
                deviceId = deviceId ?: existing.deviceId,
                age = age ?: existing.age,
                email = email ?: existing.email,
                phone = phone ?: existing.phone,
            )
            if (merged != existing) repository.saveTripParticipant(merged)
            return merged
        }

        val participant = TripParticipant(
            id = stableId ?: idGenerator.newId(),
            tripId = tripId,
            displayName = trimmed,
            deviceId = deviceId,
            age = age,
            email = email,
            phone = phone,
        )
        repository.saveTripParticipant(participant)
        return participant
    }
}
