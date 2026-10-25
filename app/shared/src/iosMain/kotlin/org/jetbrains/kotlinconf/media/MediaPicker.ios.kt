@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("DEPRECATION")

package org.jetbrains.kotlinconf.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.jetbrains.kotlinconf.personal.MediaKind
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVMetadataCommonKeyCreationDate
import platform.AVFoundation.AVMetadataItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.commonKey
import platform.AVFoundation.availableMetadataFormats
import platform.AVFoundation.commonMetadata
import platform.AVFoundation.metadataForFormat
import platform.AVFoundation.dateValue
import platform.AVFoundation.stringValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.timeIntervalSince1970
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import kotlin.time.Instant
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCompatible
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerQualityTypeHigh
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSInteger
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_enter
import platform.darwin.dispatch_group_leave
import platform.darwin.dispatch_group_notify

@Composable
actual fun rememberMediaPicker(): MediaPicker? = remember { IosMediaPicker() }

private const val TYPE_IMAGE = "public.image"
private const val TYPE_JPEG = "public.jpeg"
private const val TYPE_PNG = "public.png"
private const val TYPE_MOVIE = "public.movie"

private class IosMediaPicker : MediaPicker {
    // UIKit keeps delegates weakly: hold on to them while their screen is up
    private var cameraDelegate: CameraDelegate? = null
    private var libraryDelegate: LibraryDelegate? = null
    private var previewSource: PreviewSource? = null

    override val hasCamera: Boolean =
        UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    override fun takePhoto(onPicked: (List<PickedMedia>) -> Unit) = openCamera(video = false, onPicked)

    override fun recordVideo(onPicked: (List<PickedMedia>) -> Unit) = openCamera(video = true, onPicked)

    private fun openCamera(video: Boolean, onPicked: (List<PickedMedia>) -> Unit) {
        if (!hasCamera) return onPicked(emptyList())
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.mediaTypes = listOf(if (video) TYPE_MOVIE else TYPE_IMAGE)
        if (video) {
            picker.cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModeVideo
            picker.videoQuality = UIImagePickerControllerQualityTypeHigh
            picker.videoMaximumDuration = 60.0 * 60
        }
        val delegate = CameraDelegate { result ->
            cameraDelegate = null
            onPicked(result)
        }
        cameraDelegate = delegate
        picker.delegate = delegate
        present(picker)
    }

    override fun pickFromLibrary(onLoading: () -> Unit, onPicked: (List<PickedMedia>) -> Unit) {
        val config = PHPickerConfiguration()
        config.selectionLimit = 0
        config.filter = PHPickerFilter.anyFilterMatchingSubfilters(
            listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter)
        )
        // HEIC photos arrive as JPEG and HEVC videos as H.264, which every recipient of the report can open
        config.preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCompatible
        val picker = PHPickerViewController(configuration = config)
        val delegate = LibraryDelegate(onLoading) { result ->
            libraryDelegate = null
            onPicked(result)
        }
        libraryDelegate = delegate
        picker.delegate = delegate
        present(picker)
    }

    override fun open(path: String, kind: MediaKind) {
        val controller = QLPreviewController()
        val source = PreviewSource(PreviewItem(NSURL.fileURLWithPath(path)))
        previewSource = source
        controller.dataSource = source
        present(controller)
    }
}

private class CameraDelegate(
    private val done: (List<PickedMedia>) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val videoUrl = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val picked = when {
            videoUrl != null -> copyVideo(videoUrl)
            image != null -> saveJpeg(image)
            else -> null
        }
        done(listOfNotNull(picked))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        done(emptyList())
    }
}

private class LibraryDelegate(
    private val onLoading: () -> Unit,
    private val done: (List<PickedMedia>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) return done(emptyList())
        onLoading()

        val picked = arrayOfNulls<PickedMedia>(results.size)
        val group = dispatch_group_create()
        results.forEachIndexed { index, result ->
            val provider = result.itemProvider
            val type = when {
                provider.hasItemConformingToTypeIdentifier(TYPE_MOVIE) -> TYPE_MOVIE
                provider.hasItemConformingToTypeIdentifier(TYPE_JPEG) -> TYPE_JPEG
                provider.hasItemConformingToTypeIdentifier(TYPE_PNG) -> TYPE_PNG
                provider.hasItemConformingToTypeIdentifier(TYPE_IMAGE) -> TYPE_IMAGE
                else -> return@forEachIndexed
            }
            dispatch_group_enter(group)
            // The file is only there while the handler runs: copy it right away
            provider.loadFileRepresentationForTypeIdentifier(type) { url, _ ->
                picked[index] = url?.let { copyPicked(it, type) }
                dispatch_group_leave(group)
            }
        }
        dispatch_group_notify(group, dispatch_get_main_queue()) {
            done(picked.filterNotNull())
        }
    }

    private fun copyPicked(url: NSURL, type: String): PickedMedia? = when (type) {
        TYPE_MOVIE -> copyVideo(url)
        TYPE_JPEG, TYPE_PNG -> copyFile(url, url.pathExtension?.ifEmpty { null } ?: if (type == TYPE_PNG) "png" else "jpg")
            ?.let { PickedMedia(it, MediaKind.PHOTO, it.substringAfterLast('.'), takenAt = photoTime(it)) }
        // Another image format (rare once HEIC is converted): re-encode it as JPEG, keeping when it was taken
        else -> url.path?.let { path ->
            val takenAt = photoTime(path)
            UIImage.imageWithContentsOfFile(path)?.let { saveJpeg(it) }?.let { PickedMedia(it.path, it.kind, it.extension, takenAt = takenAt) }
        }
    }
}

