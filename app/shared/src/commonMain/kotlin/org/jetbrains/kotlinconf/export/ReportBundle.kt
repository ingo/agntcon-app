package org.jetbrains.kotlinconf.export

import kotlinx.datetime.number
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.personal.Attachment

/**
 * Where each session's slides and the attendee's photos and videos go inside the report bundle:
 * `slides/<session>.pdf` and `media/<session>/01.jpg`, where `<session>` is the date, start time
 * and title, so the folders sort in the order the sessions happened.
 */
class ReportLayout(
    sessions: List<SessionCardView>,
    private val attachments: Map<String, List<Attachment>>,
) {
    private val folders: Map<String, String> = buildMap {
        val used = mutableSetOf<String>()
        sessions.sortedWith(compareBy({ it.startsAt }, { it.title })).forEach { s ->
            val start = s.startsAt
            val base = "${start.date}-${start.hour.pad()}${start.minute.pad()}-${slug(s.title)}".trimEnd('-')
            val name = if (used.add(base)) base else "$base-${slug(s.id.id)}".also { used.add(it) }
            put(s.id.id, name)
        }
    }

    fun folder(session: SessionCardView): String = folders[session.id.id] ?: slug(session.id.id)

    /** Where the session's slides go, or null when it has none. */
    fun slidesPath(session: SessionCardView): String? =
        session.slidesUrl?.let { "slides/${folder(session)}.${extensionOf(it, default = "pdf")}" }

    /**
     * The session's photos and videos, in the order they were taken (the order they were added where the time is
     * unknown, a stable sort so ties keep it), with their place in the bundle.
     */
    fun media(session: SessionCardView): List<Pair<Attachment, String>> =
        attachments[session.id.id].orEmpty().sortedBy { it.orderTime }.mapIndexed { index, attachment ->
            attachment to "media/${folder(session)}/${(index + 1).pad()}.${extensionOf(attachment.cleaned ?: attachment.file, default = "bin")}"
        }

    /** Where the text read from a photo goes: next to it, with its name (`01.jpg` and `01.txt`). */
    fun textPath(mediaPath: String): String = mediaPath.substringBeforeLast('.') + ".txt"

    /**
     * Where the original of a cleaned-up photo goes, next to the cleaned-up copy: `01-original.jpg`. Null for a photo
     * that was not cleaned up (its original is the one at [media]'s path).
     */
    fun originalPath(attachment: Attachment, mediaPath: String): String? = attachment.cleaned?.let {
        mediaPath.substringBeforeLast('.') + "-original." + extensionOf(attachment.file, default = "jpg")
    }

    companion object {
        /** Lower-case letters and digits joined by dashes, at most 48 characters. */
        fun slug(text: String): String = text.lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(48)
            .trimEnd('-')

        /** The file type at the end of a name or Sessionize download link (`…/x.pdf~name.pdf`), or [default]. */
        fun extensionOf(name: String, default: String): String =
            name.substringAfterLast('/').substringAfterLast('.', "").lowercase()
                .takeIf { it.isNotEmpty() && it.length <= 5 && it.all { c -> c in 'a'..'z' || c in '0'..'9' } }
                ?: default

        private fun Int.pad(): String = toString().padStart(2, '0')
    }
}

/** What the attendee's report bundle is made from. [mediaPath] gives a stored photo's or video's absolute path. */
class ReportInput(
    val snapshot: ExportSnapshot,
    val event: ExportEvent,
    val labels: ExportLabels,
    val mediaPath: (String) -> String,
)

sealed interface ReportProgress {
    data class Slides(val done: Int, val total: Int) : ReportProgress
    data object Packing : ReportProgress
}

sealed interface ReportResult {
    /** The bundle is at [path]. Slides that could not be downloaded are linked instead ([slidesLinked]). */
    data class Done(
        val path: String,
        val fileName: String,
        val sessions: Int,
        val slidesIncluded: Int,
        val slidesLinked: Int,
        val photos: Int,
        val videos: Int,
        val bytes: Long,
    ) : ReportResult

    /** A zip file holds at most 4 GB; this report would be bigger. */
    data object TooLarge : ReportResult

    data object Failed : ReportResult
}

/** Builds the downloadable report: a zip with the report in Markdown and HTML, the slides and the attendee's media. */
interface ReportBundleBuilder {
    suspend fun build(input: ReportInput, onProgress: (ReportProgress) -> Unit): ReportResult
}

/** The builder for this platform, or null where the app cannot write files (the web build). */
expect val reportBundleBuilder: ReportBundleBuilder?

/** The file name of the bundle, e.g. `agntcon-mcpcon-na-2026-trip-report.zip`. */
fun reportFileName(event: ExportEvent): String = "${ReportLayout.slug(event.id).ifEmpty { "event" }}-trip-report.zip"

/** The date the report was made, for the zip entries. */
internal fun dosDateTime(dateTime: kotlinx.datetime.LocalDateTime): Pair<Int, Int> {
    val time = (dateTime.hour shl 11) or (dateTime.minute shl 5) or (dateTime.second / 2)
    val date = ((dateTime.year - 1980).coerceIn(0, 127) shl 9) or (dateTime.month.number shl 5) or dateTime.day
    return date to time
}
