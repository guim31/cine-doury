package fr.guim.cinedoury.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayModesTest {
    private val m60 = ScreenMode(1, 3840, 2160, 60f)
    private val m5994 = ScreenMode(2, 3840, 2160, 59.94f)
    private val m24 = ScreenMode(3, 3840, 2160, 24f)
    private val m23976 = ScreenMode(4, 3840, 2160, 23.976f)
    private val m50 = ScreenMode(5, 3840, 2160, 50f)
    private val hd24 = ScreenMode(6, 1920, 1080, 23.976f)

    @Test
    fun `a film in 23,976p switches a 60 Hz screen to 23,976 Hz of the same size`() {
        assertEquals(m23976, DisplayModes.choose(listOf(m60, m5994, m24, m23976, m50, hd24), m60, 23.976f))
    }

    @Test
    fun `25p goes to 50 Hz, its double, when there is no 25 Hz`() {
        assertEquals(m50, DisplayModes.choose(listOf(m60, m24, m50), m60, 25f))
    }

    @Test
    fun `nothing changes when the screen already fits, or nothing fits`() {
        assertNull(DisplayModes.choose(listOf(m60, m5994), m5994, 29.97f))
        assertNull(DisplayModes.choose(listOf(m60), m60, 23.976f))
        assertNull(DisplayModes.choose(listOf(m60, m24), m60, 0f))
    }

    private val hd60 = ScreenMode(7, 1920, 1080, 60f)

    @Test
    fun `resolution matching - a 1080p film on a 4K screen takes the 1080p mode at its cadence`() {
        val modes = listOf(m60, m23976, hd24, hd60)
        assertEquals(hd24, DisplayModes.choose(modes, m60, 23.976f, videoHeight = 1080))
        // Without frame rate matching, the 1080p mode closest to the current refresh rate.
        assertEquals(hd60, DisplayModes.choose(modes, m60, 0f, videoHeight = 1080))
    }

    @Test
    fun `resolution matching - a 4K film stays in 4K, a 720p film takes the smallest mode that holds it`() {
        val modes = listOf(m60, m23976, hd24, hd60)
        assertNull(DisplayModes.choose(modes, m60, 0f, videoHeight = 2160))
        assertEquals(m23976, DisplayModes.choose(modes, m60, 23.976f, videoHeight = 1608))
        assertEquals(hd60, DisplayModes.choose(modes, m60, 0f, videoHeight = 720))
    }
}
