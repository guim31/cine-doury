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
}
