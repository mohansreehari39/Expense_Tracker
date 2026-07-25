package et.android.kharcha.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Mirrors Implementation/Windows/.../ui/JoinInvite.kt's payload shape. As
 * noted there, this is a plain unauthenticated placeholder — not the real
 * pairing handshake in Core/sync/Pairing.kt — good enough to connect to a
 * server and deep-link into a household/activity from one QR scan.
 */
@Serializable
data class JoinInvitePayload(
    val kind: String, // "household" | "activity"
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
) {
    val serverBaseUrl: String get() = "http://$host:$port"
}

private val json = Json { ignoreUnknownKeys = true }

fun decodeJoinInvite(qrText: String): JoinInvitePayload? = runCatching { json.decodeFromString<JoinInvitePayload>(qrText) }.getOrNull()
