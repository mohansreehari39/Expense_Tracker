package et.android.kharcha.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Mirrors Implementation/Windows/.../ui/JoinInvite.kt's payload shape.
 * [pairingSecret] is the single-use secret that QR's server minted — must
 * be presented back to `POST /devices` (see `ApiClient.pairDevice`) or the
 * server rejects the registration; see `PairingSession.kt` on the Windows
 * side for why this exists (prevents an unpaired device from registering
 * itself without ever having scanned a real QR).
 */
@Serializable
data class JoinInvitePayload(
    val kind: String, // "household" | "activity"
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val pairingSecret: String,
) {
    val serverBaseUrl: String get() = "http://$host:$port"
}

private val json = Json { ignoreUnknownKeys = true }

fun decodeJoinInvite(qrText: String): JoinInvitePayload? = runCatching { json.decodeFromString<JoinInvitePayload>(qrText) }.getOrNull()
