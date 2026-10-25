@file:OptIn(ExperimentalForeignApi::class)

package org.jetbrains.kotlinconf.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import platform.CoreGraphics.CGImageRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/** Apple's on-device text recognition (Vision); slides are mostly large, clear text. */
actual suspend fun recognizeText(path: String): String? = withContext(Dispatchers.IO) {
    try {
        autoreleasepool {
            // A 2000-pixel copy reads a slide as well as the full photo and needs a fraction of the memory
            val bitmap = downsampledImage(path, 2000) ?: return@autoreleasepool null
            try {
                val request = VNRecognizeTextRequest()
                request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
                request.usesLanguageCorrection = true
                val handler = VNImageRequestHandler(cGImage = bitmap, options = emptyMap<Any?, Any?>())
                if (!handler.performRequests(listOf(request), error = null)) return@autoreleasepool null
                request.results.orEmpty()
                    .filterIsInstance<VNRecognizedTextObservation>()
                    .mapNotNull { (it.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string }
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
            } finally {
                CGImageRelease(bitmap)
            }
        }
    } catch (_: Throwable) {
        null
    }
}
