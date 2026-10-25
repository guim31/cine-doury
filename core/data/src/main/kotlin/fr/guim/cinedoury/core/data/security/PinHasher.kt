package fr.guim.cinedoury.core.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Profile PINs, local to the device (Jellyfin 12 has no PIN any more): stored as
 * `pbkdf2-sha256:<iterations>:<salt>:<hash>`, salt and hash in Base64. Four digits are only 10 000
 * possibilities: the hash keeps the PIN out of sight in the database, it does not resist a search
 * by someone who holds the database. The PIN guards a profile against a child, not a thief.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 20_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val PARTS = 4
    private const val SEPARATOR = ":"
    private val random = SecureRandom()

    /** Whether [pin] is a valid profile PIN: exactly four digits. */
    fun isValid(pin: String): Boolean = pin.length == PIN_DIGITS && pin.all { it in '0'..'9' }

    fun hash(pin: String): String {
        require(isValid(pin)) { "A PIN is four digits" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val encoder = Base64.getEncoder()
        return listOf(
            PREFIX,
            ITERATIONS,
            encoder.encodeToString(salt),
            encoder.encodeToString(derive(pin, salt, ITERATIONS)),
        ).joinToString(":")
    }

    fun verify(
        pin: String,
        stored: String,
    ): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != PARTS || parts[0] != PREFIX || !isValid(pin)) return false
        return try {
            val (iterations, salt, expected) = parts.drop(1)
            MessageDigest.isEqual(decode(expected), derive(pin, decode(salt), iterations.toInt()))
        } catch (_: IllegalArgumentException) {
            // A malformed hash (NumberFormatException is one) never matches.
            false
        }
    }

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private fun derive(
        pin: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    const val PIN_DIGITS = 4
}
