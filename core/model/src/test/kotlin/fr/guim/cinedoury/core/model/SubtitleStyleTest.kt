package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleStyleTest {
    @Test
    fun stepsGoFromSixtyToTwoHundredPercent() {
        var style = SubtitleStyle()
        repeat(20) { style = style.step(forward = true) }
        assertEquals(200, SubtitleScale.percent(style.scale))
        repeat(20) { style = style.step(forward = false) }
        assertEquals(60, SubtitleScale.percent(style.scale))
        assertEquals(1.2f, SubtitleStyle().step(forward = true).scale)
        assertEquals(0.9f, SubtitleStyle().step(forward = false).scale)
    }

    @Test
    fun aValueBetweenStepsSnapsToTheNext() {
        assertEquals(1.2f, SubtitleScale.step(1.1f, forward = true))
        assertEquals(1f, SubtitleScale.step(1.1f, forward = false))
    }

    @Test
    fun theTextIsAPartOfTheHeightOfThePicture() {
        // The same fraction at any resolution: 1080 px or 2160 px high, 160 % is 8.5 % of it.
        assertEquals(0.08528f, SubtitleStyle(scale = 1.6f).textFraction, 1e-5f)
    }

    @Test
    fun aPictureGrowsAroundTheMiddleOfItsBottom() {
        val box = SubtitleBox(left = 0.3f, top = 0.8f, width = 0.4f, height = 0.1f)
        val scaled = box.scaled(1.5f)
        assertEquals(0.6f, scaled.width, 1e-5f)
        assertEquals(0.15f, scaled.height, 1e-5f)
        assertEquals(0.2f, scaled.left, 1e-5f)
        assertEquals(0.75f, scaled.top, 1e-5f)
    }

    @Test
    fun aPictureStaysOnThePictureAndMovesUp() {
        val wide = SubtitleBox(left = 0.05f, top = 0.85f, width = 0.9f, height = 0.1f).scaled(2f)
        assertEquals(1f, wide.width, 1e-5f)
        assertEquals(0f, wide.left, 1e-5f)
        assertEquals(0.75f, wide.top, 1e-5f)
        val raised = SubtitleBox(0.3f, 0.8f, 0.4f, 0.1f).scaled(1f, raise = SubtitlePosition.High.raise)
        assertEquals(0.63f, raised.top, 1e-5f)
    }
}
