package org.jetbrains.kotlinconf.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.kotlinconf.personal.MediaKind
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Composable
actual fun rememberMediaPicker(): MediaPicker? = remember { DesktopMediaPicker }

private val VIDEO_EXTENSIONS = setOf("mov", "mp4", "m4v", "webm", "avi")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic")

/** Desktop: files from disk, no camera. The originals stay where they are; the app keeps copies. */
private object DesktopMediaPicker : MediaPicker {
    override val hasCamera: Boolean = false

    override fun takePhoto(onPicked: (List<PickedMedia>) -> Unit) = onPicked(emptyList())

    override fun recordVideo(onPicked: (List<PickedMedia>) -> Unit) = onPicked(emptyList())

    override fun pickFromLibrary(onLoading: () -> Unit, onPicked: (List<PickedMedia>) -> Unit) {
        val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS + IMAGE_EXTENSIONS }
            isVisible = true
        }
        val files = dialog.files.orEmpty().toList()
        if (files.isNotEmpty()) onLoading()
        onPicked(files.mapNotNull { file ->
            val extension = file.extension.lowercase()
            val kind = when (extension) {
                in VIDEO_EXTENSIONS -> MediaKind.VIDEO
                in IMAGE_EXTENSIONS -> MediaKind.PHOTO
                else -> return@mapNotNull null
            }
            val copy = Files.createTempFile("media", ".$extension")
            Files.copy(file.toPath(), copy, StandardCopyOption.REPLACE_EXISTING)
            PickedMedia(copy.toString(), kind, extension)
        })
    }

    override fun open(path: String, kind: MediaKind) {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(File(path))
    }
}
