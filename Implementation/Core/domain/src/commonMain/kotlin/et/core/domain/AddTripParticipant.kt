package et.core.domain

import et.core.model.TripParticipant

/**
 * Adds a trip participant by name, deduplicated the same way as
 * [AddMember]/[AddCategory] — reuses an existing active participant with
 * the same (trimmed, case-insensitive) name instead of creating a
 * near-duplicate.
 */
class AddTripParticipant(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(tripId: String, displayName: String): TripParticipant {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "participant name must not be blank" }

        val existing = repository.tripParticipants(tripId).find { it.displayName.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val participant = TripParticipant(id = idGenerator.newId(), tripId = tripId, displayName = trimmed)
        repository.saveTripParticipant(participant)
        return participant
    }
}
