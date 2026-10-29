package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlaybackRulesTest {
    private val runtime = 42.minutes
    private val outro = MediaSegment(SegmentType.Outro, 40.minutes, 42.minutes)
    private val intro = MediaSegment(SegmentType.Intro, 1.minutes, 2.minutes)

    @Test
    fun `watched at 90 percent, or from the start of the credits`() {
        assertFalse(PlaybackRules.isWatched(37.minutes, runtime))
        assertTrue(PlaybackRules.isWatched(37.8.minutes, runtime))
        assertTrue(PlaybackRules.isWatched(40.minutes, runtime, listOf(outro), watchedThreshold = 0.99f))
        assertFalse(PlaybackRules.isWatched(39.minutes, runtime, listOf(outro), watchedThreshold = 0.99f))
        assertTrue(PlaybackRules.isWatched(34.minutes, runtime, watchedThreshold = 0.8f))
        assertFalse(PlaybackRules.isWatched(10.minutes, kotlin.time.Duration.ZERO))
    }

    @Test
    fun `the next episode shows during the credits, else the last 30 seconds`() {
        assertTrue(PlaybackRules.showsNext(40.minutes, runtime, listOf(outro), hasNext = true))
        assertFalse(PlaybackRules.showsNext(39.minutes, runtime, listOf(outro), hasNext = true))
        assertFalse(PlaybackRules.showsNext(41.minutes, runtime, listOf(outro), hasNext = false))
        assertFalse(PlaybackRules.showsNext(41.minutes, runtime, emptyList(), hasNext = true))
        assertTrue(PlaybackRules.showsNext(runtime - 20.seconds, runtime, emptyList(), hasNext = true))
        assertEquals(runtime - 30.seconds, PlaybackRules.creditsStart(runtime, listOf(intro)))
    }

    @Test
    fun `with credits, the countdown runs from their start, then starts the next episode`() {
        val countdown = 10.seconds
        assertEquals(countdown, PlaybackRules.countdownLeft(40.minutes, runtime, listOf(outro), ZERO, countdown))
        assertEquals(4.seconds, PlaybackRules.countdownLeft(40.minutes, runtime, listOf(outro), 6.seconds, countdown))
        assertEquals(ZERO, PlaybackRules.countdownLeft(40.minutes, runtime, listOf(outro), 12.seconds, countdown))
        assertTrue(PlaybackRules.countdownStartsNext(listOf(intro, outro)))
    }

    @Test
    fun `without credits, the countdown never ends before the end of the episode`() {
        val countdown = 10.seconds
        // The card shows for the last 30 s; the countdown waits until exactly 10 s are left.
        assertNull(PlaybackRules.countdownLeft(runtime - 30.seconds, runtime, listOf(intro), 20.seconds, countdown))
        assertNull(PlaybackRules.countdownLeft(runtime - 11.seconds, runtime, emptyList(), 19.seconds, countdown))
        assertEquals(
            countdown,
            PlaybackRules.countdownLeft(runtime - 10.seconds, runtime, emptyList(), 20.seconds, countdown),
        )
        // Whatever time has passed with the card shown (a pause, a jump back): what is left of the episode.
        assertEquals(
            3.seconds,
            PlaybackRules.countdownLeft(runtime - 3.seconds, runtime, emptyList(), 2.minutes, countdown),
        )
        assertEquals(ZERO, PlaybackRules.countdownLeft(runtime, runtime, emptyList(), 2.minutes, countdown))
        // A countdown longer than the card: it starts with the card, still at the end.
        assertEquals(
            30.seconds,
            PlaybackRules.countdownLeft(runtime - 30.seconds, runtime, emptyList(), ZERO, 45.seconds),
        )
        // Its end starts nothing: the end of the episode does.
        assertFalse(PlaybackRules.countdownStartsNext(listOf(intro)))
    }

    @Test
    fun `the intro is skippable while it plays, the credits are not`() {
        assertEquals(intro, PlaybackRules.skippableAt(90.seconds, listOf(intro, outro)))
        assertNull(PlaybackRules.skippableAt(2.minutes, listOf(intro, outro)))
        assertNull(PlaybackRules.skippableAt(41.minutes, listOf(intro, outro)))
    }

    @Test
    fun `still watching asks after three episodes in a row without a key`() {
        val still = StillWatching()
        assertTrue(still.mayAutoplayNext()) // 2nd episode
        assertTrue(still.mayAutoplayNext()) // 3rd
        assertFalse(still.mayAutoplayNext()) // asks before the 4th
        still.onInteraction()
        assertTrue(still.mayAutoplayNext())
        val never = StillWatching(limit = 0)
        repeat(10) { assertTrue(never.mayAutoplayNext()) }
    }

    @Test
    fun `a decoder or format failure in direct play or remux falls back to transcoding, once`() {
        assertEquals(FallbackAction.Transcode, PlaybackFallback.next(PlayMethod.DirectPlay, PlaybackFailure.Decoder))
        assertEquals(FallbackAction.Transcode, PlaybackFallback.next(PlayMethod.DirectStream, PlaybackFailure.Format))
        assertEquals(FallbackAction.ShowError, PlaybackFallback.next(PlayMethod.Transcode, PlaybackFailure.Decoder))
        assertEquals(FallbackAction.ShowError, PlaybackFallback.next(PlayMethod.DirectPlay, PlaybackFailure.Network))
    }

    @Test
    fun `the trickplay picture of a position`() {
        val info = TrickplayInfo(320, 180, 10, 10, 250, 10.seconds)
        assertEquals(TrickplayFrame(0, 0, 0), info.frameAt(5.seconds))
        assertEquals(TrickplayFrame(0, 3, 1), info.frameAt(130.seconds))
        assertEquals(TrickplayFrame(1, 0, 0), info.frameAt(1000.seconds))
        assertEquals(TrickplayFrame(2, 9, 4), info.frameAt(10_000.seconds))
        assertNull(info.copy(interval = 0.milliseconds).frameAt(1.seconds))
    }

    @Test
    fun `transcode reasons read in either case, and the audio ones`() {
        assertEquals(TranscodeReason.VideoCodecNotSupported, TranscodeReason.of("videocodecnotsupported"))
        assertTrue(TranscodeReason.AudioChannelsNotSupported.isAudio)
        assertFalse(TranscodeReason.ContainerNotSupported.isAudio)
    }
}
