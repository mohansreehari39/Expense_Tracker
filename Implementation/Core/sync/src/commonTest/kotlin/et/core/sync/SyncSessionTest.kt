package et.core.sync

import et.core.model.Hlc
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val HOUSEHOLD = "household-1"

class SyncSessionTest {
    @Test
    fun twoPeersConvergeRegardlessOfWhoInitiates() = runTest {
        val storeA = FakeOperationStore()
        val storeB = FakeOperationStore()
        storeA.append(listOf(testOp("op-a1", authorDeviceId = "a", hlc = Hlc(1, 0, "a"))))
        storeB.append(listOf(testOp("op-b1", entityId = "expense-2", authorDeviceId = "b", hlc = Hlc(1, 0, "b"))))

        val (channelA, channelB) = FakeChannel.pair()
        val sessionA = SyncSession("a", HOUSEHOLD, storeA)
        val sessionB = SyncSession("b", HOUSEHOLD, storeB)

        coroutineScopeRun {
            launch { assertIs<SyncResult.Success>(sessionA.run(channelA)) }
            launch { assertIs<SyncResult.Success>(sessionB.run(channelB)) }
        }

        val finalA = storeA.all().map { it.opId }.toSet()
        val finalB = storeB.all().map { it.opId }.toSet()
        assertEquals(setOf("op-a1", "op-b1"), finalA)
        assertEquals(setOf("op-a1", "op-b1"), finalB)
    }

    @Test
    fun gossipThroughARelayConvergesWithoutTheTwoOriginatorsEverConnecting() = runTest {
        // Phone A syncs only with the Server; Server later syncs only with Phone B.
        // B must end up with A's data despite never talking to A directly.
        val storeA = FakeOperationStore()
        val storeServer = FakeOperationStore()
        val storeB = FakeOperationStore()
        storeA.append(listOf(testOp("op-a1", authorDeviceId = "a", hlc = Hlc(1, 0, "a"))))

        run {
            val (chA, chServer) = FakeChannel.pair()
            val sessionA = SyncSession("a", HOUSEHOLD, storeA)
            val sessionServer = SyncSession("server", HOUSEHOLD, storeServer)
            coroutineScopeRun {
                launch { sessionA.run(chA) }
                launch { sessionServer.run(chServer) }
            }
        }
        assertEquals(setOf("op-a1"), storeServer.all().map { it.opId }.toSet())

        run {
            val (chServer, chB) = FakeChannel.pair()
            val sessionServer = SyncSession("server", HOUSEHOLD, storeServer)
            val sessionB = SyncSession("b", HOUSEHOLD, storeB)
            coroutineScopeRun {
                launch { sessionServer.run(chServer) }
                launch { sessionB.run(chB) }
            }
        }

        assertEquals(setOf("op-a1"), storeB.all().map { it.opId }.toSet())
    }

    @Test
    fun householdMismatchFailsWithoutExchangingOps() = runTest {
        val storeA = FakeOperationStore()
        val storeB = FakeOperationStore()
        val (channelA, channelB) = FakeChannel.pair()
        val sessionA = SyncSession("a", "household-1", storeA)
        val sessionB = SyncSession("b", "household-2", storeB)

        coroutineScopeRun {
            launch { assertIs<SyncResult.Failure>(sessionA.run(channelA)) }
            launch { assertIs<SyncResult.Failure>(sessionB.run(channelB)) }
        }
    }
}

/** Small helper so both launches above share one scope without importing coroutineScope everywhere. */
private suspend fun coroutineScopeRun(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
    kotlinx.coroutines.coroutineScope(block)
