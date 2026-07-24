package et.core.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Hybrid Logical Clock. Gives a total order over operations across devices
 * without requiring synchronized clocks. See Design/Core/02-data-model.md.
 */
@Serializable
data class Hlc(val physical: Long, val counter: Int, val deviceId: String) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int =
        compareValuesBy(this, other, Hlc::physical, Hlc::counter, Hlc::deviceId)
}

/** Injected so tests can control "wall clock now" without real time passing. */
fun interface WallClock {
    fun nowMillis(): Long
}

private val systemWallClock = WallClock { Clock.System.now().toEpochMilliseconds() }

/**
 * Kulkarni et al. HLC algorithm. `tick()` is called for local events,
 * `receive()` when folding in a remote timestamp — both keep the clock
 * causally consistent while staying close to wall-clock time.
 */
class HlcClock(
    private val deviceId: String,
    private val wallClock: WallClock = systemWallClock,
) {
    private var last: Hlc = Hlc(0, 0, deviceId)

    fun tick(): Hlc {
        val physicalNow = wallClock.nowMillis()
        last = if (physicalNow > last.physical) {
            Hlc(physicalNow, 0, deviceId)
        } else {
            Hlc(last.physical, last.counter + 1, deviceId)
        }
        return last
    }

    fun receive(remote: Hlc): Hlc {
        val physicalNow = wallClock.nowMillis()
        val newPhysical = maxOf(physicalNow, last.physical, remote.physical)
        last = when {
            newPhysical == last.physical && newPhysical == remote.physical ->
                Hlc(newPhysical, maxOf(last.counter, remote.counter) + 1, deviceId)
            newPhysical == last.physical -> Hlc(newPhysical, last.counter + 1, deviceId)
            newPhysical == remote.physical -> Hlc(newPhysical, remote.counter + 1, deviceId)
            else -> Hlc(newPhysical, 0, deviceId)
        }
        return last
    }
}
