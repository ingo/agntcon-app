package org.jetbrains.kotlinconf.personal

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinconf.SessionId

@Serializable
enum class MediaKind { PHOTO, VIDEO }

/**
 * How the attendee wants a photo of a screen cleaned up, when the app's own choice (no mode, [Attachment.cleanMode]
 * null) is not right. [ORIGINAL] leaves the photo as it was taken.
 */
@Serializable
enum class CleanUpMode { ORIGINAL, DARK, LIGHT, COLOURFUL }

/**
 * A photo, screenshot or video the attendee attached to a session. [file] and [thumbnail] are names
 * inside the event's media folder (see [org.jetbrains.kotlinconf.media.MediaFiles]), never absolute
 * paths: on iOS the app's folder moves with every update.
 */
@Serializable
data class Attachment(
    val id: String,
    val kind: MediaKind,
    val file: String,
    /** A small preview: a still for a video, a shrunk copy for a photo (of the cleaned-up one, if there is one). */
    val thumbnail: String? = null,
    /** When it was added, in milliseconds since 1970; attachments are shown and exported in this order. */
    val addedAt: Long = 0,
    /** Where it came from (the photo library's id for the item), to recognise the same photo picked again. */
    val sourceId: String? = null,
    /** SHA-256 of the file as stored, to recognise the same file added again from anywhere. */
    val contentHash: String? = null,
    /** A photo of a screen or whiteboard, straightened and cleaned up; the original stays in [file]. */
    val cleaned: String? = null,
    /** True once the clean-up has looked at this photo, whether or not it found a screen to straighten. */
    val cleanChecked: Boolean = false,
    /** Which version of the clean-up looked at it (see [org.jetbrains.kotlinconf.media.SCREEN_CLEAN_UP_VERSION]). */
    val cleanVersion: Int = 0,
    /** True once a small preview was made (or tried) for this photo. */
    val previewChecked: Boolean = false,
    /** The attendee's choice of clean-up for this photo; null lets the app decide. */
    val cleanMode: CleanUpMode? = null,
    /** True when the attendee starred it; the trip report features starred photos. */
    val highlighted: Boolean = false,
    /** When it was taken, in milliseconds since 1970, if the file says so; the report orders photos by it. */
    val takenAt: Long? = null,
) {
    /** When it happened, for ordering: the capture time, else when it was added. */
    val orderTime: Long get() = takenAt ?: addedAt

    /** The picture to show in a list: the small preview, else the photo itself (cleaned up where there is one). */
    val preview: String? get() = if (kind == MediaKind.VIDEO) thumbnail else thumbnail ?: cleaned ?: file

    /** The files to show in the viewer, best first: the cleaned-up photo, then the original. */
    val viewFiles: List<String> get() = listOfNotNull(cleaned, file)

    /** Every file stored for it: the original, the cleaned-up copy and the preview. */
    val files: List<String> get() = listOfNotNull(file, cleaned, thumbnail)
}

/** The same photo with another clean-up chosen: it is cleaned up again, and the new copy replaces the old one. */
fun Attachment.withCleanUpMode(mode: CleanUpMode?): Attachment =
    if (mode == cleanMode) this else copy(cleanMode = mode, cleanChecked = false)

/** The same photo starred or not. */
fun Attachment.withHighlighted(highlighted: Boolean): Attachment =
    if (highlighted == this.highlighted) this else copy(highlighted = highlighted)

/**
 * Stars every photo whose library id is in [favoriteIds] (the photos hearted in the photo library). Only ever adds
 * stars. Gives the new map and how many photos were newly starred.
 */
fun Map<String, List<Attachment>>.withStarsFrom(favoriteIds: Set<String>): Pair<Map<String, List<Attachment>>, Int> {
    var starred = 0
    val next = mapValues { (_, list) ->
        list.map {
            if (it.kind == MediaKind.PHOTO && !it.highlighted && it.sourceId in favoriteIds) {
                starred++
                it.copy(highlighted = true)
            } else it
        }
    }
    return next to starred
}

/** Replaces one attachment wherever it is, e.g. when its cleaned-up copy is ready. */
fun Map<String, List<Attachment>>.withUpdated(attachmentId: String, transform: (Attachment) -> Attachment): Map<String, List<Attachment>> =
    mapValues { (_, list) -> list.map { if (it.id == attachmentId) transform(it) else it } }

/** Adds [added] to a session's attachments, keeping them in the order they were added. */
fun Map<String, List<Attachment>>.withAdded(id: SessionId, added: List<Attachment>): Map<String, List<Attachment>> {
    if (added.isEmpty()) return this
    val list = (this[id.id].orEmpty() + added).distinctBy { it.id }.sortedBy { it.addedAt }
    return this + (id.id to list)
}

/** Removes one attachment; a session left with none is removed too. */
fun Map<String, List<Attachment>>.withRemoved(id: SessionId, attachmentId: String): Map<String, List<Attachment>> {
    val list = this[id.id].orEmpty().filterNot { it.id == attachmentId }
    return if (list.isEmpty()) this - id.id else this + (id.id to list)
}

/**
 * Moves one attachment to another session, e.g. a photo that landed on the wrong talk. Its files stay as they are;
 * a session left with none is removed. Unchanged if [from] does not have it or [to] is [from].
 */
fun Map<String, List<Attachment>>.withMoved(from: SessionId, to: SessionId, attachmentId: String): Map<String, List<Attachment>> {
    if (from == to) return this
    val attachment = this[from.id]?.find { it.id == attachmentId } ?: return this
    return withRemoved(from, attachmentId).withAdded(to, listOf(attachment))
}
