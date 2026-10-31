package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** N (`SPEC.md` § 4.29): the banner only for failed loads. */
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
    }

    @Test
    fun `failing loads show the banner after 3 s of waiting, and a load that succeeds hides it`() {
        val watch = PlaybackWatch()
        assertFalse(watch.over(2.seconds) { waiting(loads = 3, failing = 1) }.reconnecting)
        assertTrue(watch.over(1.seconds) { waiting(loads = 3, failing = 2) }.reconnecting)
        assertFalse(watch.sample(waiting(loads = 4, failing = 0)).reconnecting)
    }
}
