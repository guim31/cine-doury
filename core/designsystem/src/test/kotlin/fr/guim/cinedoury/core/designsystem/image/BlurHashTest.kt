package fr.guim.cinedoury.core.designsystem.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlurHashTest {
    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private fun encode83(
        value: Int,
        length: Int,
    ): String =
        (length - 1 downTo 0)
            .map { power ->
                var divisor = 1
                repeat(power) { divisor *= 83 }
                alphabet[value / divisor % 83]
            }.joinToString("")

    /** A 1 × 1 component hash: a single, flat colour. */
    private fun flat(rgb: Int) = "00" + encode83(rgb, 4)

    @Test
    fun `a single component is a flat colour`() {
        val pixels = assertNotNull(BlurHash.decode(flat(0xF2C14E), 4, 3))
        assertEquals(12, pixels.size)
        pixels.forEach { assertEquals(0xFFF2C14E.toInt(), it) }
    }

    @Test
    fun `decodes the reference hash of the BlurHash project`() {
        val pixels = assertNotNull(BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 32, 18))
        assertEquals(32 * 18, pixels.size)
        assertTrue(pixels.all { it ushr 24 == 0xFF }, "opaque")
        assertTrue(pixels.distinct().size > 50, "a gradient, not a flat colour")
    }

    @Test
    fun `rejects invalid hashes`() {
        assertNull(BlurHash.decode("", 4, 4))
        assertNull(BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdn", 4, 4), "one character short")
        assertNull(BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdn\"", 4, 4), "not in the alphabet")
    }
}
