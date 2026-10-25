package org.jetbrains.kotlinconf.export

import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.contactLink
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.personal.SessionRating
import org.jetbrains.kotlinconf.personal.Thumb
import org.jetbrains.kotlinconf.utils.DateTimeFormatting

/**
 * The same report as [tripReportMarkdown], as one HTML page that opens in any browser with the photos
 * shown and the videos playable. It loads nothing from the network: links to slides and media are
 * relative to the page, inside the report bundle.
 */
fun tripReportHtml(
    sessions: List<SessionCardView>,
    speakersById: Map<SpeakerId, Speaker>,
    notes: PersonalNotes,
    ratings: Map<String, SessionRating>,
    met: Set<SpeakerId>,
    event: ExportEvent,
    labels: ExportLabels = ExportLabels(),
    slideLink: (SessionCardView) -> String? = { it.slidesUrl },
    media: (SessionCardView) -> List<ReportMedia> = { emptyList() },
): String = buildString {
    val title = "${labels.tripReportHeading}: ${event.name}".html()
    appendLine("<!doctype html>")
    appendLine("<html><head><meta charset=\"utf-8\">")
    appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
    appendLine("<title>$title</title>")
    appendLine("<style>$STYLE</style>")
    appendLine("</head><body><main>")
    appendLine("<h1>$title</h1>")
    byDay(sessions).forEach { (date, daySessions) ->
        appendLine("<h2>${DateTimeFormatting.weekdayDate(date).html()}</h2>")
        daySessions.forEach { s ->
            appendLine("<section>")
            appendLine("<h3>${s.title.trim().html()}</h3>")
            val where = s.locationLine.takeIf { it.isNotBlank() }?.let { " · ${it.html()}" }.orEmpty()
            appendLine("<p class=\"meta\">${DateTimeFormatting.timeToTime(s.startsAt, s.endsAt).html()}$where</p>")
            val speakers = s.speakerIds.mapNotNull { speakersById[it] }
            if (speakers.isNotEmpty()) {
                appendLine("<p><b>${labels.speakers.html()}:</b> ${speakers.joinToString(", ") { it.nameWithCompany().html() }}</p>")
            }
            val rating = ratings[s.id.id]
            val thumb = rating?.thumb?.let { if (it == Thumb.UP) labels.thumbUp else labels.thumbDown }
            val takeaway = rating?.takeaway?.takeIf { it.isNotBlank() }?.let { "“$it”" }
            if (thumb != null || takeaway != null) {
                appendLine("<p><b>${labels.rating.html()}:</b> ${listOfNotNull(thumb, takeaway).joinToString(" ").html()}</p>")
            }
            val links = listOfNotNull(
                slideLink(s)?.let { "<a href=\"${it.attr()}\">${labels.slides.html()}</a>" },
                s.videoUrl?.takeIf { it.isWebLink() }?.let { "<a href=\"${it.attr()}\">${labels.recording.html()}</a>" },
            )
            if (links.isNotEmpty()) appendLine("<p class=\"links\">${links.joinToString(" · ")}</p>")
            notes.sessionNote(s.id).takeIf { it.isNotBlank() }?.let {
                appendLine("<p><b>${labels.myNotes.html()}:</b></p>")
                appendLine("<p class=\"note\">${it.trim().html().replace("\n", "<br>")}</p>")
            }
            val items = media(s)
            if (items.isNotEmpty()) {
                appendLine("<div class=\"media\">")
                items.withNumbers().forEach { (item, number) ->
                    val label = "${labels.mediaLabel(item.kind)} $number".html()
                    val src = item.path.attr()
                    if (item.kind == MediaKind.PHOTO) {
                        // A cleaned-up photo links to its original
                        val href = (item.original ?: item.path).attr()
                        appendLine("<a href=\"$href\"><img src=\"$src\" alt=\"$label\" loading=\"lazy\"></a>")
                    } else {
                        appendLine("<video src=\"$src\" controls preload=\"metadata\" title=\"$label\"></video>")
                    }
                }
                appendLine("</div>")
            }
            appendLine("</section>")
        }
    }
    val people = met.mapNotNull { speakersById[it] }.sortedBy { it.name.lowercase() }
    if (people.isNotEmpty()) {
        appendLine("<h2>${labels.peopleMet.html()}</h2>")
        appendLine("<ul>")
        people.forEach { p ->
            val name = p.name.html()
            val link = p.contactLink
            val position = p.position.takeIf { it.isNotBlank() }?.let { ", ${it.html()}" }.orEmpty()
            val note = notes.speakerNote(p.id).takeIf { it.isNotBlank() }
                ?.let { "<br><span class=\"note\">${it.trim().html().replace("\n", "<br>")}</span>" }.orEmpty()
            appendLine("<li><b>${if (link != null && link.url.isWebLink()) "<a href=\"${link.url.attr()}\">$name</a>" else name}</b>$position$note</li>")
        }
        appendLine("</ul>")
    }
    appendLine("</main></body></html>")
}

/** Only web links go into an href: a feed could otherwise slip a script link into a page opened from disk. */
private fun String.isWebLink(): Boolean = startsWith("https://") || startsWith("http://")

private fun String.html(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun String.attr(): String = html().replace("\"", "&quot;")

private const val STYLE = "body{margin:0;background:#fff;color:#19191c;font:16px/1.5 -apple-system,system-ui,sans-serif}" +
    "main{max-width:760px;margin:0 auto;padding:16px}" +
    "h1{font-size:1.6em;line-height:1.2}h2{margin-top:2em;border-bottom:1px solid #ddd}" +
    "section{margin:1.5em 0}h3{margin-bottom:.2em}.meta{margin-top:0;color:#666}" +
    ".note{white-space:normal;color:#333}a{color:#c2003e}" +
    ".media{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:8px}" +
    ".media img,.media video{width:100%;border-radius:8px;background:#eee}" +
    "@media (prefers-color-scheme:dark){body{background:#19191c;color:#eee}.meta{color:#aaa}" +
    ".note{color:#ccc}h2{border-color:#444}a{color:#ff6a8f}}"
