package et.core.sync

import et.core.model.Hlc
import et.core.model.Operation
import kotlinx.serialization.Serializable

/**
 * Wire messages for one sync session, per Design/Core/03-sync-protocol.md.
 * Every message after [Hello] is symmetric — both peers push and pull in
 * the same session, matching the mesh topology (no separate client/server
 * role between two peers).
 */
@Serializable
sealed interface WireMessage {
    @Serializable
    data class Hello(
        val deviceId: String,
        val protocolVersion: Int = PROTOCOL_VERSION,
        val householdId: String,
    ) : WireMessage

    @Serializable
    data class Frontier(val frontier: Map<String, Hlc>) : WireMessage

    /** Payload is the sender's own frontier: "send me anything past this." */
    @Serializable
    data class OpsRequest(val frontier: Map<String, Hlc>) : WireMessage

    @Serializable
    data class OpsBatch(val ops: List<Operation>, val isFinal: Boolean = true) : WireMessage

    @Serializable
    data class Ack(val lastAppliedOpId: String?) : WireMessage

    companion object {
        const val PROTOCOL_VERSION = 1
    }
}
