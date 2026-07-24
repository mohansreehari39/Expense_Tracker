package et.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HlcTest {
    @Test
    fun compareTo_ordersByPhysicalThenCounterThenDeviceId() {
        val a = Hlc(100, 0, "device-a")
        val b = Hlc(100, 1, "device-a")
        val c = Hlc(101, 0, "device-a")
        val d = Hlc(100, 0, "device-b")

        assertTrue(a < b)
        assertTrue(b < c)
        assertTrue(a < c)
        assertTrue(a < d) // same physical+counter, deviceId tiebreak
    }

    @Test
    fun tick_advancesCounterWhenWallClockDoesNotMove() {
        var now = 1_000L
        val clock = HlcClock("device-a", WallClock { now })

        val first = clock.tick()
        val second = clock.tick() // wall clock unchanged
        assertEquals(first.physical, second.physical)
        assertEquals(first.counter + 1, second.counter)

        now = 1_001L
        val third = clock.tick() // wall clock moved forward
        assertEquals(1_001L, third.physical)
        assertEquals(0, third.counter)
    }

    @Test
    fun tick_neverGoesBackwardsEvenIfWallClockRegresses() {
        var now = 2_000L
        val clock = HlcClock("device-a", WallClock { now })
        val first = clock.tick()

        now = 1_000L // clock regressed
        val second = clock.tick()

        assertTrue(second > first)
    }

    @Test
    fun receive_advancesPastTheLaterOfLocalAndRemote() {
        val local = HlcClock("device-a", WallClock { 500 })
        local.tick() // local = (500, 0, a)

        val remote = Hlc(700, 3, "device-b")
        val merged = local.receive(remote)

        assertEquals(700, merged.physical)
        assertEquals(4, merged.counter)
    }

    @Test
    fun receive_isCommutativeInResultingCausalOrder() {
        // Two devices independently tick, then both receive each other's
        // timestamp: both must end up ordered strictly after both originals.
        val a = HlcClock("device-a", WallClock { 100 })
        val b = HlcClock("device-b", WallClock { 100 })
        val ta = a.tick()
        val tb = b.tick()

        val aAfterReceive = a.receive(tb)
        val bAfterReceive = b.receive(ta)

        assertTrue(aAfterReceive > ta && aAfterReceive > tb)
        assertTrue(bAfterReceive > ta && bAfterReceive > tb)
    }
}
