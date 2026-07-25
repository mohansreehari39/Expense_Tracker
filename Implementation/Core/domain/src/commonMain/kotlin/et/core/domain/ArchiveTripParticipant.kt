package et.core.domain

import et.core.model.TripParticipant

/**
 * Soft-deletes a trip participant (sets [TripParticipant.isArchived])
 * rather than physically removing it, so existing expenses/splits/
 * settlements that reference it by id stay intact.
 */
class ArchiveTripParticipant(private val repository: Repository) {
    suspend operator fun invoke(participantId: String): TripParticipant? {
        val participant = repository.tripParticipantById(participantId) ?: return null
        val archived = participant.copy(isArchived = true)
        repository.saveTripParticipant(archived)
        return archived
    }
}
