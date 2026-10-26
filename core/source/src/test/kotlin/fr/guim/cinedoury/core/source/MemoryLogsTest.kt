package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.PlayMethod
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryLogsTest {
    @AfterTest
    fun clear() {
        FailureLog.clear()
        PlaybackLog.clear()
    }

    @Test
    fun `the last failures are kept in a ring, with the class of the error, and still reach the sink`() {
        val sunk = mutableListOf<String>()
        val previous = FailureLog.sink
        FailureLog.sink = FailureLog.Sink { _, message, _ -> sunk += message }
        try {
            repeat(FailureLog.CAPACITY + 5) { FailureLog.warn("failure $it") }
            FailureLog.error("broken", IllegalStateException("no key"))
        } finally {
            FailureLog.sink = previous
        }
        val recent = FailureLog.recent()
        assertEquals(FailureLog.CAPACITY, recent.size)
        assertEquals("failure 6", recent.first().message)
        assertEquals("IllegalStateException: no key", recent.last().error)
        assertEquals(FailureLog.Level.Error, recent.last().level)
        assertEquals(FailureLog.CAPACITY + 6, sunk.size)
    }

    @Test
    fun `the last decisions of playback are kept in a ring`() {
        repeat(PlaybackLog.CAPACITY + 1) {
            PlaybackLog.record(PlaybackLog.Entry(Instant.EPOCH, "Film $it", PlayMethod.DirectPlay, emptySet(), "mkv", "hevc", "eac3", null))
        }
        assertEquals(PlaybackLog.CAPACITY, PlaybackLog.recent().size)
        assertEquals("Film 1", PlaybackLog.recent().first().title)
    }
}
