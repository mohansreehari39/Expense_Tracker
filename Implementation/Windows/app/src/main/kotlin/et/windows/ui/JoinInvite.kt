package et.windows.ui

import et.windows.server.DEFAULT_PORT
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What the "Add via QR" button encodes today. This is deliberately NOT the
 * real pairing scheme designed in Core/sync/Pairing.kt — that two-step
 * exchange needs a second device to show its own pubkey first, which
 * there's nothing to test against until the Android app exists. This is a
 * plain, unauthenticated placeholder (server address + household/trip id
 * + name) just so the QR has real content to scan — enough for the
 * Android app to both connect to this server and deep-link into the right
 * household/activity in one scan; swap it for [et.core.sync.Pairing]'s
 * handshake once there's a real two-device exchange to build against.
 */
@Serializable
data class JoinInvitePayload(
    val kind: String, // "household" | "activity"
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
)

private val json = Json { ignoreUnknownKeys = true }

fun encodeJoinInvite(payload: JoinInvitePayload): String = json.encodeToString(payload)

fun joinInviteForHousehold(householdId: String, householdName: String) =
    JoinInvitePayload(kind = "household", id = householdId, name = householdName, host = localNetworkAddress(), port = DEFAULT_PORT)

fun joinInviteForTrip(tripId: String, tripName: String) =
    JoinInvitePayload(kind = "activity", id = tripId, name = tripName, host = localNetworkAddress(), port = DEFAULT_PORT)
