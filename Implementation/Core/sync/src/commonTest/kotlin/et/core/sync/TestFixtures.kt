package et.core.sync

import et.core.model.EntityType
import et.core.model.Hlc
import et.core.model.OpType
import et.core.model.Operation
import kotlinx.serialization.json.JsonPrimitive

fun testOp(
    opId: String,
    entityId: String = "expense-1",
    opType: OpType = OpType.CREATE,
    authorDeviceId: String,
    hlc: Hlc,
    fields: Map<String, String> = mapOf("note" to opId),
): Operation = Operation(
    opId = opId,
    entityType = EntityType.HOUSEHOLD_EXPENSE,
    entityId = entityId,
    opType = opType,
    patch = fields.mapValues { JsonPrimitive(it.value) },
    authorDeviceId = authorDeviceId,
    hlc = hlc,
)
