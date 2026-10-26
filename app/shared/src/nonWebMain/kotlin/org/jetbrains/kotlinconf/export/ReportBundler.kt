package org.jetbrains.kotlinconf.export

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.IOException
import org.jetbrains.kotlinconf.SessionCardView
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import org.jetbrains.kotlinconf.personal.MediaKind
import kotlin.random.Random
import kotlin.time.Clock

actual val reportBundleBuilder: ReportBundleBuilder? = OkioReportBundler()

/**
 * Builds the report bundle in the temporary folder (`<tmp>/report/`, which Android's share provider
 * exposes). Slides are downloaded from Sessionize as the report is made; one that cannot be fetched
 * stays a link in the report.
 */
class OkioReportBundler(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val outputDir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "report",
    private val download: suspend (url: String, target: Path) -> Boolean = ::downloadWithKtor,
) : ReportBundleBuilder {

    override suspend fun build(input: ReportInput, onProgress: (ReportProgress) -> Unit): ReportResult =
        withContext(Dispatchers.IO) {
            val work = outputDir / "work-${Random.nextLong().toULong().toString(16)}"
            try {
                buildIn(work, input, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (_: ZipTooLargeException) {
                ReportResult.TooLarge
            } catch (_: Exception) {
                ReportResult.Failed
            } finally {
                try {
                    fileSystem.deleteRecursively(work, mustExist = false)
                } catch (_: IOException) {
                }
            }
        }

    private suspend fun buildIn(work: Path, input: ReportInput, onProgress: (ReportProgress) -> Unit): ReportResult {
        val snap = input.snapshot
        val sessions = sessionsForTripReport(snap.sessions, snap.notes, snap.ratings, snap.attachments)
        val layout = ReportLayout(sessions, snap.attachments)
        fileSystem.createDirectories(work)

        // Slides first: the report links to the copy in the bundle only when the download worked
        val withSlides = sessions.filter { layout.slidesPath(it) != null }
        val downloaded = mutableMapOf<String, Path>()
        withSlides.forEachIndexed { index, session ->
            onProgress(ReportProgress.Slides(index, withSlides.size))
            val url = session.slidesUrl ?: return@forEachIndexed
            val inBundle = layout.slidesPath(session) ?: return@forEachIndexed
            val target = work / "slide-$index"
            if (url.startsWith("https://") && download(url, target)) downloaded[inBundle] = target
        }
        onProgress(ReportProgress.Packing)

        // Only media whose file is still on the device; a cleaned-up photo brings its original along
        val media = sessions.associate { session ->
            session.id.id to layout.media(session).mapNotNull { (attachment, inBundle) ->
                val cleaned = attachment.cleaned?.let { input.mediaPath(it).toPath() }?.takeIf { fileSystem.exists(it) }
                val original = input.mediaPath(attachment.file).toPath().takeIf { fileSystem.exists(it) }
                when {
                    cleaned != null -> BundledMedia(attachment, inBundle, cleaned, original?.let { layout.originalPath(attachment, inBundle)!! to it })
                    original != null -> BundledMedia(attachment.copy(cleaned = null), inBundle.substringBeforeLast('.') + "." + ReportLayout.extensionOf(attachment.file, "bin"), original, null)
                    else -> null
                }
            }
        }
        val slideLink: (SessionCardView) -> String? = { s ->
            layout.slidesPath(s)?.takeIf { it in downloaded } ?: s.slidesUrl
        }
        val reportMedia: (SessionCardView) -> List<ReportMedia> = { s ->
            media[s.id.id].orEmpty().map {
                val text = it.attachment.text?.takeIf { text -> text.isNotBlank() }
                ReportMedia(it.attachment.kind, it.inBundle, it.original?.first, text, text?.let { _ -> layout.textPath(it.inBundle) }, it.attachment.highlighted)
            }
        }
        val markdown = tripReportMarkdown(
            sessions, snap.speakers, snap.notes, snap.ratings, snap.met, input.event, input.labels, slideLink, reportMedia,
        )
        val html = tripReportHtml(
            sessions, snap.speakers, snap.notes, snap.ratings, snap.met, input.event, input.labels, slideLink, reportMedia,
        )

        val files = downloaded.entries.map { it.key to it.value } +
            media.values.flatten().flatMap { listOfNotNull(it.inBundle to it.path, it.original) }
        // The text read from each photo, saved as a text file next to it
        val texts = media.values.flatten().mapNotNull { m ->
            m.attachment.text?.takeIf { it.isNotBlank() }?.let { layout.textPath(m.inBundle) to it.trim() + "\n" }
        }
        val total = files.sumOf { (_, path) -> fileSystem.metadataOrNull(path)?.size ?: 0L } +
            markdown.length * 4L + html.length * 4L + texts.sumOf { it.second.length * 4L }
        if (total >= ZipWriter.LIMIT) return ReportResult.TooLarge

        val fileName = reportFileName(input.event)
        val zip = outputDir / fileName
        val partial = work / fileName
        val (dosDate, dosTime) = dosDateTime(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
        ZipWriter(fileSystem.sink(partial).buffer(), dosDate, dosTime).use { writer ->
            writer.addText("report.md", markdown)
            writer.addText("report.html", html)
            files.forEach { (inBundle, path) -> writer.addFile(inBundle, fileSystem, path) }
            texts.forEach { (inBundle, text) -> writer.addText(inBundle, text) }
            writer.finish()
        }
        fileSystem.delete(zip, mustExist = false)
        fileSystem.atomicMove(partial, zip)

        val all = media.values.flatten()
        return ReportResult.Done(
            path = zip.toString(),
            fileName = fileName,
            sessions = sessions.size,
            slidesIncluded = downloaded.size,
            slidesLinked = withSlides.size - downloaded.size,
            photos = all.count { it.attachment.kind == MediaKind.PHOTO },
            videos = all.count { it.attachment.kind == MediaKind.VIDEO },
            bytes = fileSystem.metadataOrNull(zip)?.size ?: 0L,
        )
    }
}

private class BundledMedia(
    val attachment: org.jetbrains.kotlinconf.personal.Attachment,
    val inBundle: String,
    val path: Path,
    /** The original of a cleaned-up photo: its path in the bundle and on the device. */
    val original: Pair<String, Path>?,
)

/** Streams one file to disk; Sessionize redirects its download links to a CDN, which the client follows. */
private suspend fun downloadWithKtor(url: String, target: Path): Boolean {
    val client = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 180_000
        }
    }
    return try {
        client.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) return@execute false
            val channel = response.bodyAsChannel()
            FileSystem.SYSTEM.sink(target).buffer().use { sink ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) sink.write(buffer, 0, read)
                }
            }
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    } finally {
        client.close()
    }
}
