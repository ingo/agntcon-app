@file:OptIn(ExperimentalForeignApi::class)

package org.jetbrains.kotlinconf.media

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinconf.personal.CleanUpMode
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.CIVector
import platform.CoreImage.filterWithName
import platform.CoreImage.kCIFormatRGBAf
import platform.CoreImage.kCIImageApplyOrientationProperty
import platform.CoreImage.kCIInputImageKey
import platform.CoreImage.writeJPEGRepresentationOfImage
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.setValue
import platform.Vision.VNDetectDocumentSegmentationRequest
import platform.Vision.VNDetectRectanglesRequest
import platform.Vision.VNDetectTextRectanglesRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRectangleObservation
import kotlin.math.abs
import kotlin.math.hypot

actual val screenCleanUpAvailable: Boolean = true

private const val INSET = 0.012
private const val DETECT_PIXELS = 1600
private const val MAX_OUTPUT = 3000.0

/** Smallest screen worth straightening, as a share of the photo: a screen seen from the back of a large room. */
private const val MIN_SCREEN_AREA = 0.06

/** A slide outline must be this sure, and clearly larger than the rectangle found, to be used instead. */
private const val MIN_OUTLINE_CONFIDENCE = 0.85f
private const val OUTLINE_LARGER = 1.3

/**
 * Width to height of a screen or board as photographed, perspective included. An outline outside this is not a
 * slide; the simulator's outline model, for one, answers every photo with a strip along the bottom.
 */
private val SCREEN_SHAPES = 0.6..3.0

/** A photo with no screen edges but this many lines of text is a slide filling the frame: enhanced, not cropped. */
private const val MIN_TEXT_LINES = 4

/** Pixels on the long side of the copy the colours are measured on. */
private const val STAT_PIXELS = 256.0

/**
 * Vision finds the screen, monitor or whiteboard: the clearest four-sided shape, or the outline of a slide that fills
 * most of the frame. CoreImage maps it to a flat rectangle. A photo with no edges to find but plenty of text is a slide
 * filling the frame and is enhanced as it is. Then the colours: the projector's cast is removed so white is white,
 * the brightest part is stretched to white and the darkest to black, and light slides get their uneven lighting
 * evened out and faint lines darkened. Everything is sharpened.
 */
actual suspend fun cleanUpScreenPhoto(input: String, output: String, mode: CleanUpMode?): Boolean = withContext(Dispatchers.IO) {
    // No clean-up at all is the attendee's choice to keep the photo as it was taken
    if (mode == CleanUpMode.ORIGINAL) return@withContext false
    try {
        autoreleasepool { cleanUp(input, output, mode) }
    } catch (_: Throwable) {
        false
    }
}

/** What to clean up: the screen's corners (normalised, origin bottom left), or the whole photo. */
private class Found(val corners: List<Pair<Double, Double>>?)

private fun cleanUp(input: String, output: String, mode: CleanUpMode?): Boolean {
    // The screen is found on a small copy; only a photo that has one is opened at full size
    val found = findScreen(input) ?: return false
    val image = CIImage(
        contentsOfURL = NSURL.fileURLWithPath(input),
        options = mapOf<Any?, Any?>(kCIImageApplyOrientationProperty to true),
    )
    val context = sharedCIContext
    val flat = found.corners?.let { straighten(image, it) ?: return false } ?: image

    // A slide is legible at 3000 pixels; larger only costs memory, time and space
    val longSide = flat.extent.useContents { maxOf(size.width, size.height) }
    val sized = if (longSide <= MAX_OUTPUT) flat else filter("CILanczosScaleTransform", flat) {
        setValue(NSNumber(MAX_OUTPUT / longSide), forKey = "inputScale")
        setValue(NSNumber(1.0), forKey = "inputAspectRatio")
    } ?: flat

    val enhanced = enhance(sized, context, mode)
    val sharp = filter("CIUnsharpMask", enhanced) {
        setValue(NSNumber(2.0), forKey = "inputRadius")
        setValue(NSNumber(0.5), forKey = "inputIntensity")
    } ?: enhanced

    return context.writeJPEGRepresentationOfImage(
        sharp.imageByCroppingToRect(sized.extent),
        toURL = NSURL.fileURLWithPath(output),
        colorSpace = CGColorSpaceCreateDeviceRGB(),
        options = mapOf<Any?, Any?>("kCGImageDestinationLossyCompressionQuality" to 0.9),
        error = null,
    )
}

