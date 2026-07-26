package et.windows.ui

import et.windows.KharchaConfig
import et.windows.server.PairingSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What every "Add via QR" button encodes today — server address + household
 * /trip id + name, deep-linking the Android app straight into the right
 * household/activity (or just the server, for device-only pairing) in one
 * scan. Still NOT the full asymmetric two-step handshake designed in
 * Core/sync/Pairing.kt (that needs a genuinely new two-scan UX on both
 * apps, tracked separately) — but [pairingSecret] closes the concrete gap
 * that mattered most in the meantime: without it, any device that could
 * merely reach this server's HTTP port on the LAN could silently register
 * itself via `POST /devices` without ever having scanned a QR at all. Every
 * QR kind carries one because every kind can trigger a first-time device
 * pairing (see Android's `joinScanLauncher`, which auto-pairs when joining
 * a household/activity from a device that hasn't paired yet).
 */
@Serializable
data class JoinInvitePayload(
    val kind: String, // "household" | "activity" | "server"
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val pairingSecret: String,
)

private val json = Json { ignoreUnknownKeys = true }

fun encodeJoinInvite(payload: JoinInvitePayload): String = json.encodeToString(payload)

fun joinInviteForHousehold(householdId: String, householdName: String) =
    JoinInvitePayload(kind = "household", id = householdId, name = householdName, host = localNetworkAddress(), port = KharchaConfig.port, pairingSecret = PairingSession.issue())

fun joinInviteForTrip(tripId: String, tripName: String) =
    JoinInvitePayload(kind = "activity", id = tripId, name = tripName, host = localNetworkAddress(), port = KharchaConfig.port, pairingSecret = PairingSession.issue())

/**
 * Device-level pairing QR (see PairAndroidDeviceDialog.kt) — Android keys
 * a paired server by host:port, so [id] here is cosmetic only, but [name]
 * is shown to the user mid-scan so they know which physical machine
 * they're pairing to (see README V1 "identify the server by computer name").
 */
fun joinInviteForServerPairing() =
    JoinInvitePayload(kind = "server", id = "server", name = KharchaConfig.serverDisplayName(), host = localNetworkAddress(), port = KharchaConfig.port, pairingSecret = PairingSession.issue())
