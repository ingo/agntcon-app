package org.jetbrains.kotlinconf.sessionize

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionizeMapperTest {
    private val pacific = TimeZone.of("America/Los_Angeles")
    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun localTimesAreTakenAsEventLocal() {
        assertEquals(
            LocalDateTime(2026, 10, 22, 9, 0),
            parseSessionizeTime("2026-10-22T09:00:00", pacific),
        )
    }

    @Test
    fun utcTimesAreConvertedToTheEventZone() {
        assertEquals(
            LocalDateTime(2026, 5, 20, 8, 0),
            parseSessionizeTime("2026-05-20T06:00:00Z", berlin),
        )
        assertEquals(
            LocalDateTime(2026, 5, 20, 8, 0),
            parseSessionizeTime("2026-05-20T07:00:00+01:00", berlin),
        )
    }

    @Test
    fun mapsLocalTimeFeedAndSkipsUnscheduledSessions() {
        val conference = parseSessionizeData(AGNTCON_FEED).toConference(pacific)

        assertEquals(listOf("1001"), conference.sessions.map { it.id.id })
        val session = conference.sessions.single()
        assertEquals(LocalDateTime(2026, 10, 22, 9, 0), session.startsAt)
        assertEquals("Grand Ballroom (Concourse Level)", session.location)
        assertEquals(listOf("MCPCon", "Keynote"), session.tags)
        assertEquals("", conference.speakers.single { it.id.id == "s2" }.photoUrl)
    }

    @Test
    fun tagGroupsAreAssignedByTitle() {
        val tags = parseSessionizeData(AGNTCON_FEED).toTagInfo()

        assertEquals(listOf("MCPCon", "Evals & Testing"), tags.categories)
        assertEquals(listOf("Beginner"), tags.levels)
        assertEquals(listOf("Keynote"), tags.formats)
    }

    @Test
    fun explicitCategoryGroupsWin() {
        val tags = parseSessionizeData(AGNTCON_FEED)
            .toTagInfo(SessionizeRules(categoryGroups = listOf("Session Type")))

        assertEquals(listOf("Keynote"), tags.categories)
        assertEquals(emptyList(), tags.levels)
        assertEquals(emptyList(), tags.formats)
    }

    @Test
    fun kotlinConfRulesDropInterviewsAndMergeWorkshops() {
        val conference = parseSessionizeData(KOTLINCONF_FEED)
            .toConference(berlin, SessionizeRules.KOTLINCONF)

        assertNull(conference.sessions.find { it.title == "Interview" })
        val workshop = conference.sessions.single { it.id == SessionId("w1") }
        assertEquals("Coroutines", workshop.title)
        assertEquals(LocalDateTime(2026, 5, 20, 9, 0), workshop.startsAt)
        assertEquals(LocalDateTime(2026, 5, 20, 17, 0), workshop.endsAt)
        assertEquals("Room 5", workshop.location)
        assertEquals(1, conference.sessions.size)
        assertEquals(listOf("Workshop"), parseSessionizeData(KOTLINCONF_FEED).toTagInfo(SessionizeRules.KOTLINCONF).formats)
    }

    private companion object {
        val AGNTCON_FEED = """
            {
              "sessions": [
                {"id": "1001", "title": " Opening Keynote ", "description": null,
                 "startsAt": "2026-10-22T09:00:00", "endsAt": "2026-10-22T09:30:00",
                 "isServiceSession": false, "isPlenumSession": true, "speakers": ["s1"],
                 "categoryItems": [1, 10], "questionAnswers": [], "roomId": 7,
                 "liveUrl": null, "recordingUrl": null, "status": "Accepted"},
                {"id": "1002", "title": "TBD", "startsAt": null, "endsAt": null,
                 "speakers": [], "categoryItems": [], "roomId": null}
              ],
              "speakers": [
                {"id": "s1", "firstName": "A", "lastName": "B", "fullName": "A B",
                 "profilePicture": "https://cdn.sessionize.com/image/a.jpg",                 "tagLine": "Engineer", "bio": "Bio", "isTopSpeaker": false, "links": []},
                {"id": "s2", "firstName": "C", "lastName": "D", "fullName": "C D",
                 "profilePicture": null, "sessions": [], "tagLine": null, "bio": null}
              ],
              "questions": [],
              "categories": [
                {"id": 100, "title": "Topic", "sort": 0,
                 "items": [{"id": 1, "name": "MCPCon", "sort": 1}, {"id": 2, "name": "Evals & Testing", "sort": 2}]},
                {"id": 101, "title": "Audience Level", "sort": 1,
                 "items": [{"id": 5, "name": "Beginner", "sort": 1}]},
                {"id": 102, "title": "Session Type", "sort": 2,
                 "items": [{"id": 10, "name": "Keynote", "sort": 1}]}
              ],
              "rooms": [{"id": 7, "name": "Grand Ballroom (Concourse Level)", "sort": 0}]
            }
        """.trimIndent()

        val KOTLINCONF_FEED = """
            {
              "sessions": [
                {"id": "w1", "title": "Coroutines. Part 1", "startsAt": "2026-05-20T07:00:00Z",
                 "endsAt": "2026-05-20T10:00:00Z", "speakers": ["s1"], "categoryItems": [1], "roomId": 5},
                {"id": "w2", "title": "Coroutines. Part 2", "startsAt": "2026-05-20T12:00:00Z",
                 "endsAt": "2026-05-20T15:00:00Z", "speakers": ["s2"], "categoryItems": [1], "roomId": 5},
                {"id": "i1", "title": "Interview", "startsAt": "2026-05-21T07:00:00Z",
                 "endsAt": "2026-05-21T07:30:00Z", "speakers": [], "categoryItems": [2], "roomId": 5}
              ],
              "speakers": [],
              "categories": [
                {"id": 100, "title": "Session format",
                 "items": [{"id": 1, "name": "Workshop"}, {"id": 2, "name": "Interview"}]}
              ],
              "rooms": [{"id": 5, "name": "Room 5 (Lightning talks)"}]
            }
        """.trimIndent()
    }
}
