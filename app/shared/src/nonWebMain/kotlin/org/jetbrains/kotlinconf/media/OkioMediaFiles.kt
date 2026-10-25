package org.jetbrains.kotlinconf.media

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.use
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.jetbrains.kotlinconf.di.FileStorageDir
import org.jetbrains.kotlinconf.di.Year
import org.jetbrains.kotlinconf.di.YearScope
import kotlin.random.Random

/** Photos and videos in `<app files>/<year>/media`, next to the other files the app keeps for the event. */
@ContributesBinding(YearScope::class)
@SingleIn(YearScope::class)
class OkioMediaFiles(
    @Year year: Int,
    @FileStorageDir storageDir: String,
) : MediaFiles {
    private val fileSystem = FileSystem.SYSTEM
    private val baseDir: Path = storageDir.toPath() / year.toString() / "media"

    override val isSupported: Boolean = true

    override fun path(file: String): String = resolve(file).toString()

    override fun newName(extension: String): String {
        val ext = extension.lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "bin" }
        return "${Random.nextLong().toULong().toString(16)}.$ext"
    }

    override suspend fun hashOf(path: String): String? = withContext(Dispatchers.IO) {
        try {
            HashingSource.sha256(fileSystem.source(path.toPath())).use { hashing ->
                hashing.buffer().readAll(blackholeSink())
                hashing.hash.hex()
            }
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun adopt(tempPath: String, extension: String): String? = withContext(Dispatchers.IO) {
        val name = newName(extension)
        val source = tempPath.toPath()
        val target = resolve(name)
        try {
            fileSystem.createDirectories(baseDir)
            try {
                fileSystem.atomicMove(source, target)
            } catch (_: IOException) {
                // Another volume: copy, then drop the temporary file
                fileSystem.copy(source, target)
                fileSystem.delete(source, mustExist = false)
            }
            name
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun delete(file: String) = withContext(Dispatchers.IO) {
        try {
            fileSystem.delete(resolve(file), mustExist = false)
        } catch (_: IOException) {
        }
    }

    override suspend fun discard(tempPath: String) = withContext(Dispatchers.IO) {
        try {
            fileSystem.delete(tempPath.toPath(), mustExist = false)
        } catch (_: IOException) {
        }
    }

    override suspend fun size(file: String): Long = withContext(Dispatchers.IO) {
        try {
            fileSystem.metadataOrNull(resolve(file))?.size ?: 0L
        } catch (_: IOException) {
            0L
        }
    }

    private fun resolve(file: String): Path {
        require(file.matches(VALID_NAME)) { "Invalid media file name: '$file'" }
        return baseDir / file
    }

    private companion object {
        val VALID_NAME = Regex("^[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+$")
    }
}
