package fr.guim.cinedoury.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals

/** P (`SPEC.md` § 4.29): the HDR of the screen, as its current mode says it from Android 14. */
class ScreenHdrTest {
    private val hdr10 = 2
    private val hlg = 3
    private val dolbyVision = 1

    @Test
    fun `from API 34, a type the mode does not list is not on the screen`() {
        // The Chromecast on a 1080p SDR television: modes `supportedHdrTypes=[]`.
        assertEquals(emptySet(), ScreenHdr.types(intArrayOf(hdr10, hlg), intArrayOf()))
        assertEquals(setOf(hdr10), ScreenHdr.types(intArrayOf(hdr10, hlg), intArrayOf(hdr10)))
    }

    @Test
    fun `before API 34, the list of the display is all there is`() {
        // The Shield, Android 11, on an HDR television.
        assertEquals(setOf(hdr10, hlg, dolbyVision), ScreenHdr.types(intArrayOf(hdr10, hlg, dolbyVision), null))
        assertEquals(emptySet(), ScreenHdr.types(null, null))
    }
}
