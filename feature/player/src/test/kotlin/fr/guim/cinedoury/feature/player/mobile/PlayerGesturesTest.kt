package fr.guim.cinedoury.feature.player.mobile

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class PlayerGesturesTest {
    private val width = 900f
    private var toggles = 0
    private var lockShown = 0
    private val jumps = mutableListOf<Duration>()
    private val seeks = mutableListOf<Duration>()
    private val fills = mutableListOf<Boolean>()
    private var brightness = 0.5f
    private var volume = 0.5f

    private val gestures =
        PlayerGestures(
            onToggleControls = { toggles++ },
            onShowLock = { lockShown++ },
            onJump = { jumps += it },
            onFill = { fills += it },
            onSeek = { seeks += it },
            setBrightness = { brightness = it },
            setVolume = { volume = it },
        ).apply {
            position = { 10.minutes }
            duration = { 100.minutes }
            brightness = { this@PlayerGesturesTest.brightness }
            volume = { this@PlayerGesturesTest.volume }
        }

    private fun Int.ms() = milliseconds

    @Test
    fun `a single tap shows the controls once the time of a double tap has passed`() {
        val decide = assertNotNull(gestures.tap(450f, width, 0.ms()))
        gestures.decideTap(decide)
        assertEquals(1, toggles)
        assertTrue(jumps.isEmpty())
    }

    @Test
    fun `two taps on the left jump 10 s back, and each further tap 10 s more`() {
        assertNotNull(gestures.tap(100f, width, 0.ms()))
        assertNull(gestures.tap(100f, width, 200.ms()))
        assertEquals(10, gestures.burst?.seconds)
        assertNull(gestures.tap(100f, width, 500.ms()))
        assertEquals(20, gestures.burst?.seconds)
        assertNull(gestures.tap(100f, width, 800.ms()))
        assertEquals(30, gestures.burst?.seconds, "« −30 s »")
        gestures.decideTap(1000.ms())
        assertEquals(0, toggles, "the first tap was part of the jump")
        assertTrue(!gestures.commit(1000.ms()), "still within the burst")
        assertTrue(gestures.commit(1600.ms()))
        assertEquals(listOf(9.minutes + 30_000.ms()), jumps)
    }

    @Test
    fun `two taps on the right jump ahead, two in the middle only show the controls`() {
        gestures.tap(850f, width, 0.ms())
        gestures.tap(850f, width, 200.ms())
        gestures.commit(1000.ms())
        assertEquals(listOf(10.minutes + 10_000.ms()), jumps)
        gestures.tap(450f, width, 2000.ms())
        val decide = assertNotNull(gestures.tap(450f, width, 2200.ms()))
        gestures.decideTap(decide)
        assertEquals(1, toggles)
    }

    @Test
    fun `slides change the brightness on the left, the volume on the right, the position sideways`() {
        gestures.startSlide(Offset(100f, 300f), width, vertical = true)
        gestures.moveSlide(0f, -0.3f)
        gestures.endSlide()
        assertEquals(0.8f, brightness, 0.001f)
        gestures.startSlide(Offset(800f, 300f), width, vertical = true)
        gestures.moveSlide(0f, 0.2f)
        gestures.endSlide()
        assertEquals(0.3f, volume, 0.001f)
        gestures.startSlide(Offset(400f, 300f), width, vertical = false)
        gestures.moveSlide(0.5f, 0f)
        assertEquals(Slide.Seek(12.minutes + 30_000.ms()), gestures.slide)
        gestures.endSlide()
        assertEquals(listOf(12.minutes + 30_000.ms()), seeks)
    }

    @Test
    fun `a pinch out fills the screen, a pinch in comes back to the format of the film`() {
        gestures.pinch(1.3f)
        gestures.endPinch()
        gestures.pinch(0.7f)
        gestures.endPinch()
        gestures.pinch(1.05f)
        gestures.endPinch()
        assertEquals(listOf(true, false), fills)
    }

    @Test
    fun `locked, a tap shows the padlock and nothing else moves`() {
        gestures.locked = true
        assertNull(gestures.tap(100f, width, 0.ms()))
        assertNull(gestures.tap(100f, width, 100.ms()))
        gestures.startSlide(Offset(100f, 300f), width, vertical = true)
        gestures.moveSlide(0f, -0.5f)
        gestures.pinch(2f)
        gestures.endPinch()
        assertEquals(2, lockShown)
        assertEquals(0.5f, brightness)
        assertTrue(jumps.isEmpty() && fills.isEmpty() && toggles == 0)
    }
}
