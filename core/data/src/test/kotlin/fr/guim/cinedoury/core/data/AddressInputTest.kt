package fr.guim.cinedoury.core.data

import fr.guim.cinedoury.core.data.server.AddressInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddressInputTest {
    private fun parse(input: String) = AddressInput.parse(input)?.url

    @Test
    fun `http and port 8096 are assumed`() {
        assertEquals("http://192.0.2.10:8096", parse("192.0.2.10"))
        assertEquals("http://192.0.2.10:8096", parse(" 192.0.2.10/ "))
        assertEquals("http://jellyfin.example.com:8096", parse("jellyfin.example.com"))
    }

    @Test
    fun `what is given is kept`() {
        assertEquals("http://192.0.2.10:8920", parse("192.0.2.10:8920"))
        assertEquals("https://example.com", parse("https://example.com"))
        assertEquals("https://example.com/jellyfin", parse("HTTPS://example.com/jellyfin/"))
        assertEquals("http://localhost:8096", parse("http://localhost"))
    }

    @Test
    fun `nonsense is refused`() {
        assertNull(parse(""))
        assertNull(parse("192.0.2 .10"))
        assertNull(parse("ftp://example.com"))
        assertNull(parse("http://"))
    }
}
