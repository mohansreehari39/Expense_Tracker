package et.core.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Encodes one [WireMessage] per [SyncChannel.send]/[SyncChannel.receive]
 * call — framing (length-prefixing multiple messages on a raw socket
 * stream) is a platform Transport concern, not core-sync's.
 *
 * JSON for now; swap for kotlinx-serialization-cbor if wire size becomes a
 * problem on constrained links (Bluetooth/Nearby) — see
 * Design/Core/03-sync-protocol.md.
 */
object WireCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(message: WireMessage): ByteArray = json.encodeToString(message).encodeToByteArray()

    fun decode(bytes: ByteArray): WireMessage = json.decodeFromString(bytes.decodeToString())
}
