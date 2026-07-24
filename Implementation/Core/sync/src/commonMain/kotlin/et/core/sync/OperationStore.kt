package et.core.sync

import et.core.model.Hlc
import et.core.model.Operation

/**
 * Local append-only log storage. Platform apps provide the real
 * (SQLDelight-backed) implementation; see Design/Windows and
 * Design/Android transport-implementation docs. `core-sync` never touches
 * a database directly — it only calls this interface.
 */
interface OperationStore {
    /** Idempotent: re-appending an operation already stored by opId is a no-op. */
    suspend fun append(ops: List<Operation>)

    /**
     * All operations authored by any device where that device's Hlc is
     * strictly greater than the corresponding entry in [frontier] (or all
     * of that device's operations, if it's missing from [frontier]
     * entirely — i.e. the requester has never heard of that device).
     */
    suspend fun opsSince(frontier: Map<String, Hlc>): List<Operation>

    /** Per-author-device maximum Hlc seen across the whole local log. */
    suspend fun localFrontier(): Map<String, Hlc>
}
