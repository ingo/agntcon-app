package org.jetbrains.kotlinconf.export

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.contactLink
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.personal.SessionRating
import org.jetbrains.kotlinconf.personal.Thumb
import org.jetbrains.kotlinconf.utils.DateTimeFormatting
import kotlin.time.Instant

/** The words in exported text, so the app's language is used (the dates stay in English like the rest of the app). */
data class ExportLabels(
    val speakers: String = "Speakers",
    val room: String = "Room",
    val myNotes: String = "My notes",
    val rating: String = "Rating",
    val thumbUp: String = "👍",
    val thumbDown: String = "👎",
    val slides: String = "Slides",
    val recording: String = "Recording",
    val scheduleHeading: String = "My schedule",
    val tripReportHeading: String = "Trip report",
    val peopleMet: String = "People I met",
    val noSessions: String = "No sessions",
    val myMedia: String = "My photos and videos",
    val photo: String = "Photo",
    val video: String = "Video",
    val original: String = "original",
    val highlights: String = "Highlights",
    val highlight: String = "Highlight",
    val textOn: String = "Text on",
)

/** A photo or video in the report bundle, by its path inside the bundle. */
data class ReportMedia(
    val kind: MediaKind,
    val path: String,
    val original: String? = null,
    /** The words on the photo, and where they are saved as a text file in the bundle. */
    val text: String? = null,
    val textPath: String? = null,
    /** True for a starred photo: it is also shown in the Highlights section and larger in its session. */
    val highlighted: Boolean = false,
)

/** A starred photo with the session it was taken in and its number there (Photo 3), for the Highlights section. */
internal data class ReportHighlight(val session: SessionCardView, val item: ReportMedia, val number: Int)

/** The starred photos of every session, sessions in time order and photos in the order of the report. */
internal fun highlightsOf(sessions: List<SessionCardView>, media: (SessionCardView) -> List<ReportMedia>): List<ReportHighlight> =
    byDay(sessions).flatMap { (_, daySessions) ->
        daySessions.flatMap { s ->
            media(s).withNumbers().filter { (item, _) -> item.highlighted && item.kind == MediaKind.PHOTO }
                .map { (item, number) -> ReportHighlight(s, item, number) }
        }
    }

/** The event a file or text is about. */
data class ExportEvent(
    val id: String,
    val name: String,
    val timeZone: TimeZone,
    val venueAddress: String? = null,
)

// ---- Calendar (.ics) -------------------------------------------------------------------------

/**
 * An iCalendar file (RFC 5545) with one event per session. Times are written in UTC so no
 * VTIMEZONE block is needed and every calendar app places them correctly.
 */
fun buildIcs(
    sessions: List<SessionCardView>,
    speakersById: Map<SpeakerId, Speaker>,
    event: ExportEvent,
    stamp: Instant,
): String {
    val lines = mutableListOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//${event.name.icsText()}//Schedule//EN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:${event.name.icsText()}",
    )
    sessions.sortedWith(compareBy({ it.startsAt }, { it.endsAt }, { it.title })).forEach { session ->
        val location = listOfNotNull(session.locationLine.takeIf { it.isNotBlank() }, event.venueAddress).joinToString(", ")
        val speakers = session.speakerIds.mapNotNull { speakersById[it] }
        val description = buildList {
            if (speakers.isNotEmpty()) add(speakers.joinToString(", ") { it.name })
            if (session.description.isNotBlank()) add(session.description.trim().take(ICS_DESCRIPTION_LIMIT))
            add(event.name)
        }.joinToString("\n\n")

        lines += "BEGIN:VEVENT"
        lines += "UID:session-${session.id.id}@${event.id}"
        lines += "DTSTAMP:${stamp.icsUtc()}"
        lines += "DTSTART:${session.startsAt.toInstant(event.timeZone).icsUtc()}"
        lines += "DTEND:${session.endsAt.toInstant(event.timeZone).icsUtc()}"
        lines += "SUMMARY:${session.title.icsText()}"
        if (location.isNotEmpty()) lines += "LOCATION:${location.icsText()}"
        lines += "DESCRIPTION:${description.icsText()}"
        lines += "END:VEVENT"
    }
    lines += "END:VCALENDAR"
    return lines.joinToString("") { it.foldIcsLine() + "\r\n" }
}

/** A name for the file that is safe on every platform: "agntcon-mcpcon-na-2026-schedule.ics". */
fun icsFileName(event: ExportEvent, suffix: String): String =
    "${event.id.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}-$suffix.ics"

private const val ICS_DESCRIPTION_LIMIT = 1000

private fun Instant.icsUtc(): String {
    val t = toLocalDateTime(TimeZone.UTC)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${t.year.toString().padStart(4, '0')}${two(t.month.number)}${two(t.day)}T${two(t.hour)}${two(t.minute)}${two(t.second)}Z"
}

