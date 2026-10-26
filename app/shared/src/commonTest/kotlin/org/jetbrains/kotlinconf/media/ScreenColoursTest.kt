package org.jetbrains.kotlinconf.media

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The colour cast of a photographed slide is measured on its white, whether that is the background or the text. */
class ScreenColoursTest {
    private fun slide(background: Rgb, text: Rgb, textShare: Double): List<Rgb> {
        val count = 10_000
        val textPixels = (count * textShare).toInt()
        return List(count) { if (it < textPixels) text else background }
    }

    private fun assertNeutral(pixel: Rgb, message: String) {
        val spread = maxOf(abs(pixel.r - pixel.g), abs(pixel.g - pixel.b), abs(pixel.r - pixel.b))
        assertTrue(spread < 0.05, "$message: ${pixel.r}, ${pixel.g}, ${pixel.b}")
    }

    @Test
    fun darkSlideWithLittleTextStaysDarkAndBlue() {
        // White text on a navy slide, the text a twentieth of the photo: the navy is not the white
        val navy = Rgb(0.02, 0.03, 0.06)
        val white = Rgb(0.6, 0.6, 0.62)
        val levels = assertNotNull(levels(slide(navy, white, textShare = 0.05)))
        val text = levels.apply(white)
        val background = levels.apply(navy)
        assertNeutral(text, "the text stays white, not orange")
        assertTrue(text.luminance > 0.8, "the text is bright")
        assertTrue(background.b > background.r, "the background stays blue")
        assertTrue(background.luminance < 0.1, "the background stays dark")
        assertTrue(levels.median < 0.3, "it counts as a dark slide")
    }

    @Test
    fun castOnADarkSlideIsTakenFromTheText() {
        // A warm projector: the white text comes out yellowish and is made white again
        val warmWhite = Rgb(0.6, 0.5, 0.3)
        val levels = assertNotNull(levels(slide(Rgb(0.02, 0.02, 0.015), warmWhite, textShare = 0.03)))
        assertNeutral(levels.apply(warmWhite), "the text is white")
    }

    @Test
    fun castOnALightSlideIsTakenFromTheBackground() {
        val bluishWhite = Rgb(0.5, 0.55, 0.7)
        val levels = assertNotNull(levels(slide(bluishWhite, Rgb(0.03, 0.03, 0.04), textShare = 0.1)))
        val background = levels.apply(bluishWhite)
        assertNeutral(background, "the background is white")
        assertTrue(levels.median > 0.3, "it counts as a light slide")
    }

    @Test
    fun aBlackPhotoIsLeftAlone() {
        assertTrue(levels(List(100) { Rgb(0.005, 0.004, 0.01) }) == null)
    }
}
