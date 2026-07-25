package et.windows.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What the "Add via QR" button encodes today. This is deliberately NOT the
 * real pairing scheme designed in Core/sync/Pairing.kt — that two-step
 * exchange needs a second device to show its own pubkey first, which
 * there's nothing to test against until the Android app exists. This is a
 * plain, unauthenticated placeholder (household/trip id + name) just so the
 * QR has real content to scan; swap it for [et.core.sync.Pairing]'s
 * handshake once there's an Android scanner on the other end.
 */
@Serializable
data class JoinInvitePayload(
    val kind: String, // "household" | "activity"
    val id: String,
    val name: String,
)

private val json = Json { ignoreUnknownKeys = true }

fun encodeJoinInvite(payload: JoinInvitePayload): String = json.encodeToString(payload)
