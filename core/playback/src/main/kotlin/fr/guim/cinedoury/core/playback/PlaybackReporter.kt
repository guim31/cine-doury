package fr.guim.cinedoury.core.playback

import fr.guim.cinedoury.core.model.PlaybackReport
import fr.guim.cinedoury.core.source.PlaybackSource
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The reports of the player to the server (`SPEC.md` § 4.11): « started » once, « progress » every
 * 10 s, at each pause or resume and after each jump (the 10 s start again from there), « stopped »
 * at the end. They leave in order, one at a time; a failure is ignored (playback never waits for
 * the server), and the next report carries the position anyway.
 *
 * @param scope where the reports run: it must outlive the screen, for « stopped » to leave.
 * @param snapshot where playback is now, read on the thread of [scope].
 */
class PlaybackReporter(
    private val source: PlaybackSource,
    private val scope: CoroutineScope,
    private val interval: Duration = INTERVAL,
    private val snapshot: () -> PlaybackReport,
) {
    private enum class Kind { Start, Progress, Stopped }

    private val queue = Channel<Pair<Kind, PlaybackReport>>(Channel.UNLIMITED)
    private var ticker: Job? = null
    private var started = false
    private var stopped = false

    init {
        scope.launch {
            for ((kind, report) in queue) send(kind, report)
        }
    }

    private suspend fun send(
        kind: Kind,
        report: PlaybackReport,
    ) {
        try {
            when (kind) {
                Kind.Start -> source.reportStart(report)
                Kind.Progress -> source.reportProgress(report)
                Kind.Stopped -> source.reportStopped(report)
            }
        } catch (_: SourceException) {
            // The server will have the next report; playback goes on.
        }
    }

    /** Playback began (or began again on a new stream, after a change of quality or a fallback). */
    fun start() {
        if (stopped) return
        started = true
        queue.trySend(Kind.Start to snapshot())
        restartTicker()
    }

    /** A pause, a resume, a jump: the position now, and the 10 s from now. */
    fun progress() {
        if (!started || stopped) return
        queue.trySend(Kind.Progress to snapshot())
        restartTicker()
    }

    /** Playback ended: the last report, then nothing. */
    fun stop(final: PlaybackReport = snapshot()) {
        if (!started || stopped) return
        stopped = true
        ticker?.cancel()
        queue.trySend(Kind.Stopped to final)
        queue.close()
    }

    /** The stream is about to change (quality, fallback): « stopped » for this one, the next starts anew. */
    fun restart(final: PlaybackReport = snapshot()) {
        if (!started || stopped) return
        ticker?.cancel()
        queue.trySend(Kind.Stopped to final)
        started = false
    }

    private fun restartTicker() {
        ticker?.cancel()
        ticker =
            scope.launch {
                while (isActive) {
                    delay(interval)
                    queue.trySend(Kind.Progress to snapshot())
                }
            }
    }

    companion object {
        val INTERVAL = 10.seconds
    }
}
