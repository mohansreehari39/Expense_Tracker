package et.core.sync

/**
 * AES-256-GCM wrap/unwrap for sync channel bytes and the household key
 * itself, per Design/Core/04-pairing-and-crypto.md. `expect`/`actual` so
 * each platform uses its native crypto provider (javax.crypto on the JVM;
 * Android's javax.crypto/Keystore-backed provider once the Android target
 * is added) rather than a hand-rolled implementation.
 */
expect object Crypto {
    /** Random 256-bit key. */
    fun generateKey(): ByteArray

    /** Prepends a random 12-byte nonce to the returned ciphertext. */
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray

    /** Expects the format produced by [encrypt]: nonce prefix + ciphertext. */
    fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray
}
