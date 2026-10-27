package fr.guim.cinedoury.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioOffsetTest {
    @Test
    fun `a positive offset puts the clock ahead, so the sound comes after its picture`() {
        assertEquals(10_200_000L, AudioOffset.clock(10_000_000L, 200))
    }

    @Test
    fun `a negative offset puts it behind, never before the start`() {
        assertEquals(9_900_000L, AudioOffset.clock(10_000_000L, -100))
        assertEquals(0L, AudioOffset.clock(50_000L, -500))
    }

    @Test
    fun `an unknown position stays unknown, no offset changes nothing`() {
        assertEquals(Long.MIN_VALUE, AudioOffset.clock(Long.MIN_VALUE, 300))
        assertEquals(1_234L, AudioOffset.clock(1_234L, 0))
    }
}
