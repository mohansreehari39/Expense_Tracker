package et.core.sync

import et.core.model.Hlc
import et.core.model.OpType
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationFoldTest {
    @Test
    fun laterUpdateWinsPerFieldRegardlessOfArrivalOrder() {
        val create = testOp(
            "op-1", authorDeviceId = "a", hlc = Hlc(1, 0, "a"),
            fields = mapOf("amount" to "100", "category" to "groceries"),
        )
        val laterFix = testOp(
            "op-2", opType = OpType.UPDATE, authorDeviceId = "b", hlc = Hlc(2, 0, "b"),
            fields = mapOf("amount" to "150"),
        )

        val forward = OperationFold.fold(listOf(create, laterFix))["expense-1"]!!
        val reversed = OperationFold.fold(listOf(laterFix, create))["expense-1"]!!

        for (result in listOf(forward, reversed)) {
            assertEquals(JsonPrimitive("150"), result.fields["amount"])
            assertEquals(JsonPrimitive("groceries"), result.fields["category"])
            assertFalse(result.isDeleted)
        }
    }

    @Test
    fun concurrentEditsToDifferentFieldsBothApply() {
        val create = testOp("op-1", authorDeviceId = "a", hlc = Hlc(1, 0, "a"), fields = mapOf("amount" to "100"))
        val editAmount = testOp(
            "op-2", opType = OpType.UPDATE, authorDeviceId = "a", hlc = Hlc(2, 0, "a"),
            fields = mapOf("amount" to "120"),
        )
        val editCategory = testOp(
            "op-3", opType = OpType.UPDATE, authorDeviceId = "b", hlc = Hlc(2, 0, "b"),
            fields = mapOf("category" to "eating-out"),
        )

        val result = OperationFold.fold(listOf(create, editAmount, editCategory))["expense-1"]!!
        assertEquals(JsonPrimitive("120"), result.fields["amount"])
        assertEquals(JsonPrimitive("eating-out"), result.fields["category"])
    }

    @Test
    fun deleteWinsRegardlessOfHlcOrderRelativeToUpdates() {
        val create = testOp("op-1", authorDeviceId = "a", hlc = Hlc(1, 0, "a"))
        val delete = testOp("op-2", opType = OpType.DELETE, authorDeviceId = "a", hlc = Hlc(2, 0, "a"))
        // A concurrent update with a *later* Hlc than the delete still loses.
        val lateUpdate = testOp(
            "op-3", opType = OpType.UPDATE, authorDeviceId = "b", hlc = Hlc(3, 0, "b"),
            fields = mapOf("amount" to "999"),
        )

        val result = OperationFold.fold(listOf(create, delete, lateUpdate))["expense-1"]!!
        assertTrue(result.isDeleted)
    }

    @Test
    fun foldIsInvariantUnderOperationOrdering() {
        val ops = listOf(
            testOp("op-1", authorDeviceId = "a", hlc = Hlc(1, 0, "a"), fields = mapOf("amount" to "10")),
            testOp("op-2", opType = OpType.UPDATE, authorDeviceId = "b", hlc = Hlc(2, 0, "b"), fields = mapOf("amount" to "20")),
            testOp("op-3", opType = OpType.UPDATE, authorDeviceId = "a", hlc = Hlc(3, 0, "a"), fields = mapOf("category" to "rent")),
            testOp("op-4", opType = OpType.UPDATE, authorDeviceId = "c", hlc = Hlc(2, 5, "c"), fields = mapOf("note" to "shared")),
        )

        val baseline = OperationFold.fold(ops)["expense-1"]!!
        val permutations = listOf(
            ops.reversed(),
            listOf(ops[2], ops[0], ops[3], ops[1]),
            listOf(ops[3], ops[2], ops[1], ops[0]),
        )
        for (perm in permutations) {
            assertEquals(baseline, OperationFold.fold(perm)["expense-1"])
        }
    }
}
