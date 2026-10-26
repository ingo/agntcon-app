package org.jetbrains.kotlinconf.media

import androidx.compose.runtime.Composable
import org.jetbrains.kotlinconf.personal.MediaKind
import kotlin.time.Instant

/**
 * A photo or video the camera or the library handed over, as a temporary file that the app then moves
 * into [MediaFiles]. [thumbnailPath] is a still for a video, also temporary.
 */
class PickedMedia(
    val path: String,
    val kind: MediaKind,
    val extension: String,
    val thumbnailPath: String? = null,
    /** When it was taken, if the file says so; used to match photos from the library to sessions. */
    val takenAt: MediaTime? = null,
    /** The photo library's id for the item, where the platform gives one; the same photo always has the same id. */
    val sourceId: String? = null,
    /** True when the photo is a favorite (the heart) in the photo library; such a photo starts out starred. */
    val favorite: Boolean = false,
)

/**
 * The platform's camera, photo library and full-screen viewer. Results arrive on the main thread;
 * an empty list means the attendee cancelled or nothing could be read.
 */
interface MediaPicker {
    /** Whether this device has a camera the app can use. */
    val hasCamera: Boolean

    fun takePhoto(onPicked: (List<PickedMedia>) -> Unit)

    fun recordVideo(onPicked: (List<PickedMedia>) -> Unit)

    /**
     * Photos, screenshots and videos from the library, several at a time. [onProgress] reports how many of the
     * chosen items have been copied (from 0 when copying starts); [onPicked] gets the copies and how many could not
     * be read.
     */
    fun pickFromLibrary(onProgress: (done: Int, total: Int) -> Unit, onPicked: (List<PickedMedia>, failed: Int) -> Unit)

    /** Shows stored photos (swipe between [paths], e.g. a cleaned-up photo and its original) or plays a video. */
    fun open(paths: List<String>, kind: MediaKind)

    /** Whether [scanLibrary] works here: it reads the photo library directly, which needs the user's permission. */
    val canScanLibrary: Boolean get() = false

    /**
     * Finds the photos and videos taken inside any of [windows] and copies them one at a time, leaving out the ones
     * whose library id is in [skipIds]. Asks for access to the library the first time. [onProgress] reports how many
     * of the found items are done.
     */
    fun scanLibrary(
        windows: List<ClosedRange<Instant>>,
        skipIds: Set<String>,
        onProgress: (done: Int, total: Int) -> Unit,
        onResult: (LibraryScan) -> Unit,
    ) = onResult(LibraryScan.Unavailable)

    /**
     * Looks up which of the photos with these library ids are favorites (the heart) in the photo library. Asks for
     * access to the library the first time; only where [canScanLibrary].
     */
    fun favoriteIds(sourceIds: Set<String>, onResult: (FavoriteLookup) -> Unit) = onResult(FavoriteLookup.Unavailable)
}

sealed interface FavoriteLookup {
    /** [favorites] are the ids that are hearted; [unchecked] ids were not found in the library (deleted, or not from it). */
    data class Found(val favorites: Set<String>, val unchecked: Int) : FavoriteLookup
    data object Denied : FavoriteLookup
    data object Unavailable : FavoriteLookup
}

sealed interface LibraryScan {
    /** [items] were copied; [failed] could not be read (e.g. an iCloud photo that would not download). */
    data class Found(val items: List<PickedMedia>, val failed: Int) : LibraryScan
    data object Denied : LibraryScan
    data object Unavailable : LibraryScan
}

/** The picker for this platform, or null where the app keeps no photos or videos (the web build). */
@Composable
expect fun rememberMediaPicker(): MediaPicker?
