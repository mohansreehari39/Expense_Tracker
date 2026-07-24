package et.core.sync

import et.core.model.Hlc
import et.core.model.Operation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory [OperationStore] for tests — no real persistence. */
class FakeOperationStore : OperationStore {
    private val ops = LinkedHashMap<String, Operation>()
    private val mutex = Mutex()

    override suspend fun append(ops: List<Operation>): Unit = mutex.withLock {
        for (op in ops) this.ops.putIfAbsent(op.opId, op)
    }

    override suspend fun opsSince(frontier: Map<String, Hlc>): List<Operation> = mutex.withLock {
        ops.values.filter { op ->
            val known = frontier[op.authorDeviceId]
            known == null || op.hlc > known
        }
    }

    override suspend fun localFrontier(): Map<String, Hlc> = mutex.withLock {
        ops.values.groupBy { it.authorDeviceId }.mapValues { (_, v) -> v.maxOf { it.hlc } }
    }

    suspend fun all(): List<Operation> = mutex.withLock { ops.values.toList() }
}
