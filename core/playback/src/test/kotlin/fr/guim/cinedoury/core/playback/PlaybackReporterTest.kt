package fr.guim.cinedoury.core.playback

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackReport
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.source.fake.InMemoryPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackReporterTest {
    private var position = Duration.ZERO
    private var paused = false

    private fun report() =
        PlaybackReport(ItemId("film"), "source", "session", position, paused, PlayMethod.DirectPlay, 1, null)

    @Test
    fun `start, progress every 10 seconds, at a pause and after a jump, then stopped`() =
        runTest {
            val playback = InMemoryPlayback()
            val reporter = PlaybackReporter(playback, backgroundScope) { report() }
            reporter.start()
            runCurrent()
            position = 10.seconds
            advanceTimeBy(10_001)
            position = 15.seconds
            paused = true
            reporter.progress()
            runCurrent()
            // The pause starts the 10 s again.
            advanceTimeBy(9_000)
            assertEquals(3, playback.reports.size)
            advanceTimeBy(1_001)
            position = 600.seconds
            paused = false
            reporter.progress()
            reporter.stop()
            runCurrent()
            assertEquals(
                listOf("start", "progress", "progress", "progress", "progress", "stopped"),
                playback.reports.map { it.first },
            )
            assertEquals(
                listOf(0, 10, 15, 15, 600, 600),
                playback.reports.map {
                    it.second.position.inWholeSeconds
                        .toInt()
                },
            )
            assertEquals(true, playback.reports[2].second.paused)
            // Nothing after « stopped ».
            advanceTimeBy(60_000)
            reporter.progress()
            runCurrent()
            assertEquals(6, playback.reports.size)
        }

    @Test
    fun `a server that does not answer never stops the reports`() =
        runTest {
            val playback = InMemoryPlayback()
            val reporter = PlaybackReporter(playback, backgroundScope) { report() }
            playback.failure = SourceException.Unreachable()
            reporter.start()
            advanceTimeBy(20_001)
            playback.failure = null
            reporter.stop()
            runCurrent()
            assertEquals(listOf("stopped"), playback.reports.map { it.first })
        }

    @Test
    fun `a new stream stops the old one and starts again`() =
        runTest {
            val playback = InMemoryPlayback()
            val reporter = PlaybackReporter(playback, backgroundScope) { report() }
            reporter.start()
            reporter.restart()
            reporter.start()
            reporter.stop()
            runCurrent()
            assertEquals(listOf("start", "stopped", "start", "stopped"), playback.reports.map { it.first })
        }
}
