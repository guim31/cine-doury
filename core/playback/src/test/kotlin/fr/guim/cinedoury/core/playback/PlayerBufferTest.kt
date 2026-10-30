package fr.guim.cinedoury.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals

/** The buffer of the TV never moves with the phone's (M1): the values of every version since the J4. */
class PlayerBufferTest {
    @Test
    fun `the TV keeps its buffer`() {
        assertEquals(PlayerBuffer(30_000, 90_000, 1_500, 4_000, 192 * 1024 * 1024), PlayerBuffer.Tv)
    }

    @Test
    fun `the phone has the TV's until M2 sets its own`() {
        assertEquals(PlayerBuffer.Tv, PlayerBuffer.Phone)
    }
}
