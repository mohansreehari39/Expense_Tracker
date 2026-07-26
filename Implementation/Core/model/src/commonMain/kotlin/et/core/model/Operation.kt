package et.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class EntityType {
    HOUSEHOLD,
    MEMBER,
    CATEGORY,
    MONTHLY_BUDGET,
    HOUSEHOLD_EXPENSE,
    TRIP,
    TRIP_PARTICIPANT,
    TRIP_EXPENSE,
    EXPENSE_SPLIT,
    SETTLEMENT,
    HOUSEHOLD_SETTLEMENT,
    DEVICE,
}

@Serializable
enum class OpType { CREATE, UPDATE, DELETE }

/**
 * The unit of sync. Immutable once created. See
 * Design/Core/02-data-model.md and Design/Core/03-sync-protocol.md.
 *
 * [patch] carries only the changed fields (for UPDATE) or the full initial
 * field set (for CREATE), keyed by field name, as raw JsonElement so an
 * older app version can store-and-forward fields it doesn't understand
 * from a newer one without data loss.
 */
@Serializable
data class Operation(
    val opId: String,
    val entityType: EntityType,
    val entityId: String,
    val opType: OpType,
    val patch: Map<String, JsonElement> = emptyMap(),
    val authorDeviceId: String,
    val hlc: Hlc,
    val receivedFrom: String? = null,
)
