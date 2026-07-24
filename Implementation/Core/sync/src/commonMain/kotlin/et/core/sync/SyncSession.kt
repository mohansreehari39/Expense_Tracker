package et.core.sync

sealed interface SyncResult {
    data class Success(val opsReceived: Int, val opsSent: Int) : SyncResult
    data class Failure(val reason: String, val cause: Throwable? = null) : SyncResult
}

/**
 * Runs one sync session against an already-connected [SyncChannel], per
 * the state machine in Design/Core/03-sync-protocol.md#session-state-machine.
 * Symmetric: both peers run this same method concurrently over their ends
 * of the same channel pair — there is no separate client/server role.
 *
 * Simplification vs. the full design: sends all missing operations as a
 * single [WireMessage.OpsBatch] rather than paginating into multiple
 * batches. Fine for the data volumes a household actually produces;
 * revisit with real pagination + mid-batch resume if first-sync payloads
 * ever get large enough to matter.
 */
class SyncSession(
    private val localDeviceId: String,
    private val householdId: String,
    private val store: OperationStore,
) {
    suspend fun run(channel: SyncChannel): SyncResult {
        return try {
            channel.send(WireCodec.encode(WireMessage.Hello(localDeviceId, householdId = householdId)))
            val peerHello = expect<WireMessage.Hello>(channel)
            if (peerHello.householdId != householdId) {
                return SyncResult.Failure(
                    "household mismatch: expected $householdId, got ${peerHello.householdId}",
                )
            }

            val localFrontier = store.localFrontier()
            channel.send(WireCodec.encode(WireMessage.Frontier(localFrontier)))
            expect<WireMessage.Frontier>(channel) // captured for future protocol validation/logging

            // My own frontier is exactly "what I already have" — sending it as
            // the request means "give me anything past this."
            channel.send(WireCodec.encode(WireMessage.OpsRequest(localFrontier)))
            val peerRequest = expect<WireMessage.OpsRequest>(channel)

            val opsToSend = store.opsSince(peerRequest.frontier)
            channel.send(WireCodec.encode(WireMessage.OpsBatch(opsToSend)))
            val incoming = expect<WireMessage.OpsBatch>(channel)

            store.append(incoming.ops)

            channel.send(WireCodec.encode(WireMessage.Ack(incoming.ops.lastOrNull()?.opId)))
            expect<WireMessage.Ack>(channel)

            SyncResult.Success(opsReceived = incoming.ops.size, opsSent = opsToSend.size)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: e::class.simpleName ?: "unknown error", e)
        }
    }

    private suspend inline fun <reified T : WireMessage> expect(channel: SyncChannel): T {
        val decoded = WireCodec.decode(channel.receive())
        return decoded as? T
            ?: error("expected ${T::class.simpleName}, got ${decoded::class.simpleName}")
    }
}
