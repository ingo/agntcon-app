package org.jetbrains.kotlinconf.media

/** A colour cast is never stronger than this ratio between channels; past it, the "white" is a coloured shape. */
private const val MAX_CAST = 2.5
private const val MAX_GAIN = 8.0

/**
 * The white of a slide is looked for among pixels at least this bright compared with its brightest. On a dark slide
 * the text is often well under a tenth of the photo, so the brightest tenth alone would be the coloured background.
 */
private const val WHITE_SHARE_OF_BRIGHTEST = 0.5

/** A pixel in linear light. */
internal class Rgb(val r: Double, val g: Double, val b: Double) {
    val luminance: Double get() = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val saturation: Double get() {
        val max = maxOf(r, g, b)
        return if (max > 0) (max - minOf(r, g, b)) / max else 0.0
    }
}

/** Per channel gains and one black point for all channels (out = in × gain − black), and the result's median. */
internal class Levels(val gains: List<Double>, val black: Double, val median: Double) {
    fun apply(pixel: Rgb) = Rgb(
        pixel.r * gains[0] - black,
        pixel.g * gains[1] - black,
        pixel.b * gains[2] - black,
    )
}

/**
 * Removes the colour cast and stretches the levels of a photo of a slide, measured on [pixels]. The white of a slide is
 * the least coloured of its brightest pixels (the background of a light slide, the text of a dark one), so its tint
 * is the cast and is taken out per channel. A coloured slide keeps its colours because its white text or frame sets
 * the balance, not its background. Then one black point for all channels, so nothing shifts hue. Null when there is
 * nothing bright enough to go by.
 */
internal fun levels(pixels: List<Rgb>): Levels? {
    if (pixels.isEmpty()) return null
    val luminance = pixels.map { it.luminance }
    val bright = maxOf(percentile(luminance, 0.9), percentile(luminance, 0.995) * WHITE_SHARE_OF_BRIGHTEST)
    val candidates = pixels.filter { it.luminance >= bright && it.luminance > 0.02 }
    val neutral = candidates.sortedBy { it.saturation }.take(maxOf(1, candidates.size / 3))
    if (neutral.isEmpty()) return null
    val white = listOf(neutral.sumOf { it.r }, neutral.sumOf { it.g }, neutral.sumOf { it.b }).map { it / neutral.size }
    val top = white.max()
    val tint = white.map { top / maxOf(it, top / MAX_CAST, 1e-4) }

    // The brightest 1.5 % become white once the cast is gone; the darkest 1 % black, within reason
    val corrected = pixels.map { Rgb(it.r * tint[0], it.g * tint[1], it.b * tint[2]).luminance }
    val high = percentile(corrected, 0.985)
    val black = percentile(corrected, 0.01).coerceIn(0.0, high * 0.3)
    val k = 1 / maxOf(high - black, 0.02)
    return Levels(tint.map { minOf(it * k, MAX_GAIN) }, black * k, (percentile(corrected, 0.5) - black) * k)
}

internal fun percentile(values: List<Double>, p: Double): Double {
    val sorted = values.sorted()
    return sorted[(sorted.size * p).toInt().coerceIn(0, sorted.lastIndex)]
}
