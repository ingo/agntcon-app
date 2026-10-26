package org.jetbrains.kotlinconf.export

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.asSessionCard
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.screens.formatBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The trip report with the attendee's photos and videos, and where everything goes in the zip. */
class TripReportBundleTest {
    private val event = ExportEvent("agntcon-mcpcon-na-2026", "AGNTCon + MCPCon North America 2026", TimeZone.of("America/Los_Angeles"))

    private fun card(
        id: String,
        title: String,
        start: String,
        day: String = "2026-10-22",
        favorite: Boolean = true,
        slides: String? = null,
        recording: String? = null,
    ): SessionCardView = Session(
        id = SessionId(id),
        title = title,
        description = "",
        speakerIds = emptyList(),
        location = "230C",
        startsAt = LocalDateTime.parse("${day}T$start"),
        endsAt = LocalDateTime.parse("${day}T$start").let { LocalDateTime(it.date, kotlinx.datetime.LocalTime(it.hour, 59)) },
        slidesUrl = slides,
        videoUrl = recording,
    ).asSessionCard(emptyMap(), LocalDateTime(2026, 10, 1, 0, 0), if (favorite) setOf(SessionId(id)) else emptySet())

    private val keynote = card("1", "Opening Keynote: Agents & MCP!", "09:00", slides = "https://sessionize.com/download/x~a.pdf~keynote.pdf")
    private val workshop = card("2", "Hands-on A2A", "14:20", slides = "https://sessionize.com/download/y~b.pptx~deck.pptx")
    private val dropIn = card("3", "Poster walk", "11:00", favorite = false)

    private val attachments = mapOf(
        "1" to listOf(
            Attachment("p1", MediaKind.PHOTO, "p1.jpg", addedAt = 1),
            Attachment("v1", MediaKind.VIDEO, "v1.mov", thumbnail = "v1s.jpg", addedAt = 2),
            Attachment("p2", MediaKind.PHOTO, "p2.png", addedAt = 3),
        ),
        "3" to listOf(Attachment("p3", MediaKind.PHOTO, "p3.jpg", addedAt = 4)),
    )

    @Test
    fun sessionsWithOnlyPhotosAreInTheReport() {
        val sessions = sessionsForTripReport(listOf(keynote, workshop, dropIn), PersonalNotes(), emptyMap(), attachments)

        assertEquals(listOf("1", "2", "3"), sessions.map { it.id.id })
        assertEquals(listOf("1", "2"), sessionsForTripReport(listOf(keynote, workshop, dropIn), PersonalNotes(), emptyMap()).map { it.id.id })
    }

    @Test
    fun filesAreNamedByDayTimeAndTitleSoTheySortInOrder() {
        val layout = ReportLayout(listOf(keynote, workshop, dropIn), attachments)

        assertEquals("2026-10-22-0900-opening-keynote-agents-mcp", layout.folder(keynote))
        assertEquals("slides/2026-10-22-0900-opening-keynote-agents-mcp.pdf", layout.slidesPath(keynote))
        assertEquals("slides/2026-10-22-1420-hands-on-a2a.pptx", layout.slidesPath(workshop))
        assertEquals(null, layout.slidesPath(dropIn))
        assertEquals(
            listOf(
                "media/2026-10-22-0900-opening-keynote-agents-mcp/01.jpg",
                "media/2026-10-22-0900-opening-keynote-agents-mcp/02.mov",
                "media/2026-10-22-0900-opening-keynote-agents-mcp/03.png",
            ),
            layout.media(keynote).map { it.second },
        )
    }

    @Test
    fun twoSessionsWithTheSameTimeAndTitleGetTheirOwnFolders() {
        val a = card("10", "Lunch", "12:00")
        val b = card("11", "Lunch", "12:00")
        val layout = ReportLayout(listOf(a, b), emptyMap())

        assertEquals("2026-10-22-1200-lunch", layout.folder(a))
        assertEquals("2026-10-22-1200-lunch-11", layout.folder(b))
    }

    @Test
    fun slugsAreShortAndSafe() {
        assertEquals("prompt-la-carte", ReportLayout.slug("Prompt à la Carte"))
        assertEquals(48, ReportLayout.slug("a".repeat(100)).length)
        assertEquals("pdf", ReportLayout.extensionOf("https://sessionize.com/download/x~y.pdf~name.pdf", "bin"))
        assertEquals("pdf", ReportLayout.extensionOf("https://example.com/slides", "pdf"))
        assertEquals("bin", ReportLayout.extensionOf("weird.name.with?query=1", "bin"))
    }

    @Test
    fun markdownLinksTheSlidesAndMediaInsideTheBundle() {
        val layout = ReportLayout(listOf(keynote), attachments)
        val report = tripReportMarkdown(
            listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event,
            slideLink = { layout.slidesPath(it) },
            media = { s -> layout.media(s).map { (a, path) -> ReportMedia(a.kind, path) } },
        )

        assertTrue("- [Slides](slides/2026-10-22-0900-opening-keynote-agents-mcp.pdf)" in report, report)
        assertTrue("**My photos and videos:**" in report)
        assertTrue("![Photo 1](media/2026-10-22-0900-opening-keynote-agents-mcp/01.jpg)" in report)
        assertTrue("[Video 1](media/2026-10-22-0900-opening-keynote-agents-mcp/02.mov)" in report)
        assertTrue("![Photo 2](media/2026-10-22-0900-opening-keynote-agents-mcp/03.png)" in report)
    }

