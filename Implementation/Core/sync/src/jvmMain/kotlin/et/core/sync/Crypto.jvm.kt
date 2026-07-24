package et.core.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val KEY_BYTES = 32
private const val NONCE_BYTES = 12
private const val TAG_BITS = 128
private const val ALGORITHM = "AES/GCM/NoPadding"

actual object Crypto {
    private val random = SecureRandom()

    actual fun generateKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    actual fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    actual fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > NONCE_BYTES) { "ciphertext too short to contain a nonce" }
        val nonce = ciphertext.copyOfRange(0, NONCE_BYTES)
        val body = ciphertext.copyOfRange(NONCE_BYTES, ciphertext.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(body)
    }
}
