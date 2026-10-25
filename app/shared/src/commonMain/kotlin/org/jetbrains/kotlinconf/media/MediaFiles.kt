package org.jetbrains.kotlinconf.media

/**
 * The folder on the device that holds the attendee's photos and videos for one event. Files are named
 * by the folder; callers keep only those names (see [org.jetbrains.kotlinconf.personal.Attachment]).
 */
interface MediaFiles {
    /** False where the app cannot keep files of its own (the web build). */
    val isSupported: Boolean

    /** The absolute path of a stored file, to show, open or pack it. */
    fun path(file: String): String

    /**
     * Moves a file the picker made (a temporary copy) into the folder under a new name and returns that
     * name, or null when it could not be moved.
     */
    suspend fun adopt(tempPath: String, extension: String): String?

    suspend fun delete(file: String)

    /** Deletes a temporary file the picker made that was not kept (an absolute path, not a stored name). */
    suspend fun discard(tempPath: String)

    /** The size of a stored file in bytes, 0 when it is missing. */
    suspend fun size(file: String): Long
}