    @Test
    fun theSharedTextStaysAsItWas() {
        val report = tripReportMarkdown(listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event)

        assertTrue("- [Slides](https://sessionize.com/download/x~a.pdf~keynote.pdf)" in report)
        assertFalse("photos and videos" in report)
    }

    @Test
    fun htmlShowsPhotosPlaysVideosAndEscapesText() {
        val note = PersonalNotes().withSessionNote(SessionId("1"), "<script>alert(1)</script>\nsecond line")
        val sneaky = card("4", "Recorded", "16:00", recording = "javascript:alert(1)")
        val html = tripReportHtml(
            listOf(keynote, sneaky), emptyMap(), note, emptyMap(), emptySet(), event,
            media = { s -> if (s.id.id == "1") listOf(ReportMedia(MediaKind.PHOTO, "media/a/01.jpg"), ReportMedia(MediaKind.VIDEO, "media/a/02.mov")) else emptyList() },
        )

        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue("<img src=\"media/a/01.jpg\" alt=\"Photo 1\"" in html)
        assertTrue("<video src=\"media/a/02.mov\" controls" in html)
        assertTrue("Opening Keynote: Agents &amp; MCP!" in html)
        assertTrue("&lt;script&gt;alert(1)&lt;/script&gt;<br>second line" in html)
        assertFalse("<script>" in html)
        assertFalse("javascript:" in html, "only web links become links")
    }

    @Test
    fun sizesReadNaturally() {
        assertEquals("1 KB", formatBytes(10))
        assertEquals("840 KB", formatBytes(840L * 1024))
        assertEquals("3.2 MB", formatBytes((3.2 * 1024 * 1024).toLong()))
        assertEquals("120 MB", formatBytes(120L * 1024 * 1024))
        assertEquals("1.5 GB", formatBytes(1536L * 1024 * 1024))
    }

    @Test
    fun aCleanedUpPhotoIsTheImageAndItsOriginalSitsNextToIt() {
        val cleaned = Attachment("p", MediaKind.PHOTO, "p.heic", cleaned = "p-clean.jpg", cleanChecked = true, addedAt = 1)
        val layout = ReportLayout(listOf(keynote), mapOf("1" to listOf(cleaned)))
        val (attachment, path) = layout.media(keynote).single()

        assertEquals("media/2026-10-22-0900-opening-keynote-agents-mcp/01.jpg", path)
        assertEquals("media/2026-10-22-0900-opening-keynote-agents-mcp/01-original.heic", layout.originalPath(attachment, path))
        assertEquals(null, layout.originalPath(Attachment("q", MediaKind.PHOTO, "q.jpg"), "media/x/02.jpg"))

        val report = tripReportMarkdown(
            listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event,
            media = { listOf(ReportMedia(MediaKind.PHOTO, path, layout.originalPath(attachment, path))) },
        )
        assertTrue("![Photo 1]($path) ([original](media/2026-10-22-0900-opening-keynote-agents-mcp/01-original.heic))" in report, report)
        val html = tripReportHtml(
            listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event,
            media = { listOf(ReportMedia(MediaKind.PHOTO, path, layout.originalPath(attachment, path))) },
        )
        assertTrue("<a href=\"media/2026-10-22-0900-opening-keynote-agents-mcp/01-original.heic\"><img src=\"$path\"" in html, html)
    }

    @Test
    fun theTextOnAPhotoIsSavedNextToItAndShownUnderItInTheReport() {
        val photo = Attachment("p", MediaKind.PHOTO, "p.jpg", addedAt = 1, text = "Tokenomics\n# not a heading\n> not a quote", textChecked = true)
        val layout = ReportLayout(listOf(keynote), mapOf("1" to listOf(photo)))
        val (attachment, path) = layout.media(keynote).single()
        val textPath = layout.textPath(path)
        assertEquals("media/2026-10-22-0900-opening-keynote-agents-mcp/01.txt", textPath)
        val media = listOf(ReportMedia(MediaKind.PHOTO, path, null, attachment.text, textPath))

        val report = tripReportMarkdown(listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event, media = { media })
        assertTrue("![Photo 1]($path)" in report, report)
        // Named after the photo and linked to its text file
        assertTrue("> **Text on Photo 1** ([01.txt]($textPath))" in report, report)
        assertTrue("> Tokenomics" in report, report)
        // Its characters are not read as formatting
        assertTrue("> \\# not a heading" in report && "> \\> not a quote" in report, report)
        assertTrue(report.indexOf("![Photo 1]") < report.indexOf("Text on Photo 1"))

        val html = tripReportHtml(listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event, media = { media })
        assertTrue("<summary>Text on Photo 1 (<a href=\"$textPath\">01.txt</a>)</summary>" in html, html)
        assertTrue("<pre>Tokenomics\n# not a heading\n&gt; not a quote</pre>" in html, html)
    }