private fun straighten(image: CIImage, points: List<Pair<Double, Double>>): CIImage? {
    val (width, height) = image.extent.useContents { size.width to size.height }
    val centerX = points.sumOf { it.first } / 4
    val centerY = points.sumOf { it.second } / 4
    // A little inside the detected edge, so the frame of the screen or board does not show as a dark line
    fun corner(point: Pair<Double, Double>) = CIVector(
        x = (point.first + (centerX - point.first) * INSET) * width,
        Y = (point.second + (centerY - point.second) * INSET) * height,
    )
    return filter("CIPerspectiveCorrection", image) {
        setValue(corner(points[0]), forKey = "inputTopLeft")
        setValue(corner(points[1]), forKey = "inputTopRight")
        setValue(corner(points[2]), forKey = "inputBottomLeft")
        setValue(corner(points[3]), forKey = "inputBottomRight")
    }
}

/**
 * The screen or board, looked for on a small copy. Vision's most confident clear rectangle, unless the outline of a
 * slide filling the frame is confident and clearly larger (the rectangle is then a table or box on the slide). With
 * neither, a photo full of text is a slide too close to have edges; anything else (people, a stage) is left alone.
 */
private fun findScreen(path: String): Found? {
    val bitmap = downsampledImage(path, DETECT_PIXELS) ?: return null
    try {
        val rectangles = VNDetectRectanglesRequest().apply {
            maximumObservations = 4u
            minimumConfidence = 0.5f
            minimumSize = 0.12f
            minimumAspectRatio = 0.3f
            quadratureTolerance = 30f
        }
        val outline = VNDetectDocumentSegmentationRequest()
        val handler = VNImageRequestHandler(cGImage = bitmap, options = emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(rectangles, outline), error = null)) return null
        val rectangle = rectangles.results?.filterIsInstance<VNRectangleObservation>()?.firstOrNull { it.area >= MIN_SCREEN_AREA }
        val aspect = CGImageGetWidth(bitmap).toDouble() / CGImageGetHeight(bitmap).toDouble()
        val slide = outline.results?.filterIsInstance<VNRectangleObservation>()?.firstOrNull {
            it.confidence >= MIN_OUTLINE_CONFIDENCE && it.area >= MIN_SCREEN_AREA && it.aspectRatio(aspect) in SCREEN_SHAPES
        }
        val best = when {
            rectangle != null && slide != null -> if (slide.area > rectangle.area * OUTLINE_LARGER) slide else rectangle
            else -> rectangle ?: slide
        }
        if (best != null) {
            // Corners come back normalised, origin bottom left, as CoreImage uses them, so they fit the full photo too
            return Found(listOf(best.topLeft, best.topRight, best.bottomLeft, best.bottomRight).map { it.pair() })
        }
        return if (textLines(bitmap) >= MIN_TEXT_LINES) Found(null) else null
    } finally {
        CGImageRelease(bitmap)
    }
}

private fun textLines(bitmap: CGImageRef): Int {
    val request = VNDetectTextRectanglesRequest()
    val handler = VNImageRequestHandler(cGImage = bitmap, options = emptyMap<Any?, Any?>())
    if (!handler.performRequests(listOf(request), error = null)) return 0
    return request.results?.size ?: 0
}

/** Width to height of the four corners in pixels, for a photo [imageAspect] times wider than high. */
private fun VNRectangleObservation.aspectRatio(imageAspect: Double): Double {
    fun distance(a: CValue<CGPoint>, b: CValue<CGPoint>): Double {
        val (ax, ay) = a.pair()
        val (bx, by) = b.pair()
        return hypot((ax - bx) * imageAspect, ay - by)
    }
    val width = (distance(topLeft, topRight) + distance(bottomLeft, bottomRight)) / 2
    val height = (distance(topLeft, bottomLeft) + distance(topRight, bottomRight)) / 2
    return if (height > 0) width / height else Double.MAX_VALUE
}

private fun CValue<CGPoint>.pair(): Pair<Double, Double> = useContents { x to y }

/** The share of the photo inside the four corners. */
private val VNRectangleObservation.area: Double
    get() {
        val p = listOf(topLeft, topRight, bottomRight, bottomLeft).map { it.pair() }
        var sum = 0.0
        for (i in 0 until 4) {
            val (ax, ay) = p[i]
            val (bx, by) = p[(i + 1) % 4]
            sum += ax * by - bx * ay
        }
        return abs(sum) / 2
    }

private fun enhance(image: CIImage, context: CIContext, mode: CleanUpMode?): CIImage {
    val levels = measureScreenLevels(samplePixels(image, context), mode) ?: return darkSlide(image)
    val balanced = balance(image, levels)
    return if (levels.light) lightSlide(balanced) else darkSlide(balanced)
}

