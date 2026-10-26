package org.jetbrains.kotlinconf.media

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The colours of a photographed slide: casts are removed, and dark slides stay dark rather than turning orange. */
class ScreenLevelsTest {
    /** A slide of [background] with [textShare] of its pixels in [text], with a little sensor noise. */
    private fun slide(background: Rgb, text: Rgb, textShare: Double): List<Rgb> {
        val random = Random(21)
        fun noisy(c: Double) = (c * (1 + (random.nextDouble() - 0.5) * 0.3)).coerceAtLeast(0.0)
        val count = 256 * 144
        return List(count) { i ->
            val base = if (i < count * textShare) text else background
            Rgb(noisy(base.r), noisy(base.g), noisy(base.b))
        }
    }

    private fun average(pixels: List<Rgb>) =
        Rgb(pixels.sumOf { it.r } / pixels.size, pixels.sumOf { it.g } / pixels.size, pixels.sumOf { it.b } / pixels.size)

    private fun spread(pixel: Rgb) = maxOf(pixel.r, pixel.g, pixel.b) - minOf(pixel.r, pixel.g, pixel.b)

    // A dark slide under a warm projector: the black is lifted and tinted, the white text is yellowish
    private val darkBackground = Rgb(0.05, 0.04, 0.03)
    private val warmText = Rgb(0.75, 0.6, 0.4)

    private fun assertStaysDark(textShare: Double, background: Rgb = darkBackground, text: Rgb = warmText) =
        assertStaysDark(slide(background, text, textShare), textShare)

    private fun assertStaysDark(pixels: List<Rgb>, textShare: Double) {
        val levels = assertNotNull(measureScreenLevels(pixels))
        val text = pixels.take((pixels.size * textShare).toInt())
        val background = pixels.drop((pixels.size * textShare).toInt() + 1)
        val backgroundAfter = average(background.map(levels::apply))
        val textAfter = average(text.map(levels::apply))

        assertFalse(levels.light, "a dark slide is not treated as a light one")
        assertTrue(backgroundAfter.luminance < 0.08, "background stays dark: ${backgroundAfter.describe()}")
        // In linear light: (0.03, 0.003, 0) already shows as a dark orange
        assertTrue(spread(backgroundAfter) < 0.015, "background is not coloured: ${backgroundAfter.describe()}")
        assertTrue(textAfter.luminance > 0.6, "text is light: ${textAfter.describe()}")
        assertTrue(spread(textAfter) < 0.15, "text is close to white: ${textAfter.describe()}")
    }

    @Test
    fun aDarkSlideWithLittleTextStaysDark() = assertStaysDark(0.01)

    @Test
    fun aDarkSlideWithSomeTextStaysDark() = assertStaysDark(0.05)

    @Test
    fun aDarkSlideWithMuchTextStaysDark() = assertStaysDark(0.15)

    // The room's warm light falls on the dark screen, the text is the projector's own, near-white light
    private val roomLitBackground = Rgb(0.05, 0.03, 0.015)
    private val projectorText = Rgb(0.7, 0.68, 0.65)

    @Test
    fun aDarkSlideLitByTheRoomIsNotOrange() = assertStaysDark(0.05, roomLitBackground, projectorText)

    @Test
    fun aDarkSlideLitByTheRoomWithLittleTextIsNotOrange() = assertStaysDark(0.01, roomLitBackground, projectorText)

    @Test
    fun aLightSlideLosesItsCastAndBecomesWhite() {
        val pixels = slide(Rgb(0.55, 0.5, 0.38), Rgb(0.05, 0.045, 0.035), 0.08)
        val levels = assertNotNull(measureScreenLevels(pixels))
        val backgroundAfter = average(pixels.drop(pixels.size / 10).map(levels::apply))
        val textAfter = average(pixels.take(pixels.size * 8 / 100).map(levels::apply))

        assertTrue(levels.light)
        assertTrue(backgroundAfter.luminance > 0.8, "background is white: ${backgroundAfter.describe()}")
        assertTrue(spread(backgroundAfter) < 0.08, "background is not tinted: ${backgroundAfter.describe()}")
        assertTrue(textAfter.luminance < 0.05, "text is dark: ${textAfter.describe()}")
    }

    @Test
    fun aDimSlideKeepsItsBalanceWhenTheGainIsLimited() {
        // A dim, warm photo: blue needs far more than the largest gain. Limiting blue alone would turn it orange.
        val text = Rgb(0.15, 0.12, 0.06)
        val levels = assertNotNull(measureScreenLevels(slide(Rgb(0.015, 0.012, 0.006), text, 0.05)))
        // The cast is 2.5; the white is measured on noisy pixels, so a little less
        val ratio = levels.gains[2] / levels.gains[0]
        assertTrue(ratio in 2.0..text.r / text.b, "blue is raised as much as the cast needs: $ratio")
    }

    private fun Rgb.describe() = "($r, $g, $b)"
}
