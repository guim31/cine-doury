package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** N and Q (`SPEC.md` § 4.29): the banner only for failed loads, an error for a playback that stands still. */
class PlaybackWatchTest {
    private val step = 250.milliseconds

    private fun PlaybackWatch.over(
        time: Duration,
        sample: (Int) -> PlaybackWatch.Sample,
    ): PlaybackWatch.Verdict {
        var verdict: PlaybackWatch.Verdict? = null
        repeat((time / step).toInt()) { verdict = sample(sample(it)) }
        return checkNotNull(verdict)
    }

    private fun PlaybackWatch.sample(sample: PlaybackWatch.Sample) = sample(step, sample)

    private fun waiting(
        loads: Int = 0,
        failing: Int = 0,
        position: Duration = Duration.ZERO,
        playWhenReady: Boolean = true,
        offline: Boolean = false,
    ) = PlaybackWatch.Sample(position, buffering = true, playWhenReady, loads, failing, offline)

    @Test
    fun `a stream that starts is a wait, not a lost connection`() {
        val watch = PlaybackWatch()
        val verdict = watch.over(10.seconds) { waiting() }
        assertFalse(verdict.reconnecting)
        assertFalse(verdict.stalled)
    }

    @Test
    fun `failing loads show the banner after 3 s of waiting, and a load that succeeds hides it`() {
        val watch = PlaybackWatch()
        assertFalse(watch.over(2.seconds) { waiting(loads = 3, failing = 1) }.reconnecting)
        assertTrue(watch.over(1.seconds) { waiting(loads = 3, failing = 2) }.reconnecting)
        assertFalse(watch.sample(waiting(loads = 4, failing = 0)).reconnecting)
        // Loads that keep failing for 30 s: a lost connection, said as such.
        assertFalse(watch.over(PlaybackWatch.STALL - step) { waiting(loads = 4, failing = 5) }.stalled)
        val lost = watch.sample(waiting(loads = 4, failing = 6))
        assertTrue(lost.stalled && lost.failing)
    }

    @Test
    fun `nothing moving for 30 s is a stall, not a second before`() {
        val watch = PlaybackWatch()
        watch.sample(waiting(loads = 12, position = 2.seconds))
        val before = watch.over(PlaybackWatch.STALL - step) { waiting(loads = 12, position = 2.seconds) }
        assertFalse(before.stalled)
        val after = watch.sample(waiting(loads = 12, position = 2.seconds))
        assertTrue(after.stalled)
        assertFalse(after.failing, "nothing failed: the player stands still")
        assertEquals(PlaybackWatch.STALL, after.stalledFor)
        assertEquals(PlaybackWatch.STALL, after.sinceLastLoad)
    }

    @Test
    fun `a load that succeeds, a position that moves, a pause, no network - no stall`() {
        val watch = PlaybackWatch()
        assertFalse(watch.over(PlaybackWatch.STALL * 2) { waiting(loads = it / 80) }.stalled)
        assertFalse(
            watch.over(PlaybackWatch.STALL * 2) { waiting(loads = 9, position = (it * 10).milliseconds) }.stalled,
        )
        assertFalse(watch.over(PlaybackWatch.STALL * 2) { waiting(loads = 9, playWhenReady = false) }.stalled)
        assertFalse(watch.over(PlaybackWatch.STALL * 2) { waiting(loads = 9, offline = true) }.stalled)
        // Playing (not waiting) never counts.
        val playing = PlaybackWatch.Sample(Duration.ZERO, false, true, 9, 0)
        assertFalse(watch.over(PlaybackWatch.STALL * 2) { playing }.stalled)
    }

    @Test
    fun `a reset starts the count again`() {
        val watch = PlaybackWatch()
        watch.over(PlaybackWatch.STALL - 1.seconds) { waiting(loads = 2) }
        watch.reset()
        assertFalse(watch.over(PlaybackWatch.STALL - 1.seconds) { waiting(loads = 0) }.stalled)
    }
}
