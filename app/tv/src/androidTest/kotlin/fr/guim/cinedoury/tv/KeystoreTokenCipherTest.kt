package fr.guim.cinedoury.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.guim.cinedoury.core.data.security.KeystoreTokenCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The token cipher on the real Android Keystore (Robolectric has none): AES-256-GCM with an IV the
 * Keystore draws, as Android 14 requires (`SPEC.md` § 4.7). Checked on the API 34 TV emulator.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
    private val cipher = KeystoreTokenCipher(alias = ALIAS)

    @AfterTest
    fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    @Test
    fun aTokenComesBackAsItWent() {
        val token = "0123456789abcdef0123456789abcdef"
        val sealed = cipher.encrypt(token)

        assertNotEquals(token, sealed)
        assertEquals(token, cipher.decrypt(sealed))
        // IV (12 bytes) + ciphertext + tag (16 bytes).
        assertEquals(IV_BYTES + token.length + TAG_BYTES, Base64.getDecoder().decode(sealed).size)
    }

    @Test
    fun eachEncryptionDrawsItsOwnIv() {
        val first = cipher.encrypt("token")
        val second = cipher.encrypt("token")

        assertNotEquals(first, second)
        assertEquals("token", cipher.decrypt(second))
    }

    @Test
    fun anAlteredTokenIsUnreadable() {
        val sealed = Base64.getDecoder().decode(cipher.encrypt("token"))
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()

        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(sealed)))
        assertNull(cipher.decrypt("not base64 at all"))
    }

    @Test
    fun theMainThreadAndAWorkerShareTheKey() =
        runBlocking {
            // The first key is made on one thread, used on another: the app encrypts off the main thread.
            val sealed = withContext(Dispatchers.Default) { cipher.encrypt("token") }
            assertEquals("token", cipher.decrypt(sealed))
        }

    private companion object {
        const val ALIAS = "cinedoury.tokens.test"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
