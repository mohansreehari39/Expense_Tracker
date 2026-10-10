package et.windows.db

import et.core.model.Hlc
import et.core.model.HlcClock
import et.core.model.encode

/**
 * Stamps every write the server makes (see Schema.sq's "Materialized
 * tables" header). Owns this server's Hybrid Logical Clock and its write
 * sequence number, and serializes all writes through one lock, so:
 * - stamps from this server are unique and increasing, and
 * - sequence numbers are handed out in the order writes actually land, so
 *   a pull's "serverSeq > cursor" never skips a row.
 * The app is one process with a small household's worth of writes, so a
 * single lock costs nothing noticeable.
 */
class Stamper(
    private val clock: HlcClock,
    initialSeq: Long,
    /** Told every sequence number handed out — [DatabaseIdentity.recordSeq]. */
    private val onSeq: (Long) -> Unit = {},
) {
    private val lock = Any()
    private var seq = initialSeq

    /** Run [block] with exclusive access to the database's write path. */
    fun <T> exclusive(block: Scope.() -> T): T = synchronized(lock) { Scope().block() }

    inner class Scope {
        /** Stamp for a change made on this server (e.g. through the Windows app's own screens). */
        fun localStamp(): String = clock.tick().encode()

        /** Keep this server's clock ahead of a stamp it has just accepted from another device. */
        fun observe(remote: Hlc) {
            clock.receive(remote)
        }

        fun nextSeq(): Long = (++seq).also(onSeq)
    }

    /** A fresh stamp outside any write — for the (unsent) operation log. */
    fun tick(): Hlc = synchronized(lock) { clock.tick() }
}
