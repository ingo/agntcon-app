package org.jetbrains.kotlinconf.export

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.buildAgenda
import org.jetbrains.kotlinconf.directory.buildCompanies
import org.jetbrains.kotlinconf.directory.companiesBySessionCount
import org.jetbrains.kotlinconf.directory.isShowcase
import org.jetbrains.kotlinconf.directory.search
import org.jetbrains.kotlinconf.event.SanJoseMcEneryConventionCenter
import org.jetbrains.kotlinconf.event.toMapData
import org.jetbrains.kotlinconf.isServiceEvent
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.personal.SessionRating
import org.jetbrains.kotlinconf.personal.Thumb
import org.jetbrains.kotlinconf.screens.SessionSearch
import org.jetbrains.kotlinconf.sessionize.SessionizeData
import org.jetbrains.kotlinconf.sessionize.SessionizeRules
import org.jetbrains.kotlinconf.sessionize.parseSessionizeData
import org.jetbrains.kotlinconf.sessionize.toConference
import org.jetbrains.kotlinconf.utils.performSearch
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Exports, the company directory, search and map pins on the real AGNTCon feed: the shapes the
 * hand-written tests miss (long titles, accents, 150 companies, a showcase room with 78 talks).
 */
class RealFeedFeaturesTest {
    private val pacific = TimeZone.of("America/Los_Angeles")
    private val feed: SessionizeData =
        parseSessionizeData(File(System.getProperty("sessionize.testdata"), "agntcon-mcpcon-na-2026.json").readText())
    private val conference: Conference = feed.toConference(pacific, SessionizeRules(slidesQuestion = "Presentation or Poster"))
    private val event = ExportEvent("agntcon-mcpcon-na-2026", "AGNTCon + MCPCon North America 2026", pacific, "San Jose")
    private val now = LocalDateTime(2026, 10, 22, 11, 0)
    private val speakersById = conference.speakers.associateBy { it.id }
    private val showcaseRoom = "Solutions Showcase"

    private fun cards(favorites: Set<SessionId> = emptySet()): List<SessionCardView> =
        conference.buildAgenda(favorites, emptyList(), now).flatMap { it.timeSlots }.flatMap { it.sessions }

    private fun unfolded(ics: String) = ics.replace("\r\n ", "").split("\r\n").dropLast(1)

    // ---- calendar and trip report ----

    @Test
    fun calendarFileOfRandomBookmarksHasEveryEventAtTheRightUtcTime() {
        val random = Random(7)
        repeat(10) {
            val favorites = conference.sessions.filter { random.nextDouble() < 0.1 }.map { it.id }.toSet()
            val bookmarked = cards(favorites).filter { it.isFavorite }
            val ics = buildIcs(bookmarked, speakersById, event, Instant.parse("2026-10-22T12:00:00Z"))
            val lines = unfolded(ics)

            assertEquals(bookmarked.size, lines.count { it == "BEGIN:VEVENT" })
            assertEquals(bookmarked.size, lines.count { it == "END:VEVENT" })
            assertEquals(bookmarked.size, lines.map { it.substringBefore(':') }.count { it == "UID" })
            for (session in bookmarked) {
                val start = session.startsAt.toInstant(pacific).toString().replace(Regex("[-:]"), "").replace(".000000000", "")
                assertTrue(lines.contains("DTSTART:$start"), "${session.title}: DTSTART $start")
            }
            // No line over 75 octets, whatever the titles and descriptions hold
            ics.split("\r\n").forEach { assertTrue(it.encodeToByteArray().size <= 75, "too long: $it") }
            assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        }
    }

    @Test
    fun calendarFileOfTheWholeConferenceKeepsAccentsAndPunctuation() {
        val all = cards().filter { !it.isServiceEvent }
        val ics = buildIcs(all, speakersById, event, Instant.parse("2026-10-22T12:00:00Z"))
        val summaries = unfolded(ics).filter { it.startsWith("SUMMARY:") }

        assertEquals(all.size, summaries.size)
        // "Prompt à la Carte" survives; titles with commas are escaped
        assertTrue("SUMMARY:Prompt à la Carte" in summaries)
        assertTrue(summaries.none { Regex("""(?<!\\),""").containsMatchIn(it.removePrefix("SUMMARY:")) })
    }