    @Test
    fun aPhotoWithoutTextHasNoTextBlock() {
        val media = listOf(ReportMedia(MediaKind.PHOTO, "media/a/01.jpg", null, null, null), ReportMedia(MediaKind.PHOTO, "media/a/02.jpg", null, "  ", "media/a/02.txt"))
        val report = tripReportMarkdown(listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event, media = { media })
        assertFalse("Text on" in report, report)
        val html = tripReportHtml(listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event, media = { media })
        assertFalse("<details>" in html, html)
    }

    @Test
    fun theZipIsNamedAfterTheEvent() {
        assertEquals("agntcon-mcpcon-na-2026-trip-report.zip", reportFileName(event))
    }


    @Test
    fun photosAreNumberedInTheOrderTheyWereTakenNotImported() {
        // Imported 12, 13, 11, 14 style: added in one order, taken in another; ones without a time use when added
        val late = Attachment("late", MediaKind.PHOTO, "late.jpg", addedAt = 1000, takenAt = 300)
        val early = Attachment("early", MediaKind.PHOTO, "early.jpg", addedAt = 1001, takenAt = 100)
        val mid = Attachment("mid", MediaKind.PHOTO, "mid.jpg", addedAt = 1002, takenAt = 200)
        val noTime = Attachment("none", MediaKind.PHOTO, "none.jpg", addedAt = 250)
        val tieA = Attachment("tieA", MediaKind.PHOTO, "tieA.jpg", addedAt = 2000, takenAt = 400)
        val tieB = Attachment("tieB", MediaKind.PHOTO, "tieB.jpg", addedAt = 2001, takenAt = 400)
        val layout = ReportLayout(listOf(keynote), mapOf("1" to listOf(late, early, mid, noTime, tieA, tieB)))

        val media = layout.media(keynote)

        assertEquals(listOf("early", "mid", "none", "late", "tieA", "tieB"), media.map { it.first.id })
        assertEquals(
            listOf("01.jpg", "02.jpg", "03.jpg", "04.jpg", "05.jpg", "06.jpg").map { "media/2026-10-22-0900-opening-keynote-agents-mcp/$it" },
            media.map { it.second },
        )
    }

    private val starred = listOf(
        ReportMedia(MediaKind.PHOTO, "media/a/01.jpg"),
        ReportMedia(MediaKind.VIDEO, "media/a/02.mov"),
        ReportMedia(MediaKind.PHOTO, "media/a/03.jpg", original = "media/a/03-original.heic", highlighted = true),
    )

    @Test
    fun htmlFeaturesHighlightsAtTheTopAndLargerInTheSession() {
        val html = tripReportHtml(
            listOf(keynote, workshop), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event,
            media = { s -> if (s.id.id == "1") starred else emptyList() },
        )

        assertTrue("<h2>Highlights</h2>" in html, html)
        assertTrue(html.indexOf("<h2>Highlights</h2>") < html.indexOf("<section>"))
        // The original is linked, the caption has session title and time, and the photo keeps its session number
        assertTrue("<figure><a href=\"media/a/03-original.heic\"><img src=\"media/a/03.jpg\" alt=\"Photo 2\"" in html, html)
        assertTrue("★ Opening Keynote: Agents &amp; MCP! · " in html, html)
        // Inside the session only the starred one is large
        assertTrue("<a href=\"media/a/03-original.heic\" class=\"hl\"" in html, html)
        assertFalse("class=\"hl\"><img src=\"media/a/01.jpg\"" in html)
        assertTrue("<a href=\"media/a/01.jpg\"><img src=\"media/a/01.jpg\"" in html, html)
    }

    @Test
    fun noHighlightsMeansNoHighlightsSection() {
        val plain = starred.map { it.copy(highlighted = false) }
        val html = tripReportHtml(listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event, media = { plain })
        val markdown = tripReportMarkdown(listOf(keynote), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event, media = { plain })

        assertFalse("Highlights" in html)
        assertFalse("Highlights" in markdown)
        assertFalse("★" in markdown)
    }

    @Test
    fun markdownListsHighlightsFirstAndMarksThem() {
        val report = tripReportMarkdown(
            listOf(keynote, workshop), emptyMap(), PersonalNotes(), emptyMap(), emptySet(), event,
            labels = ExportLabels(highlights = "Höhepunkte", highlight = "Highlight"),
            media = { s -> if (s.id.id == "1") starred else emptyList() },
        )

        assertTrue(report.indexOf("## Höhepunkte") < report.indexOf("### Opening"), report)
        assertTrue("- ![Photo 2](media/a/03.jpg) **Opening Keynote: Agents & MCP! · " in report, report)
        assertTrue("([original](media/a/03-original.heic))" in report)
        assertTrue("![Photo 2](media/a/03.jpg) ([original](media/a/03-original.heic)) ★ Highlight" in report, report)
        assertFalse("![Photo 1](media/a/01.jpg) ★" in report)
    }
}
