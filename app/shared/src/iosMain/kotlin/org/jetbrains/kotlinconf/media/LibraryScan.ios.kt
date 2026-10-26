@file:OptIn(ExperimentalForeignApi::class)

package org.jetbrains.kotlinconf.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import org.jetbrains.kotlinconf.personal.MediaKind
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFURLRef
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSPredicate
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.ImageIO.CGImageDestinationAddImageFromSource
import platform.ImageIO.CGImageDestinationCreateWithURL
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetMediaTypeVideo
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypeFullSizePhoto
import platform.Photos.PHAssetResourceTypeFullSizeVideo
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAssetResourceTypeVideo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHFetchOptions
import platform.Photos.PHPhotoLibrary
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.time.Instant

/**
 * Reads the photo library: every photo and video taken inside one of [windows], copied one at a time from its original
 * file (downloading from iCloud where needed). HEIC photos are rewritten as JPEG with their metadata, so the report opens
 * anywhere. Results arrive on the main thread.
 */
internal fun scanPhotoLibrary(
    windows: List<ClosedRange<Instant>>,
    skipIds: Set<String>,
    onProgress: (done: Int, total: Int) -> Unit,
    onResult: (LibraryScan) -> Unit,
) {
    fun main(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)
    if (windows.isEmpty()) return onResult(LibraryScan.Found(emptyList(), 0))
    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
        if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
            return@requestAuthorizationForAccessLevel main { onResult(LibraryScan.Denied) }
        }
        val assets = findAssets(windows).filterNot { it.localIdentifier in skipIds }
        main { onProgress(0, assets.size) }
        if (assets.isEmpty()) return@requestAuthorizationForAccessLevel main { onResult(LibraryScan.Found(emptyList(), 0)) }

        val copied = mutableListOf<PickedMedia>()
        var failed = 0
        fun step(index: Int) {
            if (index == assets.size) return main { onResult(LibraryScan.Found(copied.toList(), failed)) }
            copyAsset(assets[index]) { item ->
                if (item != null) copied += item else failed++
                main { onProgress(index + 1, assets.size) }
                step(index + 1)
            }
        }
        step(0)
    }
}

private fun findAssets(windows: List<ClosedRange<Instant>>): List<PHAsset> {
    val from = windows.minOf { it.start }
    val to = windows.maxOf { it.endInclusive }
    val options = PHFetchOptions()
    options.predicate = NSPredicate.predicateWithFormat(
        "creationDate >= %@ AND creationDate <= %@ AND (mediaType == %d OR mediaType == %d)",
        argumentArray = listOf(from.toNSDate(), to.toNSDate(), PHAssetMediaTypeImage, PHAssetMediaTypeVideo),
    )
    options.sortDescriptors = listOf(NSSortDescriptor(key = "creationDate", ascending = true))
    val result = PHAsset.fetchAssetsWithOptions(options)
    return (0 until result.count.toInt()).mapNotNull { result.objectAtIndex(it.toULong()) as? PHAsset }
        .filter { asset ->
            val at = asset.creationDate?.toInstant() ?: return@filter false
            windows.any { at in it }
        }
}

/** Which of the photos with these library ids are hearted in the Photos app; asks for access the first time. */
internal fun lookUpFavorites(sourceIds: Set<String>, onResult: (FavoriteLookup) -> Unit) {
    fun main(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)
    if (sourceIds.isEmpty()) return onResult(FavoriteLookup.Found(emptySet(), 0))
    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
        if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
            return@requestAuthorizationForAccessLevel main { onResult(FavoriteLookup.Denied) }
        }
        val found = PHAsset.fetchAssetsWithLocalIdentifiers(sourceIds.toList(), options = null)
        val assets = (0 until found.count.toInt()).mapNotNull { found.objectAtIndex(it.toULong()) as? PHAsset }
        val favorites = assets.filter { it.favorite }.mapTo(mutableSetOf()) { it.localIdentifier }
        main { onResult(FavoriteLookup.Found(favorites, sourceIds.size - assets.size)) }
    }
}