    @Test
    fun tripReportOfTheWholeConferenceListsEverySessionOnceInTimeOrder() {
        val all = cards(conference.sessions.map { it.id }.toSet()).filter { !it.isServiceEvent }
        val notes = PersonalNotes().withSessionNote(all.first().id, "A note")
        val ratings = mapOf(all.first().id.id to SessionRating(Thumb.UP, "Worth it"))
        val report = tripReportMarkdown(all, speakersById, notes, ratings, setOf(conference.speakers.first().id), event)

        assertEquals(all.size, report.lines().count { it.startsWith("### ") })
        assertEquals(all.count { it.slidesUrl != null }, report.lines().count { it.startsWith("- [Slides](") })
        assertTrue("A note" in report && "“Worth it”" in report)
        // Time order within the report: each day's headings follow the sessions' start times
        val order = all.sortedWith(compareBy({ it.startsAt }, { it.endsAt }, { it.title })).map { it.title }
        val headings = report.lines().filter { it.startsWith("### ") }.map { it.removePrefix("### ").replace(Regex("""\\(.)"""), "$1") }
        assertEquals(order, headings)
        assertTrue(report.contains("## People I met"))
    }

    // ---- company directory ----

    @Test
    fun companiesAreBuiltFromTheSpeakerAnswers() {
        val companies = buildCompanies(conference.speakers, cards(), showcaseRoom)

        // 148 spellings on the live feed, "Vstorm"/"VStorm" and the like merged
        assertTrue(companies.size in 140..150, "companies: ${companies.size}")
        assertEquals(companies.map { it.name.lowercase() }.toSet().size, companies.size, "a company listed twice")
        assertEquals(companies.map { it.name.lowercase() }, companies.map { it.name.lowercase() }.sorted())
        val google = companies.single { it.name == "Google" }
        assertTrue(google.speakers.size >= 10)
        assertTrue(google.sessions.isNotEmpty())
        assertTrue(companies.sumOf { it.speakers.size } == conference.speakers.count { it.company.isNotBlank() })
    }

    @Test
    fun solutionsShowcaseHasSeventyEightTalksInItsRoomAndCanBeBrowsedByCompany() {
        val all = cards()
        val showcase = all.filter { it.isShowcase(showcaseRoom) }

        // 78 on the live feed when the snapshot was cut; the snapshot has a coffee break in the room too
        val inRoom = showcase.count { it.locationLine == "Solutions Showcase (Concourse Level)" }
        assertTrue(inRoom in 78..80, "sessions in the Solutions Showcase room: $inRoom")
        // Rooms named "... Solutions Showcase" (the AAIF Hub) count as well
        assertTrue(showcase.size >= inRoom)

        val companies = buildCompanies(conference.speakers, all, showcaseRoom).filter { it.showcaseSessions.isNotEmpty() }
        assertTrue(companies.size > 20, "companies with a showcase talk: ${companies.size}")
        for (company in companies) {
            assertTrue(company.showcaseSessions.all { it.isShowcase(showcaseRoom) })
            assertTrue(company.showcaseSessions.all { s -> company.speakers.any { it.id in s.speakerIds } })
            assertEquals(company.showcaseSessions, company.sessions.filter { it.isShowcase(showcaseRoom) })
        }
        // Showcase sessions without a speaker from a company ("Sponsor Activity", coffee) belong to no company
        val listed = companies.flatMap { it.showcaseSessions }.map { it.id }.toSet()
        assertTrue(listed.size < showcase.size)
    }

