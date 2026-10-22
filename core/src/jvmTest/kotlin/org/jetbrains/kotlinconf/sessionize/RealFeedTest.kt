package org.jetbrains.kotlinconf.sessionize

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
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
    }
}
