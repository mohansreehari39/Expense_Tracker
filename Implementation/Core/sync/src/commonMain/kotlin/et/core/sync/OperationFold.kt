package et.core.sync

import et.core.model.EntityType
import et.core.model.Operation
import et.core.model.OpType
import kotlinx.serialization.json.JsonElement

/**
 * The "current state" of one entity after folding its operations. Repos
 * turn [fields] into a typed entity; `core-sync` doesn't know the entity
 * shapes, only that they're field maps.
 */
data class MaterializedEntity(
    val entityId: String,
    val entityType: EntityType,
    val fields: Map<String, JsonElement>,
    val isDeleted: Boolean,
)

/**
 * Implements the conflict-resolution rule from
 * Design/Core/03-sync-protocol.md#conflict-resolution-implementation-of-the-arch-rule:
 * per-entity, per-field last-write-wins by Hlc, with delete as an
 * unconditional tombstone (wins over any concurrent update regardless of
 * Hlc order).
 */
object OperationFold {
    fun fold(ops: List<Operation>): Map<String, MaterializedEntity> =
        ops.groupBy { it.entityId }
            .mapValues { (entityId, entityOps) -> foldOne(entityId, entityOps) }

    /** Fold just the operations for entities affected by [changedEntityIds], reusing [priorOps] as history. */
    fun foldEntity(entityId: String, ops: List<Operation>): MaterializedEntity =
        foldOne(entityId, ops)

    private fun foldOne(entityId: String, ops: List<Operation>): MaterializedEntity {
        val sorted = ops.sortedBy { it.hlc }
        val fields = LinkedHashMap<String, JsonElement>()
        var deleted = false
        var entityType: EntityType = sorted.first().entityType
        for (op in sorted) {
            entityType = op.entityType
            when (op.opType) {
                OpType.CREATE, OpType.UPDATE -> fields.putAll(op.patch)
                OpType.DELETE -> deleted = true
            }
        }
        return MaterializedEntity(entityId, entityType, fields, deleted)
    }
}