    @Test
    fun companySearchIgnoresCaseAndAccents() {
        val companies = buildCompanies(conference.speakers, cards(), showcaseRoom)

        assertEquals(listOf("Okta"), companies.search("okta").map { it.name }.filter { it == "Okta" })
        assertTrue(companies.search("GOOG").any { it.name == "Google" })
        assertEquals(companies, companies.search("  "))
        assertEquals(emptyList(), companies.search("no such company xyz"))
        // Diacritics are ignored unless typed: "cafe" finds "Café Co"-style names
        val withAccent = companies.firstOrNull { name -> name.name.any { it.code > 127 } }
        if (withAccent != null) {
            val plain = java.text.Normalizer.normalize(withAccent.name, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            assertTrue(companies.search(plain).any { it.name == withAccent.name })
        }
    }

    @Test
    fun scheduleFilterListsCompaniesWithTheMostSessionsFirst() {
        val byCount = companiesBySessionCount(cards())

        assertTrue(byCount.size > 100)
        assertEquals("Google", byCount.first())
        assertTrue(byCount.indexOf("Google") < byCount.indexOf("Okta"))
        assertEquals(byCount.size, byCount.toSet().size)
    }

    @Test
    fun speakerSearchFindsPeopleByCompany() {
        val results = conference.speakers.performSearch(
            searchText = "okta",
            produceResult = { speaker, _ -> speaker },
            selectors = listOf({ it.name }, { it.position }),
        )

        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.name == "Aaron Parecki" })
        assertTrue(results.all { it.company.equals("Okta", ignoreCase = true) || it.position.contains("okta", ignoreCase = true) })
    }

    // ---- schedule search ----

    @Test
    fun searchFindsSessionsByWordsOnlyInTheDescription() {
        val sessions = cards().filter { !it.isServiceEvent }
        val target = sessions.firstNotNullOf { session ->
            val visible = (session.title + " " + session.speakerLine + " " + session.companies.joinToString(" ")).lowercase()
            val word = session.description.split(Regex("[^\\p{L}]+"))
                .filter { it.length >= 9 && it.lowercase() !in visible }
                .minByOrNull { it.length }
            word?.let { session to it }
        }
        val (session, word) = target

        val match = SessionSearch(word).match(session)
        assertTrue(match.matched, "'$word' should find '${session.title}'")
        assertTrue(match.matchedHiddenText)
        assertTrue(match.titleHighlights.isEmpty() && match.speakerHighlights.isEmpty())
        // The same word does not find a session that never mentions it
        val others = sessions.filter { word.lowercase() !in (it.title + it.speakerLine + it.description + it.companies.joinToString()).lowercase() }
        assertTrue(others.isNotEmpty() && others.none { SessionSearch(word).match(it).matched })
    }

    @Test
    fun searchFindsSessionsByTheirSpeakersCompany() {
        val sessions = cards().filter { !it.isServiceEvent }
        val byCompany = sessions.filter { SessionSearch("okta").match(it).matched }

        assertTrue(byCompany.isNotEmpty())
        assertTrue(byCompany.all { s ->
            "okta" in (s.title + s.speakerLine + s.description).lowercase() || s.companies.any { it.equals("Okta", ignoreCase = true) }
        })
        assertTrue(byCompany.any { it.companies.contains("Okta") })
    }

    @Test
    fun companyFilterKeepsOnlyThatCompanysSessionsAndCombinesWithTagsAndText() {
        val sessions = cards().filter { !it.isServiceEvent }
        val google = sessions.filter { SessionSearch("", company = "Google").match(it).matched }

        assertTrue(google.isNotEmpty())
        assertTrue(google.all { "Google" in it.companies })
        assertEquals(sessions.count { "Google" in it.companies }, google.size)
        assertEquals(0, sessions.count { SessionSearch("", company = "No such company").match(it).matched })

        val tag = google.first().tags.first()
        val withTag = sessions.filter { SessionSearch("", tags = listOf(tag), company = "Google").match(it).matched }
        assertTrue(withTag.isNotEmpty() && withTag.all { tag in it.tags && "Google" in it.companies })
        // A query narrows the company's sessions further
        val title = google.first().title
        val narrowed = sessions.filter { SessionSearch(title, company = "Google").match(it).matched }
        assertTrue(narrowed.any { it.title == title } && narrowed.size <= google.size)
    }

    @Test
    fun blankSearchMatchesEveryTalkButNoServiceEvents() {
        val all = cards()
        val search = SessionSearch("")

        assertEquals(all.count { !it.isServiceEvent }, all.count { search.match(it).matched })
        assertTrue(all.filter { it.isServiceEvent }.none { search.match(it).matched })
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        val sessions = cards().filter { !it.isServiceEvent }

        assertTrue(sessions.any { it.title == "Prompt à la Carte" })
        assertTrue(sessions.any { it.title == "Prompt à la Carte" && SessionSearch("prompt a la carte").match(it).matched })
        assertTrue(sessions.any { it.title == "Prompt à la Carte" && SessionSearch("PROMPT À LA").match(it).matched })
    }

    // ---- find on map ----

    @Test
    fun onlyRoomsWithAPinOfferFindOnMap() {
        val mapData = SanJoseMcEneryConventionCenter.toMapData(feed, "San Jose")
        val pinned = mapData.rooms.keys
        val all = cards().filter { !it.isServiceEvent }

        assertTrue("210C (Concourse Level)" in pinned)
        assertTrue("Grand Ballroom (Concourse Level)" in pinned)
        assertTrue("Solutions Showcase (Concourse Level)" !in pinned)
        assertTrue(all.any { it.locationLine in pinned }, "no session in a pinned room")
        assertTrue(all.any { it.locationLine !in pinned }, "every session in a pinned room")
        // The pin of a session's room is on one of the floors
        for (session in all.filter { it.locationLine in pinned }) {
            val room = assertNotNull(mapData.rooms[session.locationLine])
            assertTrue(room.floorIndex in mapData.floors.indices)
        }
    }
}
