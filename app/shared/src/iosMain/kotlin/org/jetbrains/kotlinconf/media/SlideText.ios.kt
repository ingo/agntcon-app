@file:OptIn(ExperimentalForeignApi::class)

package org.jetbrains.kotlinconf.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/** Apple's on-device text recognition (Vision); slides are mostly large, clear text. */
actual suspend fun recognizeText(path: String): String? = withContext(Dispatchers.IO) {
    try {
        val request = VNRecognizeTextRequest()
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        request.usesLanguageCorrection = true
        val handler = VNImageRequestHandler(uRL = NSURL.fileURLWithPath(path), options = emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), error = null)) return@withContext null
        request.results.orEmpty()
            .filterIsInstance<VNRecognizedTextObservation>()
            .mapNotNull { (it.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }
}
