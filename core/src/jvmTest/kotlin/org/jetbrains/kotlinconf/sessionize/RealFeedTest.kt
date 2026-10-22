package org.jetbrains.kotlinconf.sessionize

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.contactLink
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parses snapshots of real feeds: one sending event-local times, one sending UTC. */
class RealFeedTest {
    private fun feed(name: String): SessionizeData =
        parseSessionizeData(File(System.getProperty("sessionize.testdata"), name).readText())

    @Test
    fun agntconLocalTimeFeed() {
        val feed = feed("agntcon-mcpcon-na-2026.json")
        val conference = feed.toConference(TimeZone.of("America/Los_Angeles"))

        assertEquals(254, feed.sessions.size)
        // Four sessions have no time yet and are left out
        assertEquals(250, conference.sessions.size)
        assertEquals(241, conference.speakers.size)
        assertEquals(
            LocalDateTime(2026, 10, 22, 9, 0),
            conference.sessions.single { it.title == "Welcome to AGNTCon + MCPCon North America" }.startsAt,
        )
        assertTrue(conference.sessions.all { it.startsAt.date in LocalDate(2026, 10, 19)..LocalDate(2026, 10, 23) })
        assertTrue(conference.sessions.all { it.endsAt > it.startsAt })

        val tags = feed.toTagInfo()
        assertEquals(20, tags.categories.size)
        assertEquals(listOf("Beginner", "Intermediate", "Advanced", "Any"), tags.levels)
        assertEquals(listOf("Keynote", "Breakout", "Workshop", "Sponsored Session", "Panel"), tags.formats)
    }

    @Test
    fun agntconSlides() {
        val feed = feed("agntcon-mcpcon-na-2026.json")
        val named = feed.toConference(TimeZone.of("America/Los_Angeles"), SessionizeRules(slidesQuestion = "Presentation or Poster"))
        val withSlides = named.sessions.filter { it.slidesUrl != null }

        // Every upload to "Presentation or Poster": PDFs, PowerPoints and a poster image
        assertEquals(67, withSlides.size)
        assertTrue(withSlides.all { it.slidesUrl!!.startsWith("https://sessionize.com/download/") })
        // The two "Prompt à la Carte" sessions only have an Event Logo upload
        val promptALaCarte = named.sessions.filter { it.title == "Prompt à la Carte" }
        assertEquals(2, promptALaCarte.size)
        assertTrue(promptALaCarte.all { it.slidesUrl == null })

        // Without a named question only the PDFs count
        val pdfs = feed.toConference(TimeZone.of("America/Los_Angeles")).sessions.mapNotNull { it.slidesUrl }
        assertEquals(63, pdfs.size)
        assertTrue(pdfs.all { it.endsWith(".pdf") })

        // No recordings yet
        assertTrue(named.sessions.all { it.videoUrl == null })
    }

    @Test
    fun agntconSpeakerLinksAndCompanies() {
        val conference = feed("agntcon-mcpcon-na-2026.json").toConference(TimeZone.of("America/Los_Angeles"))
        val speakers = conference.speakers

        // 241 speakers; the live feed had 179 LinkedIn links when the snapshot was taken
        val linkedIn = speakers.count { s -> s.links.any { it.isLinkedIn } }
        assertTrue(linkedIn in 170..200, "LinkedIn links: $linkedIn")
        assertTrue(speakers.all { s -> s.links.all { it.url.startsWith("https://") || it.url.startsWith("http://") } })
        assertTrue(speakers.flatMap { it.links }.map { it.type }.toSet().containsAll(setOf("LinkedIn", "Company_Website", "Twitter", "Blog")))

        val aaron = speakers.single { it.name == "Aaron Parecki" }
        assertEquals("Okta", aaron.company)
        assertEquals("Director of Identity Standards, Okta", aaron.position)
        assertEquals("https://www.linkedin.com/in/aaronparecki", aaron.contactLink?.url)

        // Almost every speaker answered "Company"; a company a tag line leaves out is still in the shown line
        assertTrue(speakers.count { it.company.isNotEmpty() } >= 230)
        assertTrue(speakers.filter { it.company.isNotEmpty() }.all { it.position.contains(it.company, ignoreCase = true) })
        // A logo in "Company URL" is not taken as a website
        assertTrue(speakers.mapNotNull { it.companyUrl }.none { it.endsWith(".svg") || it.endsWith(".png") })
    }

    @Test
    fun kotlinConfSpeakersHaveNoCompanyQuestionsAndKeepTheirTagLines() {
        val feed = feed("kotlinconf-2026.json")
        val conference = feed.toConference(TimeZone.of("Europe/Berlin"), SessionizeRules.KOTLINCONF)

        assertTrue(conference.speakers.all { it.company.isEmpty() })
        assertEquals(feed.speakers.map { it.tagLine ?: "" }, conference.speakers.map { it.position })
    }

    @Test
    fun kotlinConfUtcFeed() {
        val feed = feed("kotlinconf-2026.json")
        val conference = feed.toConference(TimeZone.of("Europe/Berlin"), SessionizeRules.KOTLINCONF)

        // UTC 06:00 is 08:00 in Berlin in May
        assertEquals(LocalDateTime(2026, 5, 20, 8, 0), conference.sessions.minOf { it.startsAt })
        assertTrue(conference.sessions.all { it.startsAt.hour in 7..23 }, "times not converted to Berlin")
        // Multi-part workshops are merged into one session each
        assertEquals(129, feed.sessions.size)
        assertEquals(111, conference.sessions.size)
        assertTrue(conference.sessions.none { ". Part" in it.title })
        assertTrue(conference.sessions.map { it.id }.let { it.size == it.toSet().size })
        // Recordings published on YouTube after the event; no slides questions in this feed
        assertEquals(74, conference.sessions.count { it.videoUrl != null })
        assertTrue(conference.sessions.mapNotNull { it.videoUrl }.all { it.startsWith("https://www.youtube.com/") })
        assertTrue(conference.sessions.all { it.slidesUrl == null })
    }
}
