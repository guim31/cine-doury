package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlaybackMarginTest {
    /** A film of 60 min in segments of 6 s, as Jellyfin cuts a transcoded stream. */
    private val chunks = List(600) { MarginChunk((it * 6).seconds, ((it + 1) * 6).seconds) }

    private fun inputs(
        position: Duration,
        memoryEnd: Duration,
        loading: Boolean = false,
        started: Boolean = true,
    ) = MarginInputs(position, memoryEnd, loading, started)

    @Test
    fun `nothing is fetched before the film starts, so that it starts as fast as on the TV`() {
        assertNull(MarginPlan.next(chunks, { false }, inputs(0.seconds, 2.seconds, started = false), 15.minutes))
    }

    @Test
    fun `the disk gives way to the player while its memory is low and the disk holds nothing beyond`() {
        assertNull(MarginPlan.next(chunks, { false }, inputs(10.seconds, 30.seconds, loading = true), 15.minutes))
        // Its memory at the low mark: the disk takes over from where the memory ends.
        assertEquals(10, MarginPlan.next(chunks, { false }, inputs(10.seconds, 60.seconds, loading = true), 15.minutes))
        // Low memory, but the disk is well ahead: the player refills from the disk, the fetch goes on.
        val cached = { index: Int -> index in 0..40 }
        assertEquals(41, MarginPlan.next(chunks, cached, inputs(10.seconds, 20.seconds, loading = true), 15.minutes))
    }

    @Test
    fun `the chunk the player is loading is its own, an idle player's edge is fetched`() {
        assertEquals(10, MarginPlan.next(chunks, { false }, inputs(0.seconds, 57.seconds, loading = true), 15.minutes))
        assertEquals(9, MarginPlan.next(chunks, { false }, inputs(0.seconds, 57.seconds, loading = false), 15.minutes))
    }

    @Test
    fun `the margin stops at the target, at the end of the film, and when the disk is full`() {
        val upTo = { index: Int -> index < 160 }
        assertNull(MarginPlan.next(chunks, upTo, inputs(0.seconds, 120.seconds), 15.minutes))
        assertEquals(160, MarginPlan.next(chunks, upTo, inputs(0.seconds, 120.seconds), 30.minutes))
        assertNull(MarginPlan.next(chunks, upTo, inputs(0.seconds, 120.seconds), 30.minutes, room = false))
        assertNull(MarginPlan.next(chunks, { true }, inputs(59.minutes, 60.minutes), 15.minutes))
    }

    @Test
    fun `the margin is the memory, then what follows on the disk without a hole`() {
        val cached = { index: Int -> index in 20..49 || index in 60..70 }
        assertEquals(5.minutes, MarginPlan.end(chunks, cached, 60.seconds, 120.seconds))
        assertEquals(120.seconds, MarginPlan.end(chunks, { false }, 60.seconds, 120.seconds))
        val status = MarginStatus(60.seconds, 5.minutes, 120.seconds, 15.minutes)
        assertEquals(4.minutes, status.ahead)
        assertEquals(1.minutes, status.inMemory)
    }

    @Test
    fun `after a seek beyond the margin, the fetch starts again from the new position`() {
        val before = { index: Int -> index in 0..100 }
        // At 30 min, nothing is loaded: the memory first, then the disk from its end, not from the old one.
        assertEquals(305, MarginPlan.next(chunks, before, inputs(30.minutes, 30.minutes + 35.seconds), 15.minutes))
        assertEquals(30.minutes + 35.seconds, MarginPlan.end(chunks, before, 30.minutes, 30.minutes + 35.seconds))
        // Back within the margin: it covers the jump, nothing to fetch there.
        assertEquals(606.seconds, MarginPlan.end(chunks, before, 5.minutes, 5.minutes))
    }

    @Test
    fun `the memory takes a quarter of the heap, the disk half of the free space, both capped`() {
        val mb = 1024 * 1024
        val gb = 1024L * mb
        assertEquals(64 * mb, MarginBudget.memoryBytes(256))
        assertEquals(128 * mb, MarginBudget.memoryBytes(768))
        assertEquals(32 * mb, MarginBudget.memoryBytes(96))
        assertEquals(4 * gb, MarginBudget.diskBytes(64 * gb))
        assertEquals(gb / 2, MarginBudget.diskBytes(2 * gb))
        assertEquals(0L, MarginBudget.diskBytes(gb / 2))
        // 15 min at 4 Mb/s: 450 MB, which a phone's heap never holds; the disk does.
        assertTrue(MarginBudget.duration(64L * mb, 4_000_000) < 3.minutes)
        assertTrue(MarginBudget.duration(MarginBudget.diskBytes(16 * gb), 4_000_000) > 30.minutes)
    }
}
