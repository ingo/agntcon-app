package org.jetbrains.kotlinconf.export

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.SpeakerLink
import org.jetbrains.kotlinconf.asSessionCard
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.personal.SessionRating
import org.jetbrains.kotlinconf.personal.Thumb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ExportTest {
    private val pacific = TimeZone.of("America/Los_Angeles")
    private val event = ExportEvent("agntcon-mcpcon-na-2026", "AGNTCon + MCPCon North America 2026", pacific, "150 W San Carlos St, San Jose")
    private val stamp = Instant.parse("2026-10-22T12:00:00Z")
    private val now = LocalDateTime(2026, 10, 1, 0, 0)

    private val aaron = Speaker(
        SpeakerId("p1"), "Aaron Parecki", "Director of Identity Standards, Okta", "", "",
        company = "Okta",
        links = listOf(
            SpeakerLink("Sessionize", "Sessionize", "https://sessionize.com/aaron"),
            SpeakerLink("LinkedIn", "LinkedIn", "https://www.linkedin.com/in/aaronparecki"),
        ),
    )
    private val bea = Speaker(SpeakerId("p2"), "Bea [Dev]", "Engineer", "", "")
    private val speakers = listOf(aaron, bea).associateBy { it.id }

    private fun card(
        id: String,
        title: String,
        start: String,
        end: String,
        favorite: Boolean = true,
        speakerIds: List<String> = emptyList(),
        description: String = "",
        location: String = "Grand Ballroom (Concourse Level)",
        slides: String? = null,
        day: String = "2026-10-22",
    ): SessionCardView = Session(
        id = SessionId(id),
        title = title,
        description = description,
        speakerIds = speakerIds.map(::SpeakerId),
        location = location,
        startsAt = LocalDateTime.parse("${day}T$start"),
        endsAt = LocalDateTime.parse("${day}T$end"),
        tags = listOf("Keynote"),
        slidesUrl = slides,
    ).asSessionCard(speakers, now, if (favorite) setOf(SessionId(id)) else emptySet(), null)

    /** The content lines, with folded lines joined. */
    private fun lines(ics: String) = ics.replace("\r\n ", "").split("\r\n").dropLast(1)

    // ---- calendar ----

    @Test
    fun icsHasOneEventPerSessionWithUtcTimes() {
        val ics = buildIcs(
            listOf(card("1", "Keynote", "09:00", "09:30", speakerIds = listOf("p1"))),
            speakers, event, stamp,
        )
        val lines = lines(ics)

        assertEquals("BEGIN:VCALENDAR", lines.first())
        assertEquals("END:VCALENDAR", lines.last())
        assertTrue("VERSION:2.0" in lines)
        assertEquals(1, lines.count { it == "BEGIN:VEVENT" })
        assertTrue("UID:session-1@agntcon-mcpcon-na-2026" in lines)
        assertTrue("DTSTAMP:20261022T120000Z" in lines)
        // 9:00 in San Jose on Oct 22 (PDT, UTC-7) is 16:00 UTC
        assertTrue("DTSTART:20261022T160000Z" in lines)
        assertTrue("DTEND:20261022T163000Z" in lines)
        assertTrue("SUMMARY:Keynote" in lines)
        assertTrue(lines.any { it == "LOCATION:Grand Ballroom (Concourse Level)\\, 150 W San Carlos St\\, San Jose" })
    }

    @Test
    fun icsTimesFollowDaylightSavingOfTheEventZone() {
        // The clocks went back on Nov 1: the same 9:00 is one hour later in UTC
        val ics = buildIcs(listOf(card("1", "Late", "09:00", "10:00", day = "2026-11-02")), speakers, event, stamp)

        assertTrue("DTSTART:20261102T170000Z" in lines(ics))
    }

    @Test
    fun icsEscapesTextAndEndsEveryLineWithCrLf() {
        val ics = buildIcs(
            listOf(card("1", "Agents; tools, and \\ more", "09:00", "09:30", description = "Line one\nLine two")),
            speakers, event, stamp,
        )

        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertFalse(ics.replace("\r\n", "").contains('\n'), "a bare newline would break a content line")
        assertTrue("SUMMARY:Agents\\; tools\\, and \\\\ more" in lines(ics))
        val description = lines(ics).first { it.startsWith("DESCRIPTION:") }
        assertTrue("Line one\\nLine two" in description)
    }

    @Test
    fun icsFoldsLongLinesAtSeventyFiveOctets() {
        val title = "Ünïcödé ".repeat(30)
        val ics = buildIcs(listOf(card("1", title, "09:00", "09:30")), speakers, event, stamp)

        for (line in ics.split("\r\n")) {
            assertTrue(line.encodeToByteArray().size <= 75, "line too long: $line")
        }
        // Unfolding (removing CRLF + space) gives the original line back
        assertTrue("SUMMARY:$title" in lines(ics))
    }

    @Test
    fun icsListsSessionsInTimeOrderAndSkipsNothing() {
        val ics = buildIcs(
            listOf(card("late", "Late", "15:00", "15:30"), card("early", "Early", "09:00", "09:30")),
            speakers, event, stamp,
        )

        assertEquals(listOf("SUMMARY:Early", "SUMMARY:Late"), lines(ics).filter { it.startsWith("SUMMARY:") })
    }

    @Test
    fun icsFileNameIsSafeEverywhere() {
        assertEquals("agntcon-mcpcon-na-2026-schedule.ics", icsFileName(event, "schedule"))
        assertEquals("a-b-session-12.ics", icsFileName(ExportEvent("A/B", "x", pacific), "session-12"))
    }

    @Test
    fun icsDescriptionNamesSpeakersAndTheEvent() {
        val ics = buildIcs(listOf(card("1", "Keynote", "09:00", "09:30", speakerIds = listOf("p1", "p2"), description = "About agents")), speakers, event, stamp)
        val description = lines(ics).first { it.startsWith("DESCRIPTION:") }

        assertTrue("Aaron Parecki\\, Bea [Dev]" in description)
        assertTrue("About agents" in description)
        assertTrue("AGNTCon + MCPCon North America 2026" in description)
    }

    // ---- share as text ----

    @Test
    fun sessionTextHasTitleTimeRoomSpeakersAndEvent() {
        val text = sessionShareText(card("1", "Keynote", "09:00", "09:30", speakerIds = listOf("p1")), speakers, event)

        assertEquals(
            """
            Keynote
            Thursday, October 22, 09:00 – 09:30
            Room: Grand Ballroom (Concourse Level)
            Speakers: Aaron Parecki (Okta)
            AGNTCon + MCPCon North America 2026
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun scheduleTextGroupsByDayInTimeOrder() {
        val text = scheduleShareText(
            listOf(
                card("c", "Day two talk", "10:00", "10:30", day = "2026-10-23"),
                card("b", "Second", "11:00", "11:30"),
                card("a", "First", "09:00", "09:30"),
            ),
            event,
        )

        assertEquals(
            """
            My schedule – AGNTCon + MCPCon North America 2026

            Thursday, October 22
            09:00 – 09:30  First (Grand Ballroom (Concourse Level))
            11:00 – 11:30  Second (Grand Ballroom (Concourse Level))

            Friday, October 23
            10:00 – 10:30  Day two talk (Grand Ballroom (Concourse Level))
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun emptyScheduleTextSaysSo() {
        assertTrue(scheduleShareText(emptyList(), event).endsWith("No sessions"))
    }

    @Test
    fun labelsComeFromTheCaller() {
        val labels = ExportLabels(speakers = "Sprecher", room = "Raum", scheduleHeading = "Mein Programm")
        val text = sessionShareText(card("1", "T", "09:00", "09:30", speakerIds = listOf("p1")), speakers, event, labels)

        assertTrue("Raum: " in text)
        assertTrue("Sprecher: " in text)
        assertTrue(scheduleShareText(listOf(card("1", "T", "09:00", "09:30")), event, labels).startsWith("Mein Programm – "))
    }

    // ---- trip report ----

    @Test
    fun tripReportListsSessionsInTimeOrderWithNotesRatingsAndSlides() {
        val sessions = listOf(
            card("2", "Second talk", "11:00", "11:45", speakerIds = listOf("p2")),
            card("1", "Keynote", "09:00", "09:30", speakerIds = listOf("p1"), slides = "https://sessionize.com/download/a.pdf"),
        )
        val notes = PersonalNotes()
            .withSessionNote(SessionId("1"), "Resource indicators\n# not a heading\n- not a bullet")
            .withSpeakerNote(SpeakerId("p1"), "Wants to review the spec")
        val ratings = mapOf(
            "1" to SessionRating(Thumb.UP, "Audience binding matters"),
            "2" to SessionRating(Thumb.DOWN),
        )
        val report = tripReportMarkdown(sessions, speakers, notes, ratings, setOf(SpeakerId("p1")), event)

        assertEquals(
            """
            # Trip report: AGNTCon + MCPCon North America 2026

            ## Thursday, October 22

            ### Keynote

            - 09:00 – 09:30 · Grand Ballroom (Concourse Level)
            - **Speakers:** Aaron Parecki (Okta)
            - **Rating:** 👍 “Audience binding matters”
            - [Slides](https://sessionize.com/download/a.pdf)

            **My notes:**

            Resource indicators
            \# not a heading
            \- not a bullet

            ### Second talk

            - 11:00 – 11:45 · Grand Ballroom (Concourse Level)
            - **Speakers:** Bea \[Dev\]
            - **Rating:** 👎

            ## People I met

            - **[Aaron Parecki](https://www.linkedin.com/in/aaronparecki)**, Director of Identity Standards, Okta
              Wants to review the spec
            """.trimIndent() + "\n",
            report,
        )
    }

    @Test
    fun tripReportWithNothingElseStillHasTheHeading() {
        val report = tripReportMarkdown(emptyList(), speakers, PersonalNotes(), emptyMap(), emptySet(), event)

        assertEquals("# Trip report: AGNTCon + MCPCon North America 2026\n", report)
    }

    @Test
    fun tripReportCoversBookmarkedAndNotedOrRatedSessions() {
        val all = listOf(
            card("bookmarked", "Bookmarked", "09:00", "09:30"),
            card("noted", "Noted", "10:00", "10:30", favorite = false),
            card("rated", "Rated", "11:00", "11:30", favorite = false),
            card("ignored", "Ignored", "12:00", "12:30", favorite = false),
            card("emptyRating", "Empty rating", "13:00", "13:30", favorite = false),
        )
        val notes = PersonalNotes().withSessionNote(SessionId("noted"), "Dropped in")
        val ratings = mapOf("rated" to SessionRating(Thumb.UP), "emptyRating" to SessionRating())

        assertEquals(
            listOf("bookmarked", "noted", "rated"),
            sessionsForTripReport(all, notes, ratings).map { it.id.id },
        )
    }

    @Test
    fun tripReportEscapesMarkdownInTitles() {
        val report = tripReportMarkdown(
            listOf(card("1", "Why *agents* use_tools [now]", "09:00", "09:30")),
            speakers, PersonalNotes(), emptyMap(), emptySet(), event,
        )

        assertTrue("### Why \\*agents\\* use\\_tools \\[now\\]" in report)
    }
}
