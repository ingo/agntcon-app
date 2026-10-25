package org.jetbrains.kotlinconf.media

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import org.jetbrains.kotlinconf.di.YearScope

/** The web build keeps no files of its own, so it offers no photos or videos. */
@ContributesBinding(YearScope::class)
@SingleIn(YearScope::class)
class NoMediaFiles : MediaFiles {
    override val isSupported: Boolean = false
    override fun path(file: String): String = file
    override suspend fun adopt(tempPath: String, extension: String): String? = null
    override suspend fun delete(file: String) {}
    override suspend fun discard(tempPath: String) {}
    override suspend fun size(file: String): Long = 0L
    override suspend fun hashOf(path: String): String? = null
    override suspend fun writeText(name: String, text: String): String? = null
    override fun newName(extension: String): String = "none.$extension"
}
