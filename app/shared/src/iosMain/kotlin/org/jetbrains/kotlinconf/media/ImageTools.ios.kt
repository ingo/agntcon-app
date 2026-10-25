@file:OptIn(ExperimentalForeignApi::class)

package org.jetbrains.kotlinconf.media

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.CoreGraphics.CGImageRef
import platform.CoreImage.CIContext
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSURL
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL

/**
 * One CoreImage context for the whole app: making one is expensive and each keeps its own caches. CoreImage
 * contexts are safe to use from several threads.
 */
internal val sharedCIContext: CIContext by lazy { CIContext(options = null) }

/**
 * A small copy of the photo at [path], upright, at most [maxPixels] on its long side, decoded straight to that size
 * (the full photo is never in memory). The caller owns the result and must [platform.CoreGraphics.CGImageRelease] it.
 */
internal fun downsampledImage(path: String, maxPixels: Int): CGImageRef? {
    val url = CFBridgingRetain(NSURL.fileURLWithPath(path)) as CFURLRef? ?: return null
    val source = CGImageSourceCreateWithURL(url, null)
    CFRelease(url)
    source ?: return null
    // The CFString keys spelled out: an NSDictionary with these NSString keys is the same CFDictionary
    val options = CFBridgingRetain(
        mapOf<Any?, Any?>(
            "kCGImageSourceCreateThumbnailFromImageAlways" to true,
            "kCGImageSourceCreateThumbnailWithTransform" to true,
            "kCGImageSourceShouldCacheImmediately" to true,
            "kCGImageSourceThumbnailMaxPixelSize" to maxPixels,
        )
    ) as CFDictionaryRef?
    val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
    options?.let { CFRelease(it) }
    CFRelease(source)
    return image
}