/** Removes the colour cast and stretches the levels, as measured by [measureScreenLevels]. */
private fun balance(image: CIImage, levels: ScreenLevels): CIImage {
    val gains = levels.gains
    val biases = levels.biases
    return filter("CIColorMatrix", image) {
        setValue(CIVector(x = gains[0], Y = 0.0, Z = 0.0, W = 0.0), forKey = "inputRVector")
        setValue(CIVector(x = 0.0, Y = gains[1], Z = 0.0, W = 0.0), forKey = "inputGVector")
        setValue(CIVector(x = 0.0, Y = 0.0, Z = gains[2], W = 0.0), forKey = "inputBVector")
        setValue(CIVector(x = -biases[0], Y = -biases[1], Z = -biases[2], W = 0.0), forKey = "inputBiasVector")
    }?.imageByCroppingToRect(image.extent) ?: image
}

/** The pixels of a small copy, in the linear working space CoreImage filters see. */
private fun samplePixels(image: CIImage, context: CIContext): List<Rgb> {
    val longSide = image.extent.useContents { maxOf(size.width, size.height) }
    val small = filter("CILanczosScaleTransform", image) {
        setValue(NSNumber(STAT_PIXELS / longSide), forKey = "inputScale")
        setValue(NSNumber(1.0), forKey = "inputAspectRatio")
    } ?: return emptyList()
    val (x, y, size) = small.extent.useContents { Triple(origin.x, origin.y, size.width.toInt() to size.height.toInt()) }
    val (width, height) = size
    if (width <= 0 || height <= 0) return emptyList()
    return memScoped {
        val buffer = allocArray<FloatVar>(width * height * 4)
        context.render(
            small,
            toBitmap = buffer,
            rowBytes = (width * 16).convert(),
            bounds = CGRectMake(x, y, width.toDouble(), height.toDouble()),
            format = kCIFormatRGBAf,
            colorSpace = null,
        )
        List(width * height) { i -> Rgb(buffer[i * 4].toDouble(), buffer[i * 4 + 1].toDouble(), buffer[i * 4 + 2].toDouble()) }
    }
}

/**
 * Dark text on a light background. The projector's hot spot and fall-off are evened out gently (measured on a tiny,
 * blurred copy and never brightened more than 1.4×, so a coloured panel does not wash out), then faint lines are made
 * darker while white stays white.
 */
private fun lightSlide(image: CIImage): CIImage {
    val longSide = image.extent.useContents { maxOf(size.width, size.height) }
    val scale = 64 / longSide
    val lighting = filter("CILanczosScaleTransform", image) {
        setValue(NSNumber(scale), forKey = "inputScale")
        setValue(NSNumber(1.0), forKey = "inputAspectRatio")
    }?.let { tiny ->
        filter("CIGaussianBlur", tiny.imageByClampingToExtent()) { setValue(NSNumber(11.0), forKey = "inputRadius") }
            ?.imageByCroppingToRect(tiny.extent)
    }?.let { blurred ->
        filter("CIColorClamp", blurred) {
            setValue(CIVector(x = 0.7, Y = 0.7, Z = 0.7, W = 1.0), forKey = "inputMinComponents")
            setValue(CIVector(x = 1.0, Y = 1.0, Z = 1.0, W = 1.0), forKey = "inputMaxComponents")
        }
    }?.imageByClampingToExtent()
        ?.imageByApplyingTransform(CGAffineTransformMakeScale(1 / scale, 1 / scale))
        ?.imageByCroppingToRect(image.extent)
    // CIDivideBlendMode gives background ÷ input, so the lighting is the input: photo ÷ lighting
    val even = lighting?.let { filter("CIDivideBlendMode", it) { setValue(image, forKey = "inputBackgroundImage") } } ?: image
    val curved = toneCurve(even, 0.17, 0.4, 0.68)
    return filter("CIVibrance", curved) { setValue(NSNumber(0.15), forKey = "inputAmount") } ?: curved
}

/** Light text on a dark background: a little more contrast, colours as they are. */
private fun darkSlide(image: CIImage): CIImage = toneCurve(image, 0.22, 0.52, 0.8)

/** A curve through (0, 0), (¼, [quarter]), (½, [half]), (¾, [threeQuarters]) and (1, 1). */
private fun toneCurve(image: CIImage, quarter: Double, half: Double, threeQuarters: Double): CIImage =
    filter("CIToneCurve", image) {
        setValue(CIVector(x = 0.0, Y = 0.0), forKey = "inputPoint0")
        setValue(CIVector(x = 0.25, Y = quarter), forKey = "inputPoint1")
        setValue(CIVector(x = 0.5, Y = half), forKey = "inputPoint2")
        setValue(CIVector(x = 0.75, Y = threeQuarters), forKey = "inputPoint3")
        setValue(CIVector(x = 1.0, Y = 1.0), forKey = "inputPoint4")
    } ?: image

private inline fun filter(name: String, input: CIImage, configure: CIFilter.() -> Unit): CIImage? {
    val filter = CIFilter.filterWithName(name) ?: return null
    filter.setValue(input, forKey = kCIInputImageKey)
    filter.configure()
    return filter.outputImage
}
