package org.jetbrains.kotlinconf.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinconf.personal.MediaKind
import java.io.File
import java.util.UUID

/** What a launched picker should report back to; the launchers outlive a single request. */
private class Pending {
    var library: ((List<PickedMedia>) -> Unit)? = null
    var loading: (() -> Unit)? = null
    var capture: Pair<File, (List<PickedMedia>) -> Unit>? = null
}

@Composable
actual fun rememberMediaPicker(): MediaPicker? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { Pending() }

    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val done = pending.library ?: return@rememberLauncherForActivityResult
        pending.library = null
        if (uris.isEmpty()) return@rememberLauncherForActivityResult done(emptyList())
        pending.loading?.invoke()
        scope.launch {
            done(withContext(Dispatchers.IO) { uris.mapNotNull { copyFromUri(context, it) } })
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val (file, done) = pending.capture ?: return@rememberLauncherForActivityResult
        pending.capture = null
        done(if (saved && file.length() > 0) listOf(PickedMedia(file.path, MediaKind.PHOTO, "jpg")) else emptyList<PickedMedia>().also { file.delete() })
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        val (file, done) = pending.capture ?: return@rememberLauncherForActivityResult
        pending.capture = null
        if (!saved || file.length() == 0L) {
            file.delete()
            return@rememberLauncherForActivityResult done(emptyList())
        }
        scope.launch {
            val still = withContext(Dispatchers.IO) { videoStill(context, file.path) }
            done(listOf(PickedMedia(file.path, MediaKind.VIDEO, "mp4", still)))
        }
    }

    return remember(context) {
        object : MediaPicker {
            override val hasCamera: Boolean =
                context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)

            override fun takePhoto(onPicked: (List<PickedMedia>) -> Unit) {
                val file = captureFile(context, "jpg")
                pending.capture = file to onPicked
                photo.launch(shareUri(context, file))
            }

            override fun recordVideo(onPicked: (List<PickedMedia>) -> Unit) {
                val file = captureFile(context, "mp4")
                pending.capture = file to onPicked
                video.launch(shareUri(context, file))
            }

            override fun pickFromLibrary(onLoading: () -> Unit, onPicked: (List<PickedMedia>) -> Unit) {
                pending.library = onPicked
                pending.loading = onLoading
                library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }

            override fun open(path: String, kind: MediaKind) {
                val file = File(path)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                    ?: if (kind == MediaKind.VIDEO) "video/*" else "image/*"
                val view = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(shareUri(context, file), mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

// The provider is declared in the app's manifest; its paths are in res/xml/share_file_paths.xml
private fun shareUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + ".shareprovider", file)

private fun captureFile(context: Context, extension: String): File {
    val dir = File(context.cacheDir, "capture").apply { mkdirs() }
    return File(dir, "${UUID.randomUUID()}.$extension")
}

private fun copyFromUri(context: Context, uri: Uri): PickedMedia? {
    val mime = context.contentResolver.getType(uri).orEmpty()
    val kind = if (mime.startsWith("video/")) MediaKind.VIDEO else MediaKind.PHOTO
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        ?: if (kind == MediaKind.VIDEO) "mp4" else "jpg"
    val file = captureFile(context, extension)
    val copied = context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { input.copyTo(it) }
        true
    } ?: false
    if (!copied) return null
    val still = if (kind == MediaKind.VIDEO) videoStill(context, file.path) else null
    return PickedMedia(file.path, kind, extension, still)
}

/** A small still from the first second of a video, for the list on the session page. */
private fun videoStill(context: Context, path: String): String? = try {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
        frame?.let { bitmap ->
            val scale = 480f / maxOf(bitmap.width, bitmap.height)
            val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
            val file = captureFile(context, "jpg")
            file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            file.path
        }
    } finally {
        retriever.release()
    }
} catch (_: Exception) {
    null
}
