package org.jetbrains.kotlinconf.personal

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinconf.SessionId

@Serializable
enum class MediaKind { PHOTO, VIDEO }

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
    /** A still picture for a video; photos are shown from [file] itself. */
    val thumbnail: String? = null,
    /** When it was added, in milliseconds since 1970; attachments are shown and exported in this order. */
    val addedAt: Long = 0,
) {
    /** The picture to show in a list: the video's still or the photo. */
    val preview: String? get() = if (kind == MediaKind.VIDEO) thumbnail else file
}

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