/** Escapes a TEXT value: backslash, semicolon, comma and line breaks. */
internal fun String.icsText(): String = buildString {
    for (c in this@icsText) when (c) {
        '\\' -> append("\\\\")
        ';' -> append("\\;")
        ',' -> append("\\,")
        '\n' -> append("\\n")
        '\r' -> {}
        else -> append(c)
    }
}

/** Folds a content line at 75 octets (RFC 5545 §3.1): continuation lines start with a space. */
internal fun String.foldIcsLine(): String {
    if (utf8Length() <= 75) return this
    val out = StringBuilder()
    var lineBytes = 0
    var limit = 75
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        val chars = if (cp > 0xFFFF) 2 else 1
        val bytes = when {
            cp < 0x80 -> 1
            cp < 0x800 -> 2
            cp < 0x10000 -> 3
            else -> 4
        }
        if (lineBytes + bytes > limit) {
            out.append("\r\n ")
            lineBytes = 0
            limit = 74 // the leading space counts
        }
        out.append(this, i, i + chars)
        lineBytes += bytes
        i += chars
    }
    return out.toString()
}

private fun String.utf8Length(): Int = encodeToByteArray().size

private fun String.codePointAt(index: Int): Int {
    val c = this[index]
    if (c.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
        return 0x10000 + ((c.code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00)
    }
    return c.code
}

// ---- Share as text ---------------------------------------------------------------------------

/** One session as a short message: title, when, where, who, and the event. */
fun sessionShareText(
    session: SessionCardView,
    speakersById: Map<SpeakerId, Speaker>,
    event: ExportEvent,
    labels: ExportLabels = ExportLabels(),
): String = buildString {
    appendLine(session.title)
    appendLine("${DateTimeFormatting.weekdayDate(session.startsAt.date)}, ${DateTimeFormatting.timeToTime(session.startsAt, session.endsAt)}")
    if (session.locationLine.isNotBlank()) appendLine("${labels.room}: ${session.locationLine}")
    val speakers = session.speakerIds.mapNotNull { speakersById[it] }
    if (speakers.isNotEmpty()) appendLine("${labels.speakers}: ${speakers.joinToString(", ") { it.nameWithCompany() }}")
    append(event.name)
}

/** The schedule as text: a heading per day, one line per session in time order. */
fun scheduleShareText(
    sessions: List<SessionCardView>,
    event: ExportEvent,
    labels: ExportLabels = ExportLabels(),
): String = buildString {
    appendLine("${labels.scheduleHeading} – ${event.name}")
    if (sessions.isEmpty()) {
        append(labels.noSessions)
        return@buildString
    }
    byDay(sessions).forEach { (date, daySessions) ->
        appendLine()
        appendLine(DateTimeFormatting.weekdayDate(date))
        daySessions.forEach { s ->
            val where = s.locationLine.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            appendLine("${DateTimeFormatting.timeToTime(s.startsAt, s.endsAt)}  ${s.title}$where")
        }
    }
}.trimEnd()

// ---- Trip report (Markdown) ------------------------------------------------------------------

/**
 * Bookmarked sessions, plus any the attendee wrote a note on, rated or attached photos or videos to (they may not
 * have bookmarked what they dropped in on).
 */
fun sessionsForTripReport(
    all: List<SessionCardView>,
    notes: PersonalNotes,
    ratings: Map<String, SessionRating>,
    attachments: Map<String, List<Attachment>> = emptyMap(),
): List<SessionCardView> = all.filter {
    it.isFavorite || notes.sessionNote(it.id).isNotBlank() || ratings[it.id.id]?.isEmpty == false ||
        !attachments[it.id.id].isNullOrEmpty()
}.distinctBy { it.id }

/**
 * The attendee's report on the event, in Markdown: bookmarked sessions in time order with their notes,
 * rating, takeaway and slides, then the people they met with notes and links.
 *
 * In the downloaded report bundle [slideLink] points at the slides inside the bundle and [media] lists the
 * attendee's photos and videos; the shared text keeps the Sessionize links and has no media.
 */
fun tripReportMarkdown(
    bookmarked: List<SessionCardView>,
    speakersById: Map<SpeakerId, Speaker>,
    notes: PersonalNotes,
    ratings: Map<String, SessionRating>,
    met: Set<SpeakerId>,
    event: ExportEvent,
    labels: ExportLabels = ExportLabels(),
    slideLink: (SessionCardView) -> String? = { it.slidesUrl },
    media: (SessionCardView) -> List<ReportMedia> = { emptyList() },
): String = buildString {
    appendLine("# ${labels.tripReportHeading}: ${event.name}")
    val highlights = highlightsOf(bookmarked, media)
    if (highlights.isNotEmpty()) {
        appendLine()
        appendLine("## ${labels.highlights}")
        highlights.forEach { (session, item, number) ->
            appendLine()
            val original = item.original?.let { " ([${labels.original}]($it))" }.orEmpty()
            val caption = "${session.title.mdInline()} · ${DateTimeFormatting.timeToTime(session.startsAt, session.endsAt)}"
            appendLine("- ![${labels.photo} $number](${item.path}) **$caption**$original")
        }
    }
    byDay(bookmarked).forEach { (date, sessions) ->
        appendLine()
        appendLine("## ${DateTimeFormatting.weekdayDate(date)}")
        sessions.forEach { s ->
            appendLine()
            appendLine("### ${s.title.mdInline()}")
            appendLine()
            val where = s.locationLine.takeIf { it.isNotBlank() }?.let { " · ${it.mdInline()}" }.orEmpty()
            appendLine("- ${DateTimeFormatting.timeToTime(s.startsAt, s.endsAt)}$where")
            val speakers = s.speakerIds.mapNotNull { speakersById[it] }
            if (speakers.isNotEmpty()) {
                appendLine("- **${labels.speakers}:** ${speakers.joinToString(", ") { it.nameWithCompany().mdInline() }}")
            }
            val rating = ratings[s.id.id]
            val thumb = rating?.thumb?.let { if (it == Thumb.UP) labels.thumbUp else labels.thumbDown }
            val takeaway = rating?.takeaway?.takeIf { it.isNotBlank() }?.let { "“${it.mdInline()}”" }
            if (thumb != null || takeaway != null) {
                appendLine("- **${labels.rating}:** ${listOfNotNull(thumb, takeaway).joinToString(" ")}")
            }
            slideLink(s)?.let { appendLine("- [${labels.slides}]($it)") }
            s.videoUrl?.let { appendLine("- [${labels.recording}]($it)") }
            notes.sessionNote(s.id).takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("**${labels.myNotes}:**")
                appendLine()
                appendLine(it.mdBlock())
            }
            val items = media(s)
            if (items.isNotEmpty()) {
                appendLine()
                appendLine("**${labels.myMedia}:**")
                items.withNumbers().forEach { (item, number) ->
                    appendLine()
                    val label = "${labels.mediaLabel(item.kind)} $number"
                    val original = item.original?.let { " ([${labels.original}]($it))" }.orEmpty()
                    val star = if (item.highlighted && item.kind == MediaKind.PHOTO) " ★ ${labels.highlight}" else ""
                    appendLine(if (item.kind == MediaKind.PHOTO) "![$label](${item.path})$original$star" else "[$label](${item.path})")
                    item.text?.takeIf { it.isNotBlank() }?.let { text ->
                        // The text file has the photo's name, so each text stays linked to its photo
                        val file = item.textPath?.let { " ([${it.substringAfterLast('/')}]($it))" }.orEmpty()
                        appendLine()
                        appendLine("> **${labels.textOn} $label**$file")
                        appendLine(">")
                        text.mdBlock().lines().forEach { line -> appendLine("> ${line.trimEnd()}  ") }
                    }
                }
            }
        }
    }
    val people = met.mapNotNull { speakersById[it] }.sortedBy { it.name.lowercase() }
    if (people.isNotEmpty()) {
        appendLine()
        appendLine("## ${labels.peopleMet}")
        people.forEach { p ->
            appendLine()
            val link = p.contactLink
            val name = p.name.mdInline()
            appendLine("- **${if (link != null) "[$name](${link.url})" else name}**" + p.position.takeIf { it.isNotBlank() }?.let { ", ${it.mdInline()}" }.orEmpty())
            notes.speakerNote(p.id).takeIf { it.isNotBlank() }?.let { appendLine(it.mdBlock().prependIndent("  ")) }
        }
    }
}.trimEnd() + "\n"

