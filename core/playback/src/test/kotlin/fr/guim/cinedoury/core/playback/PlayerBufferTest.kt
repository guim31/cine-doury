package fr.guim.cinedoury.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals

/** The buffer of the TV never moves with the phone's (M1, M2): the values of every version since the J4. */
class PlayerBufferTest {
    @Test
    fun `the TV keeps its buffer`() {
        assertEquals(PlayerBuffer(30_000, 90_000, 1_500, 4_000, 192 * 1024 * 1024), PlayerBuffer.Tv)
    }

    @Test
    fun `the phone starts as fast, then keeps 2 minutes in memory, capped by its heap`() {
        val phone = PlayerBuffer.phone(memoryClassMegabytes = 256)
        assertEquals(PlayerBuffer.Tv.startMillis, phone.startMillis)
        assertEquals(PlayerBuffer.Tv.rebufferMillis, phone.rebufferMillis)
        assertEquals(30_000, phone.minMillis)
        assertEquals(120_000, phone.maxMillis)
        assertEquals(64 * 1024 * 1024, phone.targetBytes)
        assertEquals(32 * 1024 * 1024, PlayerBuffer.phone(memoryClassMegabytes = 64).targetBytes)
        assertEquals(128 * 1024 * 1024, PlayerBuffer.phone(memoryClassMegabytes = 1024).targetBytes)
    }
}
