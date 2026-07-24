package et.windows.db

import et.core.model.EntityType
import et.core.model.Hlc
import et.core.model.OpType
import et.core.model.Operation
import et.core.sync.OperationStore
import et.windows.db.sql.OperationLog
import et.windows.db.sql.WindowsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Persists the operation log described in Design/Core/03-sync-protocol.md.
 * Not wired to a real [et.core.sync.Transport] yet in v0 — see
 * Implementation/Windows/README.md — but every write already goes through
 * this so a later sync pass doesn't require a schema or write-path change.
 */
class SqlDelightOperationStore(private val db: WindowsDatabase) : OperationStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun append(ops: List<Operation>): Unit = withContext(Dispatchers.IO) {
        db.transaction {
            for (op in ops) {
                db.schemaQueries.insertOperation(
                    opId = op.opId,
                    entityType = op.entityType.name,
                    entityId = op.entityId,
                    opType = op.opType.name,
                    patchJson = json.encodeToString(op.patch),
                    authorDeviceId = op.authorDeviceId,
                    hlcPhysical = op.hlc.physical,
                    hlcCounter = op.hlc.counter.toLong(),
                    receivedFrom = op.receivedFrom,
                )
            }
        }
    }

    override suspend fun opsSince(frontier: Map<String, Hlc>): List<Operation> = withContext(Dispatchers.IO) {
        db.schemaQueries.allOperations().executeAsList().map(::toOperation).filter { op ->
            val known = frontier[op.authorDeviceId]
            known == null || op.hlc > known
        }
    }

    override suspend fun localFrontier(): Map<String, Hlc> = withContext(Dispatchers.IO) {
        db.schemaQueries.allOperations().executeAsList().map(::toOperation)
            .groupBy { it.authorDeviceId }
            .mapValues { (_, ops) -> ops.maxOf { it.hlc } }
    }

    private fun toOperation(row: OperationLog): Operation = Operation(
        opId = row.opId,
        entityType = EntityType.valueOf(row.entityType),
        entityId = row.entityId,
        opType = OpType.valueOf(row.opType),
        patch = json.decodeFromString<Map<String, JsonElement>>(row.patchJson),
        authorDeviceId = row.authorDeviceId,
        hlc = Hlc(row.hlcPhysical, row.hlcCounter.toInt(), row.authorDeviceId),
        receivedFrom = row.receivedFrom,
    )
}