/** Whether the library item with this id is hearted; false when it cannot be read (e.g. no access to the library). */
internal fun isFavoriteAsset(id: String): Boolean =
    (PHAsset.fetchAssetsWithLocalIdentifiers(listOf(id), options = null).firstObject as? PHAsset)?.favorite == true

/** Copies one asset's original file to a temporary file; [done] gets null when it cannot be read. */
private fun copyAsset(asset: PHAsset, done: (PickedMedia?) -> Unit) {
    val isVideo = asset.mediaType == PHAssetMediaTypeVideo
    val resources = PHAssetResource.assetResourcesForAsset(asset).filterIsInstance<PHAssetResource>()
    val preferred = if (isVideo) listOf(PHAssetResourceTypeFullSizeVideo, PHAssetResourceTypeVideo)
    else listOf(PHAssetResourceTypeFullSizePhoto, PHAssetResourceTypePhoto)
    val resource = preferred.firstNotNullOfOrNull { type -> resources.firstOrNull { it.type == type } } ?: return done(null)
    val extension = resource.originalFilename.substringAfterLast('.', if (isVideo) "mov" else "jpg").lowercase()
    val temp = NSTemporaryDirectory().trimEnd('/') + "/" + NSUUID().UUIDString + "." + extension
    val options = PHAssetResourceRequestOptions()
    options.networkAccessAllowed = true
    PHAssetResourceManager.defaultManager().writeDataForAssetResource(
        resource, toFile = NSURL.fileURLWithPath(temp), options = options,
    ) { error ->
        if (error != null) return@writeDataForAssetResource done(null)
        val takenAt = asset.creationDate?.toInstant()?.let { MediaTime.of(it) }
        val item = autoreleasepool {
            if (isVideo) {
                PickedMedia(temp, MediaKind.VIDEO, extension, thumbnailPath = videoStill(temp), takenAt = takenAt, sourceId = asset.localIdentifier, favorite = asset.favorite)
            } else if (extension == "heic" || extension == "heif") {
                val jpeg = heicToJpeg(temp)
                NSFileManager.defaultManager.removeItemAtPath(temp, error = null)
                jpeg?.let { PickedMedia(it, MediaKind.PHOTO, "jpg", takenAt = takenAt, sourceId = asset.localIdentifier, favorite = asset.favorite) }
            } else {
                PickedMedia(temp, MediaKind.PHOTO, extension, takenAt = takenAt, sourceId = asset.localIdentifier, favorite = asset.favorite)
            }?.let { photo -> photoThumbnail(photo)?.let { PickedMedia(photo.path, photo.kind, photo.extension, it, photo.takenAt, photo.sourceId, photo.favorite) } ?: photo }
        }
        done(item)
    }
}

/** Rewrites a HEIC photo as JPEG, keeping its metadata (date, orientation, location). */
private fun heicToJpeg(path: String): String? {
    val target = path.substringBeforeLast('.') + ".jpg"
    val sourceUrl = CFBridgingRetain(NSURL.fileURLWithPath(path)) as CFURLRef? ?: return null
    val targetUrl = CFBridgingRetain(NSURL.fileURLWithPath(target)) as CFURLRef?
    val jpegType = CFBridgingRetain("public.jpeg") as CFStringRef?
    val properties = CFBridgingRetain(mapOf<Any?, Any?>("kCGImageDestinationLossyCompressionQuality" to 0.9))
    try {
        val source = CGImageSourceCreateWithURL(sourceUrl, null) ?: return null
        val destination = CGImageDestinationCreateWithURL(targetUrl, jpegType, 1u, null)
        if (destination == null) {
            CFRelease(source)
            return null
        }
        @Suppress("UNCHECKED_CAST")
        CGImageDestinationAddImageFromSource(destination, source, 0u, properties as platform.CoreFoundation.CFDictionaryRef?)
        val ok = CGImageDestinationFinalize(destination)
        CFRelease(destination)
        CFRelease(source)
        return if (ok) target else null
    } finally {
        CFRelease(sourceUrl)
        targetUrl?.let { CFRelease(it) }
        jpegType?.let { CFRelease(it) }
        properties?.let { CFRelease(it) }
    }
}

private fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)
private fun NSDate.toInstant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * 1000).toLong())
