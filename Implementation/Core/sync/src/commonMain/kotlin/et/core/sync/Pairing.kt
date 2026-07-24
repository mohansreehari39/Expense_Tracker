package et.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Two-step QR pairing per Design/Core/04-pairing-and-crypto.md: a new
 * device first shows [DevicePubkeyPayload] (its own ephemeral pairing
 * pubkey), the existing device scans it and shows back
 * [HouseholdInvitePayload] (the household key, wrapped to that pubkey) —
 * so the raw household key is never transmitted unencrypted, even locally.
 *
 * Simplification for this first pass: [wrappedKey] is produced by
 * [Crypto.encrypt] using a key agreed out-of-band-equivalent (the
 * scanning device derives the AES key from the scanned pubkey bytes
 * directly) rather than a full X25519 ECDH exchange. This keeps pairing
 * usable end-to-end now without hand-rolling elliptic-curve key agreement;
 * swap in real X25519 (e.g. via a multiplatform crypto library) before
 * this ships to real users, since deriving an AES key directly from a
 * publicly-scanned value is not a sound key-agreement scheme on its own.
 */
@Serializable
data class DevicePubkeyPayload(
    val deviceId: String,
    val pubkey: String, // base64
)

@Serializable
data class HouseholdInvitePayload(
    val householdId: String,
    val wrappedKey: String, // base64 of Crypto.encrypt(...) output
    val bootstrapPeerHint: String? = null,
)

@OptIn(ExperimentalEncodingApi::class)
object Pairing {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeDevicePubkey(payload: DevicePubkeyPayload): String =
        Base64.encode(json.encodeToString(payload).encodeToByteArray())

    fun decodeDevicePubkey(qrText: String): DevicePubkeyPayload =
        json.decodeFromString(Base64.decode(qrText).decodeToString())

    fun encodeInvite(payload: HouseholdInvitePayload): String =
        Base64.encode(json.encodeToString(payload).encodeToByteArray())

    fun decodeInvite(qrText: String): HouseholdInvitePayload =
        json.decodeFromString(Base64.decode(qrText).decodeToString())

    /** Wraps [householdKey] so only a device holding [scannedPubkey] can unwrap it. */
    fun wrapHouseholdKey(householdKey: ByteArray, scannedPubkey: ByteArray): ByteArray =
        Crypto.encrypt(keyFromPubkey(scannedPubkey), householdKey)

    fun unwrapHouseholdKey(wrapped: ByteArray, ownPubkey: ByteArray): ByteArray =
        Crypto.decrypt(keyFromPubkey(ownPubkey), wrapped)

    private fun keyFromPubkey(pubkey: ByteArray): ByteArray = pubkey.copyOf(32)
}
