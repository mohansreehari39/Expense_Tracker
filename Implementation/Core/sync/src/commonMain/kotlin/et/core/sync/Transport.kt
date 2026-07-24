package et.core.sync

import kotlinx.coroutines.flow.Flow

/** Opaque handle to a discovered peer; platform-specific beyond a display id. */
data class PeerHandle(val id: String, val displayName: String = id)

/**
 * Platform-supplied discovery + connection. Android implements this over
 * NSD/Nearby Connections, the JVM/Windows app over JmDNS + sockets — see
 * Design/Windows/02-transport-implementation.md and
 * Design/Android/02-transport-and-permissions.md. `core-sync` itself has
 * zero platform imports; it only calls this interface.
 */
interface Transport {
    fun advertise(deviceId: String)
    fun discover(): Flow<PeerHandle>
    suspend fun connect(peer: PeerHandle): SyncChannel
}

/** A single bidirectional byte stream to one peer, framed message-at-a-time. */
interface SyncChannel {
    suspend fun send(bytes: ByteArray)
    suspend fun receive(): ByteArray
    suspend fun close()
}
