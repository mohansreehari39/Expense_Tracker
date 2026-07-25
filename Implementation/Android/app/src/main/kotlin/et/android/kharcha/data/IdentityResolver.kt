package et.android.kharcha.data

/**
 * Turns the one-time signup name into a member/participant id for every
 * household/activity this device can see, so identity is resolved exactly
 * once per household/activity rather than prompted for each time one is
 * opened. Safe to call repeatedly — [ApiClient.addMember]/
 * [ApiClient.addTripParticipant] dedupe by name server-side, and this skips
 * the call entirely once [ConnectionStore] already has an id cached.
 */
suspend fun ensureMyIdentity(
    api: ApiClient,
    store: ConnectionStore,
    myName: String,
    households: List<HouseholdDto>,
    trips: List<TripDto>,
) {
    for (household in households) {
        if (store.myMemberId(household.id) == null) {
            val member = api.addMember(household.id, myName)
            store.setMyMemberId(household.id, member.id)
        }
    }
    for (trip in trips) {
        if (store.myParticipantId(trip.id) == null) {
            val participant = api.addTripParticipant(trip.id, myName)
            store.setMyParticipantId(trip.id, participant.id)
        }
    }
}
