@file:OptIn(ExperimentalForeignApi::class)

package org.jetbrains.kotlinconf.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

/** Decoded straight to the small size (the full photo is never in memory), upright. */
actual fun makeThumbnail(input: String, output: String): Boolean = autoreleasepool {
    val image = downsampledImage(input, THUMBNAIL_PIXELS) ?: return@autoreleasepool false
    try {
        val data = UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), 0.8) ?: return@autoreleasepool false
        data.writeToFile(output, atomically = true)
    } finally {
        CGImageRelease(image)
    }
}
