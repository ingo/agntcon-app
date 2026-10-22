package org.jetbrains.kotlinconf.sessionize

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.contactLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun defaultRulePicksSessionizePdfUploadsAndNeverLogos() {
        val sessions = parseSessionizeData(SLIDES_FEED).toConference(pacific).sessions.associateBy { it.id.id }

        assertEquals("https://sessionize.com/download/a~b.pdf~deck.pdf", sessions.getValue("1").slidesUrl)
        // A PowerPoint, a PDF uploaded as the event logo, and a PDF hosted elsewhere
        assertNull(sessions.getValue("2").slidesUrl)
        assertNull(sessions.getValue("3").slidesUrl)
        assertNull(sessions.getValue("4").slidesUrl)
    }

    @Test
    fun namedSlidesQuestionTakesAnyHttpsUploadButNeverLogos() {
        val sessions = parseSessionizeData(SLIDES_FEED)
            .toConference(pacific, SessionizeRules(slidesQuestion = "presentation or poster"))
            .sessions.associateBy { it.id.id }

        assertEquals("https://sessionize.com/download/a~b.pdf~deck.pdf", sessions.getValue("1").slidesUrl)
        assertEquals("https://sessionize.com/download/c~d.pptx~deck.pptx", sessions.getValue("2").slidesUrl)
        assertNull(sessions.getValue("3").slidesUrl)
        assertEquals("https://example.com/deck.pdf", sessions.getValue("4").slidesUrl)

        val logo = parseSessionizeData(SLIDES_FEED).toConference(pacific, SessionizeRules(slidesQuestion = "Event Logo"))
        assertTrue(logo.sessions.all { it.slidesUrl == null })
    }

    @Test
    fun recordingsAreKeptAndBlankOnesDropped() {
        val sessions = parseSessionizeData(SLIDES_FEED).toConference(pacific).sessions.associateBy { it.id.id }

        assertEquals("https://www.youtube.com/watch?v=abc", sessions.getValue("1").videoUrl)
        assertNull(sessions.getValue("2").videoUrl)
        assertNull(sessions.getValue("3").videoUrl)
    }

    @Test
    fun mergedWorkshopsKeepSlidesAndRecording() {
        val workshop = parseSessionizeData(KOTLINCONF_FEED)
            .toConference(berlin, SessionizeRules.KOTLINCONF)
            .sessions.single { it.id == SessionId("w1") }

        assertEquals("https://sessionize.com/download/w~x.pdf~coroutines.pdf", workshop.slidesUrl)
        assertEquals("https://www.youtube.com/watch?v=part2", workshop.videoUrl)
    }

    @Test
    fun speakerLinksKeepTypeTitleAndWebUrlsOnly() {
        val speaker = parseSessionizeData(SPEAKERS_FEED).toConference(pacific).speakers.single { it.id.id == "s1" }

        assertEquals(
            listOf("LinkedIn", "Blog", "Company_Website", "Sessionize"),
            speaker.links.map { it.type },
        )
        // A link with no title is labelled with its type; a mailto: link is dropped
        assertEquals(listOf("LinkedIn", "Blog", "Company Website", "Sessionize"), speaker.links.map { it.title })
        assertEquals("https://www.linkedin.com/in/a-b", speaker.contactLink?.url)
        assertTrue(speaker.links.single { it.type == "LinkedIn" }.isLinkedIn)
    }

    @Test
    fun contactLinkFallsBackToAnyPersonalLinkButNotSessionize() {
        val speakers = parseSessionizeData(SPEAKERS_FEED).toConference(pacific).speakers.associateBy { it.id.id }

        assertEquals("https://example.com/blog", speakers.getValue("s2").contactLink?.url)
        // Only a Sessionize profile: nothing to contact them by
        assertNull(speakers.getValue("s3").contactLink)
        assertEquals(listOf("Sessionize"), speakers.getValue("s3").links.map { it.type })
        assertNull(speakers.getValue("s4").contactLink)
    }

    @Test
    fun companyAndTitleComeFromTheSpeakerQuestions() {
        val speakers = parseSessionizeData(SPEAKERS_FEED).toConference(pacific).speakers.associateBy { it.id.id }

        assertEquals("Okta", speakers.getValue("s1").company)
        // The tag line already names the company
        assertEquals("Director of Identity, Okta", speakers.getValue("s1").position)
        assertEquals("https://okta.com", speakers.getValue("s1").companyUrl)
        // No tag line: job title and company from the questions; a logo in the website question is not a website
        assertEquals("CTO, TrueFoundry", speakers.getValue("s2").position)
        assertEquals("TrueFoundry", speakers.getValue("s2").company)
        assertNull(speakers.getValue("s2").companyUrl)
        // A company the tag line leaves out is appended; no answers keeps the tag line as it was
        assertEquals("Java dev, ExoQuery", speakers.getValue("s4").position)
        assertEquals("Just a tag line", speakers.getValue("s3").position)
        assertEquals("", speakers.getValue("s3").company)
    }

    @Test
    fun companyQuestionsCanBeRenamedPerEvent() {
        val rules = SessionizeRules(companyQuestion = "Employer")
        val speakers = parseSessionizeData(SPEAKERS_FEED).toConference(pacific, rules).speakers.associateBy { it.id.id }

        assertEquals("", speakers.getValue("s1").company)
        assertEquals("Acme", speakers.getValue("s5").company)
    }

    private companion object {
        val SPEAKERS_FEED = """
            {
              "sessions": [],
              "speakers": [
                {"id": "s1", "fullName": "A B", "tagLine": "Director of Identity, Okta",
                 "links": [
                   {"title": "LinkedIn", "url": "https://www.linkedin.com/in/a-b", "linkType": "LinkedIn"},
                   {"title": "Blog", "url": "https://example.com/blog", "linkType": "Blog"},
                   {"title": "", "url": "https://okta.com", "linkType": "Company_Website"},
                   {"title": "Sessionize", "url": "https://sessionize.com/a-b", "linkType": "Sessionize"},
                   {"title": "Email", "url": "mailto:a@b.com", "linkType": "Other"}
                 ],
                 "questionAnswers": [
                   {"questionId": 1, "answerValue": "Okta"},
                   {"questionId": 2, "answerValue": "Director of Identity Standards"}
                 ]},
                {"id": "s2", "fullName": "C D", "tagLine": null,
                 "links": [
                   {"title": "Sessionize", "url": "https://sessionize.com/c-d", "linkType": "Sessionize"},
                   {"title": "Blog", "url": "https://example.com/blog", "linkType": "Blog"}
                 ],
                 "questionAnswers": [
                   {"questionId": 1, "answerValue": " TrueFoundry "},
                   {"questionId": 2, "answerValue": "CTO"},
                   {"questionId": 3, "answerValue": "https://cdn.example.com/logo.svg"}
                 ]},
                {"id": "s3", "fullName": "E F", "tagLine": "Just a tag line", "links": [
                   {"title": "Sessionize", "url": "https://sessionize.com/e-f", "linkType": "Sessionize"}
                 ]},
                {"id": "s4", "fullName": "G H", "tagLine": "Java dev",
                 "questionAnswers": [{"questionId": 1, "answerValue": "ExoQuery"}]},
                {"id": "s5", "fullName": "I J", "tagLine": null,
                 "questionAnswers": [{"questionId": 4, "answerValue": "Acme"}]}
              ],
              "questions": [
                {"id": 1, "question": "Company", "questionType": "Short_Text"},
                {"id": 2, "question": "Speaker Title", "questionType": "Short_Text"},
                {"id": 3, "question": "Company URL", "questionType": "Web_address"},
                {"id": 4, "question": "Employer", "questionType": "Short_Text"}
              ]
            }
        """.trimIndent()

        val SLIDES_FEED = """
            {
              "sessions": [
                {"id": "1", "title": "PDF", "startsAt": "2026-10-22T10:00:00", "endsAt": "2026-10-22T10:30:00",
                 "questionAnswers": [{"questionId": 30, "answerValue": "https://sessionize.com/download/a~b.pdf~deck.pdf"}],
                 "recordingUrl": "https://www.youtube.com/watch?v=abc"},
                {"id": "2", "title": "PowerPoint", "startsAt": "2026-10-22T10:00:00", "endsAt": "2026-10-22T10:30:00",
                 "questionAnswers": [{"questionId": 30, "answerValue": "https://sessionize.com/download/c~d.pptx~deck.pptx"}],
                 "recordingUrl": ""},
                {"id": "3", "title": "Logo", "startsAt": "2026-10-22T10:00:00", "endsAt": "2026-10-22T10:30:00",
                 "questionAnswers": [{"questionId": 31, "answerValue": "https://sessionize.com/download/e~f.pdf~logo.pdf"}]},
                {"id": "4", "title": "Elsewhere", "startsAt": "2026-10-22T10:00:00", "endsAt": "2026-10-22T10:30:00",
                 "questionAnswers": [{"questionId": 30, "answerValue": " https://example.com/deck.pdf "},
                                     {"questionId": 32, "answerValue": "https://sessionize.com/download/g~h.pdf~notes.pdf"}]}
              ],
              "questions": [
                {"id": 30, "question": "Presentation or Poster", "questionType": "File_upload"},
                {"id": 31, "question": "Event Logo", "questionType": "File_upload"},
                {"id": 32, "question": "Speaker notes", "questionType": "Short_Text"}
              ]
            }
        """.trimIndent()


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
                 "endsAt": "2026-05-20T10:00:00Z", "speakers": ["s1"], "categoryItems": [1], "roomId": 5,
                 "questionAnswers": [{"questionId": 40, "answerValue": "https://sessionize.com/download/w~x.pdf~coroutines.pdf"}]},
                {"id": "w2", "title": "Coroutines. Part 2", "startsAt": "2026-05-20T12:00:00Z",
                 "endsAt": "2026-05-20T15:00:00Z", "speakers": ["s2"], "categoryItems": [1], "roomId": 5,
                 "recordingUrl": "https://www.youtube.com/watch?v=part2"},
                {"id": "i1", "title": "Interview", "startsAt": "2026-05-21T07:00:00Z",
                 "endsAt": "2026-05-21T07:30:00Z", "speakers": [], "categoryItems": [2], "roomId": 5}
              ],
              "speakers": [],
              "questions": [{"id": 40, "question": "Slides", "questionType": "File_upload"}],
              "categories": [
                {"id": 100, "title": "Session format",
                 "items": [{"id": 1, "name": "Workshop"}, {"id": 2, "name": "Interview"}]}
              ],
              "rooms": [{"id": 5, "name": "Room 5 (Lightning talks)"}]
            }
        """.trimIndent()
    }
}
