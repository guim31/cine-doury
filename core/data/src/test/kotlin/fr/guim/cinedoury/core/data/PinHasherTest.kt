package fr.guim.cinedoury.core.data

import fr.guim.cinedoury.core.data.security.PinHasher
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PinHasherTest {
    @Test
    fun `a PIN is checked against its hash, never stored in clear`() {
        val hash = PinHasher.hash("2580")
        assertFalse("2580" in hash)
        assertTrue(hash.startsWith("pbkdf2-sha256:"))
        assertTrue(PinHasher.verify("2580", hash))
        assertFalse(PinHasher.verify("2581", hash))
    }

    @Test
    fun `the same PIN gives a different hash each time`() {
        assertNotEquals(PinHasher.hash("1234"), PinHasher.hash("1234"))
    }

    @Test
    fun `only four digits`() {
        assertFalse(PinHasher.isValid("123"))
        assertFalse(PinHasher.isValid("12a4"))
        assertFalse(PinHasher.isValid("12345"))
        assertFailsWith<IllegalArgumentException> { PinHasher.hash("12") }
        assertFalse(PinHasher.verify("1234", "garbage"))
    }
}
