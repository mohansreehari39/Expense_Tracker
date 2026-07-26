package et.windows.server

import java.util.UUID

/**
 * A single in-memory, single-use secret generated fresh every time the
 * "Add Android Device" QR is shown. `POST /devices` must present it,
 * closing the actual gap in today's pairing flow: without this, any
 * device that can merely reach this server's HTTP port on the LAN could
 * silently register itself as a paired device without ever having scanned
 * a QR — the QR only ever carried the address, not a secret. Deliberately
 * ephemeral/in-memory only (not persisted): it protects a short pairing
 * window, not data at rest. See README V1 "real crypto handshake for QR
 * pairing" and Design/Core/04-pairing-and-crypto.md.
 */
object PairingSession {
    private const val VALID_MS = 5 * 60 * 1000L

    private var current: Pair<String, Long>? = null // secret to expiryEpochMillis

    @Synchronized
    fun issue(): String {
        val secret = UUID.randomUUID().toString()
        current = secret to (System.currentTimeMillis() + VALID_MS)
        return secret
    }

    /** Single-use: a matching secret is consumed (invalidated) on its very first successful check, whether or not the caller goes on to actually register. */
    @Synchronized
    fun consume(secret: String): Boolean {
        val (expected, expiry) = current ?: return false
        if (System.currentTimeMillis() > expiry) {
            current = null
            return false
        }
        if (expected != secret) return false
        current = null
        return true
    }
}
