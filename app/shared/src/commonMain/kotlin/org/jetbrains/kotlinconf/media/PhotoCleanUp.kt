package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind

/** How far the background clean-up of the attendee's photos has got: [done] of [total] photos are finished. */
data class PhotoCleanUp(val done: Int, val total: Int) {
    val working: Boolean get() = done < total
}

/**
 * The progress of the clean-up over all [attachments]: a photo is finished once [needsCleanUp] is false for it.
 * Null when there is no photo, so the page has nothing to say.
 */
fun cleanUpProgress(attachments: Collection<Attachment>, needsCleanUp: (Attachment) -> Boolean): PhotoCleanUp? {
    val photos = attachments.filter { it.kind == MediaKind.PHOTO }
    if (photos.isEmpty()) return null
    return PhotoCleanUp(done = photos.count { !needsCleanUp(it) }, total = photos.size)
}
