package et.core.domain

import et.core.model.Money
import et.core.model.Trip
import et.core.model.TripParticipant

class CreateTrip(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        name: String,
        startDate: Long,
        endDate: Long?,
        budgetAmount: Money,
        createdBy: String,
        participantNames: List<String>,
    ): Trip {
        require(participantNames.isNotEmpty()) { "a trip needs at least one participant" }
        val trip = Trip(
            id = idGenerator.newId(),
            name = name,
            startDate = startDate,
            endDate = endDate,
            budgetAmount = budgetAmount,
            createdBy = createdBy,
        )
        repository.saveTrip(trip)
        for (participantName in participantNames) {
            repository.saveTripParticipant(
                TripParticipant(id = idGenerator.newId(), tripId = trip.id, displayName = participantName),
            )
        }
        return trip
    }
}
