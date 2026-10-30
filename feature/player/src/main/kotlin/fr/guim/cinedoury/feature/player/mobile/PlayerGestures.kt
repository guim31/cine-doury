package fr.guim.cinedoury.feature.player.mobile

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Which side of the picture a gesture started on. */
enum class Side { Left, Middle, Right }

/** The jumps of a double tap, cumulated while the taps follow each other: « −30 s » after three. */
data class JumpBurst(
    val side: Side,
    val seconds: Int,
    val from: Duration,
)

/** What a slide of the finger changes while it moves. */
sealed interface Slide {
    /** Up and down on the left: the brightness of the window, from 0 to 1. */
    data class Brightness(
        val level: Float,
    ) : Slide

    /** Up and down on the right: the volume of the media, from 0 to 1. */
    data class Volume(
        val level: Float,
    ) : Slide

    /** Sideways: the search in the film, [to] the time the film would go to. */
    data class Seek(
        val to: Duration,
    ) : Slide
}

/**
 * The rules of the gestures of the touch player (M2, `UI.md` § 9), apart from Android so that they are
 * tested on their own:
 * - a tap shows or hides the controls; two quick taps on the left or the right third jump 10 s back or
 *   ahead, each further tap on the same side 10 s more (« −30 s » after a double tap and one more), and
 *   the jump happens once the taps stop ([commit]);
 * - a slide up or down changes the brightness on the left half, the volume on the right half;
 * - a slide sideways searches in the film: the whole width is [SEEK_SPAN] (or the film when shorter);
 * - a pinch out fills the screen, a pinch in comes back to the format of the film;
 * - [locked]: nothing but a tap, which shows the padlock.
 */
@Stable
@Suppress("LongParameterList") // One callback for each thing a gesture changes.
class PlayerGestures(
    private val onToggleControls: () -> Unit,
    private val onShowLock: () -> Unit,
    private val onJump: (Duration) -> Unit,
    private val onFill: (Boolean) -> Unit,
    private val onSeek: (Duration) -> Unit,
    private val setBrightness: (Float) -> Unit,
    private val setVolume: (Float) -> Unit,
) {
    var locked by mutableStateOf(false)
    var burst by mutableStateOf<JumpBurst?>(null)
        private set
    var slide by mutableStateOf<Slide?>(null)
        private set

    /** Where playback is, how long the film is, and the levels when a slide starts. */
    var position: () -> Duration = { Duration.ZERO }
    var duration: () -> Duration = { Duration.ZERO }
    var brightness: () -> Float = { DEFAULT_LEVEL }
    var volume: () -> Float = { DEFAULT_LEVEL }

    private var pendingTap: Pair<Side, Duration>? = null
    private var lastTap = Duration.ZERO

    /**
     * A tap at [x] of a picture [width] wide, at [now]. Returns when the single tap must be decided
     * (after [DOUBLE_TAP], unless a second tap comes), or null when it is part of a jump.
     */
    @Suppress("ReturnCount") // A tap of a jump, the second of a double tap, or a single one.
    fun tap(
        x: Float,
        width: Float,
        now: Duration,
    ): Duration? {
        if (locked) {
            onShowLock()
            return null
        }
        val side = side(x, width)
        val current = burst
        if (current != null && current.side == side && now - lastTap <= BURST) {
            burst = current.copy(seconds = current.seconds + STEP_SECONDS)
            lastTap = now
            return null
        }
        val pending = pendingTap
        val double = pending != null && pending.first == side && now - pending.second <= DOUBLE_TAP
        if (double && side != Side.Middle) {
            pendingTap = null
            burst = JumpBurst(side, STEP_SECONDS, position())
            lastTap = now
            return null
        }
        pendingTap = side to now
        return now + DOUBLE_TAP
    }

    /** [now] passed the moment returned by [tap]: a single tap, unless it became a double one. */
    fun decideTap(now: Duration) {
        val pending = pendingTap ?: return
        if (now - pending.second < DOUBLE_TAP) return
        pendingTap = null
        onToggleControls()
    }

    /** The taps stopped: the film jumps by all of them at once. */
    fun commit(now: Duration): Boolean {
        val current = burst ?: return true
        if (now - lastTap < BURST) return false
        burst = null
        val step = current.seconds.seconds
        val target = if (current.side == Side.Left) current.from - step else current.from + step
        onJump(target.coerceIn(Duration.ZERO, duration().takeIf { it.isPositive() } ?: target))
        return true
    }

    private var startLevel = DEFAULT_LEVEL
    private var startPosition = Duration.ZERO

    /** A slide starts from [start], vertical or not. */
    fun startSlide(
        start: Offset,
        width: Float,
        vertical: Boolean,
    ) {
        if (locked) return
        slide =
            when {
                !vertical -> {
                    startPosition = position()
                    Slide.Seek(startPosition)
                }

                start.x < width / 2 -> {
                    startLevel = brightness()
                    Slide.Brightness(startLevel)
                }

                else -> {
                    startLevel = volume()
                    Slide.Volume(startLevel)
                }
            }
    }

    /** The finger moved by [dx] and [dy], as parts of the width and the height of the picture. */
    fun moveSlide(
        dx: Float,
        dy: Float,
    ) {
        slide =
            when (val current = slide) {
                is Slide.Brightness -> {
                    Slide.Brightness((current.level - dy).coerceIn(0f, 1f)).also { setBrightness(it.level) }
                }

                is Slide.Volume -> {
                    Slide.Volume((current.level - dy).coerceIn(0f, 1f)).also { setVolume(it.level) }
                }

                is Slide.Seek -> {
                    val span = duration().takeIf { it.isPositive() && it < SEEK_SPAN } ?: SEEK_SPAN
                    val end = duration().takeIf { it.isPositive() } ?: Duration.INFINITE
                    Slide.Seek((current.to + span * dx.toDouble()).coerceIn(Duration.ZERO, end))
                }

                null -> {
                    null
                }
            }
    }

    fun endSlide() {
        (slide as? Slide.Seek)?.let { onSeek(it.to) }
        slide = null
    }

    private var pinch = 1f

    fun pinch(zoom: Float) {
        if (locked) return
        pinch *= zoom
    }

    fun endPinch() {
        if (pinch > PINCH_OUT) onFill(true)
        if (pinch < PINCH_IN) onFill(false)
        pinch = 1f
    }

    companion object {
        const val STEP_SECONDS = 10
        val DOUBLE_TAP = 300.milliseconds

        /** The taps of a jump may be this far apart. */
        val BURST = 700.milliseconds
        val SEEK_SPAN = 5.minutes
        private const val PINCH_OUT = 1.15f
        private const val PINCH_IN = 0.87f
        private const val DEFAULT_LEVEL = 0.5f
        private const val SIDE_PART = 3

        fun side(
            x: Float,
            width: Float,
        ): Side =
            when {
                x < width / SIDE_PART -> Side.Left
                x > width * (SIDE_PART - 1) / SIDE_PART -> Side.Right
                else -> Side.Middle
            }
    }
}

