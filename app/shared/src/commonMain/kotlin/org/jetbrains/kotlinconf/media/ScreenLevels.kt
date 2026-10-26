package org.jetbrains.kotlinconf.media

/** A colour cast is never stronger than this ratio between channels; past it, the "white" is a coloured shape. */
private const val MAX_CAST = 2.5
private const val MAX_GAIN = 8.0

/** Median brightness (linear) above which a slide counts as light: dark text on a light background. */
private const val LIGHT_MEDIAN = 0.3

/**
 * The brightest channel (linear) of a dark slide's background, once corrected, up to which it counts as a projector's
 * black rather than a colour of the design. A mid blue background is far brighter in blue.
 */
private const val BLACKISH_BACKGROUND = 0.1

/** A pixel in linear light. */
internal class Rgb(val r: Double, val g: Double, val b: Double) {
    val luminance: Double get() = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val saturation: Double get() {
        val max = maxOf(r, g, b)
        return if (max > 0) (max - minOf(r, g, b)) / max else 0.0
    }
}

/**
 * How to correct a photo of a screen: each channel times its [gains], minus its [bias] (as CoreImage's colour matrix
 * does). [light] is whether the result is a light slide, dark text on a light background.
 */
internal class ScreenLevels(val gains: List<Double>, val bias: List<Double>, val light: Boolean) {
    fun apply(pixel: Rgb) = Rgb(
        (pixel.r * gains[0] - bias[0]).coerceIn(0.0, 1.0),
        (pixel.g * gains[1] - bias[1]).coerceIn(0.0, 1.0),
        (pixel.b * gains[2] - bias[2]).coerceIn(0.0, 1.0),
    )
}

/**
 * Removes the colour cast and stretches the levels, measured on [pixels] of a small copy; null leaves the photo as it
 * is. The white of a slide is the least coloured of its brightest pixels (the background of a light slide, the text of
 * a dark one), so its tint is the cast and is taken out per channel. A coloured slide keeps its colours because its
 * white text or frame sets the balance, not its background. Then one black point for all channels, so nothing shifts
 * hue.
 *
 * A dark slide has little text: its brightest 10 % is mostly background, tinted by the projector's black. Only pixels
 * at least half as bright as the brightest text count as white, and the white is never stretched past white, so the
 * background is not mistaken for the white and stays dark instead of turning orange.
 *
 * The projector's black often has a cast of its own, warmer than its white because the room's light falls on the
 * screen, and is brighter where the projector's hot spot is. So the background of a dark slide, if it is nearly black,
 * becomes the black point of each channel: it turns black rather than staying a stretched orange-brown. A clearly
 * coloured background is part of the design and keeps the one black point.
 */
internal fun measureScreenLevels(pixels: List<Rgb>): ScreenLevels? {
    if (pixels.isEmpty()) return null
    val luminances = pixels.map { it.luminance }
    val bright = maxOf(percentile(luminances, 0.9), percentile(luminances, 0.998) / 2)
    val candidates = pixels.filter { it.luminance >= bright && it.luminance > 0.02 }
    val neutral = candidates.sortedBy { it.saturation }.take(maxOf(1, candidates.size / 3))
    if (neutral.isEmpty()) {
        return ScreenLevels(listOf(1.0, 1.0, 1.0), listOf(0.0, 0.0, 0.0), percentile(luminances, 0.5) > LIGHT_MEDIAN)
    }
    val white = listOf(neutral.sumOf { it.r }, neutral.sumOf { it.g }, neutral.sumOf { it.b }).map { it / neutral.size }
    val top = white.max()
    val tint = white.map { top / maxOf(it, top / MAX_CAST, 1e-4) }

    // The brightest 1.5 % become white once the cast is gone, but nothing darker than the white; the darkest 1 % black,
    // within reason
    val corrected = pixels.map { Rgb(it.r * tint[0], it.g * tint[1], it.b * tint[2]).luminance }
    val high = maxOf(percentile(corrected, 0.985), Rgb(white[0] * tint[0], white[1] * tint[1], white[2] * tint[2]).luminance)
    val black = percentile(corrected, 0.01).coerceIn(0.0, high * 0.3)
    // Limited the same for every channel, so a dim photo keeps its balance rather than shifting hue
    val k = minOf(1 / maxOf(high - black, 0.02), MAX_GAIN / tint.max())
    val light = (percentile(corrected, 0.5) - black) * k > LIGHT_MEDIAN

    // A dark slide is mostly background, so its median in each channel is the background's colour
    val background = listOf(Rgb::r, Rgb::g, Rgb::b).mapIndexed { c, channel ->
        percentile(pixels.map { channel(it) * tint[c] }, 0.5)
    }
    val blacks = if (!light && background.all { (it - black) * k <= BLACKISH_BACKGROUND }) {
        background.map { it.coerceIn(black, high * 0.3) }
    } else {
        listOf(black, black, black)
    }
    return ScreenLevels(tint.map { it * k }, blacks.map { it * k }, light)
}

private fun percentile(values: List<Double>, p: Double): Double {
    val sorted = values.sorted()
    return sorted[(sorted.size * p).toInt().coerceIn(0, sorted.lastIndex)]
}
