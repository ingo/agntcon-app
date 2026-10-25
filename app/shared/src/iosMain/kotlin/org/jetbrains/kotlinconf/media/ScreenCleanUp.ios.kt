@file:OptIn(ExperimentalForeignApi::class)

package org.jetbrains.kotlinconf.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.cinterop.autoreleasepool
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.kCIFormatRGBA8
import platform.CoreImage.filterWithName
import platform.Foundation.setValue
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.CIVector
import platform.CoreImage.kCIImageApplyOrientationProperty
import platform.CoreImage.kCIInputImageKey
import platform.CoreImage.writeJPEGRepresentationOfImage
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Vision.VNDetectRectanglesRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRectangleObservation

actual val screenCleanUpAvailable: Boolean = true

private const val INSET = 0.012
private const val DETECT_PIXELS = 1600
private const val MAX_OUTPUT = 3000.0

/**
 * Vision finds the largest clear four-sided shape (a projection screen, a monitor, a whiteboard); CoreImage maps it to
 * a flat rectangle and enhances it. Light backgrounds (whiteboards, light slides) get the classic whiteboard clean-up:
 * the photo is divided by a heavily blurred copy of itself, which turns uneven lighting and glare into an even white.
 * Dark slides keep their colours and get more contrast. Both are sharpened.
 */
actual suspend fun cleanUpScreenPhoto(input: String, output: String): Boolean = withContext(Dispatchers.IO) {
    try {
        autoreleasepool { cleanUp(input, output) }
    } catch (_: Throwable) {
        false
    }
}

private fun cleanUp(input: String, output: String): Boolean {
    // The screen is found on a small copy; only a photo that has one is opened at full size
    val quad = findScreen(input) ?: return false
    val image = CIImage(
        contentsOfURL = NSURL.fileURLWithPath(input),
        options = mapOf<Any?, Any?>(kCIImageApplyOrientationProperty to true),
    )
    val context = sharedCIContext
    run {

        val (width, height) = image.extent.useContents { size.width to size.height }
        val points = listOf(quad.topLeft, quad.topRight, quad.bottomLeft, quad.bottomRight).map { p -> p.useContents { x to y } }
        val centerX = points.sumOf { it.first } / 4
        val centerY = points.sumOf { it.second } / 4
        // A little inside the detected edge, so the frame of the screen or board does not show as a dark line
        fun corner(point: Pair<Double, Double>) = CIVector(
            x = (point.first + (centerX - point.first) * INSET) * width,
            Y = (point.second + (centerY - point.second) * INSET) * height,
        )
        val flat = filter("CIPerspectiveCorrection", image) {
            setValue(corner(points[0]), forKey = "inputTopLeft")
            setValue(corner(points[1]), forKey = "inputTopRight")
            setValue(corner(points[2]), forKey = "inputBottomLeft")
            setValue(corner(points[3]), forKey = "inputBottomRight")
        } ?: return false

        val enhanced = if (isLight(flat, context)) whiteboard(flat) else darkSlide(flat)
        val sharp = filter("CIUnsharpMask", enhanced) {
            setValue(NSNumber(2.0), forKey = "inputRadius")
            setValue(NSNumber(0.6), forKey = "inputIntensity")
        } ?: enhanced
        // A slide is legible at 3000 pixels; larger only costs memory and space
        val longSide = sharp.extent.useContents { maxOf(size.width, size.height) }
        val sized = if (longSide <= MAX_OUTPUT) sharp else filter("CILanczosScaleTransform", sharp) {
            setValue(NSNumber(MAX_OUTPUT / longSide), forKey = "inputScale")
            setValue(NSNumber(1.0), forKey = "inputAspectRatio")
        } ?: sharp

        return context.writeJPEGRepresentationOfImage(
            sized,
            toURL = NSURL.fileURLWithPath(output),
            colorSpace = CGColorSpaceCreateDeviceRGB(),
            options = mapOf<Any?, Any?>("kCGImageDestinationLossyCompressionQuality" to 0.9),
            error = null,
        )
    }
}

/** The screen or board: one clear, large, roughly rectangular shape, or nothing. Looked for on a small copy. */
private fun findScreen(path: String): VNRectangleObservation? {
    val request = VNDetectRectanglesRequest()
    request.maximumObservations = 1u
    request.minimumConfidence = 0.8f
    // A slide photographed from a seat fills a fair part of the frame; small shapes (signs, laptops) are not it
    request.minimumSize = 0.3f
    request.minimumAspectRatio = 0.3f
    request.quadratureTolerance = 25f
    // Corners come back normalised, origin bottom left, as CoreImage uses them, so they fit the full photo too
    val bitmap = downsampledImage(path, DETECT_PIXELS) ?: return null
    try {
        val handler = VNImageRequestHandler(cGImage = bitmap, options = emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), error = null)) return null
        return request.results?.filterIsInstance<VNRectangleObservation>()?.firstOrNull()
    } finally {
        CGImageRelease(bitmap)
    }
}

/** Average brightness above the middle: a whiteboard or a light slide. */
private fun isLight(image: CIImage, context: CIContext): Boolean {
    val average = filter("CIAreaAverage", image) {
        setValue(CIVector(cGRect = image.extent), forKey = "inputExtent")
    } ?: return false
    val pixel = IntArray(4)
    memScoped {
        val buffer = allocArray<UByteVar>(4)
        context.render(
            average,
            toBitmap = buffer,
            rowBytes = 4,
            bounds = CGRectMake(0.0, 0.0, 1.0, 1.0),
            format = kCIFormatRGBA8,
            colorSpace = null,
        )
        for (i in 0 until 4) pixel[i] = buffer[i].toInt()
    }
    val luminance = 0.299 * pixel[0] + 0.587 * pixel[1] + 0.114 * pixel[2]
    return luminance > 140
}

/** Even white background: divide by a blurred copy, then push the faint marks darker. */
private fun whiteboard(image: CIImage): CIImage {
    val radius = image.extent.useContents { maxOf(size.width, size.height) } / 40
    val background = filter("CIGaussianBlur", image.imageByClampingToExtent()) {
        setValue(NSNumber(radius), forKey = "inputRadius")
    }?.imageByCroppingToRect(image.extent) ?: return image
    // CIDivideBlendMode gives background ÷ input, so the blurred copy is the input: photo ÷ blur
    val divided = filter("CIDivideBlendMode", background) { setValue(image, forKey = "inputBackgroundImage") } ?: return image
    return filter("CIColorControls", divided) {
        setValue(NSNumber(1.35), forKey = "inputContrast")
        setValue(NSNumber(1.1), forKey = "inputSaturation")
        setValue(NSNumber(0.02), forKey = "inputBrightness")
    } ?: divided
}

/** A projected dark slide: more contrast and a little colour, nothing that would wash it out. */
private fun darkSlide(image: CIImage): CIImage =
    filter("CIColorControls", image) {
        setValue(NSNumber(1.2), forKey = "inputContrast")
        setValue(NSNumber(1.1), forKey = "inputSaturation")
    } ?: image

private inline fun filter(name: String, input: CIImage, configure: CIFilter.() -> Unit): CIImage? {
    val filter = CIFilter.filterWithName(name) ?: return null
    filter.setValue(input, forKey = kCIInputImageKey)
    filter.configure()
    return filter.outputImage
}