/** What a touch of the picture turned out to be. */
private enum class Touch { Undecided, Vertical, Sideways, Pinch, Cancelled }

/**
 * The pointer side of [PlayerGestures]: taps, slides (their direction set once the finger has moved
 * past the slop) and pinches (two fingers). What a child consumed (a button, the bar) is not a gesture
 * of the picture. [now] is a monotonic clock.
 */
@Suppress("CyclomaticComplexMethod") // One loop for every kind of touch, so that they exclude each other.
fun Modifier.playerGestures(
    gestures: PlayerGestures,
    now: () -> Duration,
    onTapPending: (Duration) -> Unit,
): Modifier =
    pointerInput(gestures) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var moved = Offset.Zero
            var touch = Touch.Undecided
            var pressed = true
            while (pressed && touch != Touch.Cancelled) {
                val event = awaitPointerEvent()
                val fingers = event.changes.filter { it.pressed }
                pressed = fingers.isNotEmpty()
                touch =
                    when {
                        !pressed && touch == Touch.Undecided && event.changes.any { it.isConsumed } -> {
                            Touch.Cancelled
                        }

                        !pressed -> {
                            touch
                        }

                        fingers.size > 1 -> {
                            val sliding = touch == Touch.Vertical || touch == Touch.Sideways
                            if (sliding) gestures.endSlide()
                            gestures.pinch(event.calculateZoom())
                            event.changes.forEach { it.consume() }
                            Touch.Pinch
                        }

                        fingers.first().isConsumed -> {
                            Touch.Cancelled
                        }

                        else -> {
                            slide(
                                gestures,
                                fingers.first(),
                                down.position,
                                touch,
                                moved,
                                size.width,
                                size.height,
                                viewConfiguration.touchSlop,
                            ).also { moved += fingers.first().positionChange() }
                        }
                    }
            }
            when (touch) {
                Touch.Undecided -> gestures.tap(down.position.x, size.width.toFloat(), now())?.let(onTapPending)
                Touch.Pinch -> gestures.endPinch()
                Touch.Vertical, Touch.Sideways -> gestures.endSlide()
                Touch.Cancelled -> Unit
            }
        }
    }

/** One move of one finger: the direction of the slide once past the slop, then the slide itself. */
@Suppress("LongParameterList") // What one move needs to know of the touch so far.
private fun slide(
    gestures: PlayerGestures,
    change: PointerInputChange,
    start: Offset,
    touch: Touch,
    moved: Offset,
    width: Int,
    height: Int,
    slop: Float,
): Touch {
    val delta = change.positionChange()
    val total = moved + delta
    var next = touch
    if (touch == Touch.Undecided && total.getDistance() > slop) {
        val vertical = abs(total.y) > abs(total.x)
        next = if (vertical) Touch.Vertical else Touch.Sideways
        gestures.startSlide(start, width.toFloat(), vertical)
    }
    if (next == Touch.Vertical || next == Touch.Sideways) {
        gestures.moveSlide(delta.x / width, delta.y / height)
        change.consume()
    }
    return next
}