private class PreviewItem(private val url: NSURL) : NSObject(), QLPreviewItemProtocol {
    override fun previewItemURL(): NSURL = url
    override fun previewItemTitle(): String = ""
}

private class PreviewSource(private val item: PreviewItem) : NSObject(), QLPreviewControllerDataSourceProtocol {
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): NSInteger = 1
    override fun previewController(controller: QLPreviewController, previewItemAtIndex: NSInteger): QLPreviewItemProtocol = item
}

private fun tempPath(extension: String): String =
    NSTemporaryDirectory().trimEnd('/') + "/" + NSUUID().UUIDString + "." + extension.lowercase()

private fun copyFile(url: NSURL, extension: String): String? {
    val target = tempPath(extension)
    val copied = NSFileManager.defaultManager.copyItemAtURL(url, toURL = NSURL.fileURLWithPath(target), error = null)
    return if (copied) target else null
}

private fun copyVideo(url: NSURL): PickedMedia? {
    val extension = url.pathExtension?.ifEmpty { null } ?: "mov"
    val path = copyFile(url, extension) ?: return null
    return PickedMedia(path, MediaKind.VIDEO, extension.lowercase(), thumbnailPath = videoStill(path), takenAt = videoTime(path))
}

/** When a photo was taken, from its EXIF date and time zone offset (a screenshot has them too). */
private fun photoTime(path: String): MediaTime? {
    val url = CFBridgingRetain(NSURL.fileURLWithPath(path)) as CFURLRef? ?: return null
    val source = CGImageSourceCreateWithURL(url, null)
    CFRelease(url)
    source ?: return null
    @Suppress("UNCHECKED_CAST")
    val properties = CFBridgingRelease(CGImageSourceCopyPropertiesAtIndex(source, 0u, null)) as? Map<Any?, *>
    CFRelease(source)
    val exif = properties?.get("{Exif}") as? Map<*, *>
    val tiff = properties?.get("{TIFF}") as? Map<*, *>
    return MediaTime.fromExif(
        dateTime = exif?.get("DateTimeOriginal") as? String ?: tiff?.get("DateTime") as? String,
        offset = exif?.get("OffsetTimeOriginal") as? String ?: exif?.get("OffsetTime") as? String,
    ) ?: fileTime(path)
}

/**
 * The file's date. The photo picker sets it to when the Photos library says the item was taken, so it stands in
 * for files that carry no date of their own (screenshots without EXIF, videos from other apps).
 */
private fun fileTime(path: String): MediaTime? {
    val date = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)?.get(NSFileModificationDate) as? NSDate
        ?: return null
    return MediaTime.of(Instant.fromEpochMilliseconds((date.timeIntervalSince1970 * 1000).toLong()))
}

/** When a video was recorded: its creation date, an exact moment. */
private fun videoTime(path: String): MediaTime? {
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
    val common = asset.commonMetadata.filterIsInstance<AVMetadataItem>().filter { it.commonKey == AVMetadataCommonKeyCreationDate }
    // Other writers put the date under their own keys; any item whose value is a date will do
    val all = asset.availableMetadataFormats.flatMap { format -> asset.metadataForFormat(format as String).filterIsInstance<AVMetadataItem>() }
    for (item in common + all) {
        item.dateValue?.let { return MediaTime.of(Instant.fromEpochMilliseconds((it.timeIntervalSince1970 * 1000).toLong())) }
        // As ISO 8601 text, often with the offset written as "-0700"
        val text = item.stringValue?.trim() ?: continue
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}").containsMatchIn(text)) continue
        runCatching { Instant.parse(text.replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")) }.getOrNull()
            ?.takeIf { it.toEpochMilliseconds() > 946_684_800_000 }
            ?.let { return MediaTime.of(it) }
    }
    return fileTime(path)
}

/** A camera photo drawn upright, so viewers that ignore the orientation flag show it the right way up. */
private fun saveJpeg(image: UIImage): PickedMedia? {
    val format = UIGraphicsImageRendererFormat.defaultFormat()
    format.scale = image.scale
    val upright = image.size.useContents {
        val size = CGSizeMake(width, height)
        UIGraphicsImageRenderer(size = size, format = format).imageWithActions {
            image.drawInRect(CGRectMake(0.0, 0.0, width, height))
        }
    }
    val path = tempPath("jpg")
    val data = UIImageJPEGRepresentation(upright, 0.85) ?: return null
    return if (data.writeToFile(path, atomically = true)) PickedMedia(path, MediaKind.PHOTO, "jpg") else null
}

/** A small still from the first second of a video, for the list on the session page. */
private fun videoStill(path: String): String? {
    val generator = AVAssetImageGenerator(asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null))
    generator.appliesPreferredTrackTransform = true
    generator.maximumSize = CGSizeMake(480.0, 480.0)
    val still = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(1.0, 600), actualTime = null, error = null)
        ?: generator.copyCGImageAtTime(CMTimeMakeWithSeconds(0.0, 600), actualTime = null, error = null)
        ?: return null
    val data = UIImageJPEGRepresentation(UIImage.imageWithCGImage(still), 0.8) ?: return null
    val target = tempPath("jpg")
    return if (data.writeToFile(target, atomically = true)) target else null
}

private fun present(controller: UIViewController) {
    dispatch_async(dispatch_get_main_queue()) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@dispatch_async
        var top: UIViewController = root
        while (true) top = top.presentedViewController ?: break
        top.presentViewController(controller, animated = true, completion = null)
    }
}
