package et.windows.db

import et.windows.db.sql.WindowsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** A phone paired via the "Add Android Device" QR — see Schema.sq's `pairedDevice` table for why this is separate from `device`. */
data class PairedDevice(val id: String, val label: String, val pairingKey: String, val pairedAt: Long, val lastSeenAt: Long)

/**
 * Windows-only pairing state, not part of the shared [et.core.domain.Repository]
 * surface (Android has no reason to read this back) and not appended to the
 * operation log — this is local device-pairing bookkeeping, not synced
 * household/activity data.
 *
 * [pair] and [heartbeat] are deliberately different operations: pairing is
 * triggered only by scanning the "Add Android Device" QR shown on this
 * machine's own screen (a trusted, physically-present action) and always
 * mints a brand-new [PairedDevice.pairingKey], invalidating any key issued
 * before it. Heartbeats happen unattended over the network every ~15s from
 * the phone's background sync loop and must present that same key — this
 * is what makes "remove this device" (or a fresh re-pair) actually stick:
 * a phone that only remembers its old deviceId, without the current key,
 * gets rejected rather than silently reviving the row.
 */
class PairedDeviceStore(private val db: WindowsDatabase) {
    suspend fun all(): List<PairedDevice> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectPairedDevices().executeAsList().map { it.toModel() }
    }

    /** Registers a device, always minting a fresh key — called only from the trusted "Add Android Device" QR flow. */
    suspend fun pair(id: String, label: String, now: Long): PairedDevice = withContext(Dispatchers.IO) {
        val existing = db.schemaQueries.selectPairedDeviceById(id).executeAsOneOrNull()
        val device = PairedDevice(id, label, UUID.randomUUID().toString(), existing?.pairedAt ?: now, now)
        db.schemaQueries.upsertPairedDevice(device.id, device.label, device.pairingKey, device.pairedAt, device.lastSeenAt)
        device
    }

    /** Validates [pairingKey] against the stored one; returns null (caller should reject) if the device is unknown or the key doesn't match. */
    suspend fun heartbeat(id: String, pairingKey: String, label: String, now: Long): PairedDevice? = withContext(Dispatchers.IO) {
        val existing = db.schemaQueries.selectPairedDeviceById(id).executeAsOneOrNull() ?: return@withContext null
        if (existing.pairingKey != pairingKey) return@withContext null
        val device = PairedDevice(id, label, pairingKey, existing.pairedAt, now)
        db.schemaQueries.upsertPairedDevice(device.id, device.label, device.pairingKey, device.pairedAt, device.lastSeenAt)
        device
    }

    /** Same check as [heartbeat] but doesn't touch `lastSeenAt` — used by the general per-request device-auth check (see `Server.kt`) on every non-pairing/non-heartbeat route, not just the heartbeat endpoint itself. */
    suspend fun isValid(id: String, pairingKey: String): Boolean = withContext(Dispatchers.IO) {
        db.schemaQueries.selectPairedDeviceById(id).executeAsOneOrNull()?.pairingKey == pairingKey
    }

    /** Forgets a paired device — it needs a fresh QR scan (and a fresh key) to reconnect. */
    suspend fun remove(id: String): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.deletePairedDevice(id)
    }

    private fun et.windows.db.sql.PairedDevice.toModel() = PairedDevice(id, label, pairingKey, pairedAt, lastSeenAt)
}