/** Photos and videos are numbered separately, as on the session page: Photo 1, Photo 2, Video 1. */
internal fun List<ReportMedia>.withNumbers(): List<Pair<ReportMedia, Int>> {
    val counts = mutableMapOf<MediaKind, Int>()
    return map { item -> item to counts.getOrElse(item.kind) { 0 }.plus(1).also { counts[item.kind] = it } }
}

internal fun ExportLabels.mediaLabel(kind: MediaKind): String = if (kind == MediaKind.PHOTO) photo else video

internal fun byDay(sessions: List<SessionCardView>): List<Pair<LocalDate, List<SessionCardView>>> =
    sessions.sortedWith(compareBy({ it.startsAt }, { it.endsAt }, { it.title }))
        .groupBy { it.startsAt.date }
        .toList()

fun Speaker.nameWithCompany(): String =
    if (company.isBlank()) name else "$name ($company)"

/** Keeps a title or name on one line and stops Markdown from reading its characters as formatting. */
private fun String.mdInline(): String = replace('\n', ' ').trim().replace(Regex("""([\\`*_\[\]])"""), "\\\\$1")

/** A note kept as typed, but its lines cannot start a heading or a list that was not meant. */
private fun String.mdBlock(): String = trim().lines().joinToString("\n") { line ->
    if (line.trimStart().let { it.startsWith("#") || it.startsWith("- ") || it.startsWith("* ") || it.startsWith(">") }) "\\$line" else line
}
