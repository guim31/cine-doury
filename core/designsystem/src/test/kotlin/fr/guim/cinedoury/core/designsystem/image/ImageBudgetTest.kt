package fr.guim.cinedoury.core.designsystem.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageBudgetTest {
    @Test
    fun aDeviceWithRoomKeepsAQuarterOfItsHeapAndLoadsTheNextRowAhead() {
        val shield = ImageBudget.of(lowRam = false, memoryClassMegabytes = 384)
        assertEquals(96L * MB, shield.memoryCacheBytes)
        assertEquals(256L * MB, shield.diskCacheBytes)
        assertEquals(6, shield.prefetch)
    }

    @Test
    fun aSmallHeapGetsLessAndLoadsFewerCardsAhead() {
        val small = ImageBudget.of(lowRam = false, memoryClassMegabytes = 128)
        val shield = ImageBudget.of(lowRam = false, memoryClassMegabytes = 384)
        assertTrue(small.memoryCacheBytes < shield.memoryCacheBytes)
        assertTrue(small.diskCacheBytes < shield.diskCacheBytes)
        assertEquals(3, small.prefetch)
    }

    @Test
    fun aLowRamDeviceLoadsNothingAhead() {
        val lowRam = ImageBudget.of(lowRam = true, memoryClassMegabytes = 256)
        assertEquals(0, lowRam.prefetch)
        assertEquals((256 * MB * 0.15).toLong(), lowRam.memoryCacheBytes)
        assertEquals(64L * MB, lowRam.diskCacheBytes)
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}
