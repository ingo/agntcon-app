package org.jetbrains.kotlinconf.media

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.asSessionCard
import org.jetbrains.kotlinconf.screens.ImportAnswer
import org.jetbrains.kotlinconf.screens.ImportGroup
import org.jetbrains.kotlinconf.screens.ImportState
import org.jetbrains.kotlinconf.personal.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Photos filed under the wrong session by their time: the cases found in a real export. */
class WrongSessionTest {
    private fun session(id: String, start: String, end: String, title: String, description: String = ""): SessionCardView =
        Session(
            id = SessionId(id),
            title = title,
            description = description,
            speakerIds = listOf(SpeakerId("s-$id")),
            location = "Room $id",
            startsAt = LocalDateTime.parse("2026-10-22T$start"),
            endsAt = LocalDateTime.parse("2026-10-22T$end"),
            tags = listOf("Breakout"),
        ).asSessionCard(emptyMap(), LocalDateTime(2026, 10, 1, 0, 0), emptySet())

    private fun at(time: String) = LocalDateTime.parse("2026-10-22T$time")
    private fun candidates(vararg s: SessionCardView) = s.map { it.id to SlideTextMatcher.candidateText(it) }

    // A: a bookmarked talk, and a parallel talk that is the one on the slides
    private val prove = session("prove", "16:05", "16:30", "Prove What Your Agent Did",
        "Signed receipts and attestation for every tool call, verified after the fact.")
    private val review = session("review", "16:00", "16:30", "Automating pull request review on AgentCore",
        "How the apfm-agentic-review reviewer runs on AgentCore, with guardrails and human sign-off.")

    @Test
    fun aConfidentTimeMatchInTheWrongRoomIsQuestionedBySlideText() = runTest {
        val sessions = listOf(prove, review)
        // By time this is a plain match: bookmarked, inside its window
        assertEquals(MediaMatch.Matched(prove.id), MediaMatcher.match(at("16:15"), sessions, setOf(prove.id)))
        val slides = listOf(
            "apfm-agentic-review\nreviewer on AgentCore", "AgentCore runtime · apfm-agentic-review", "Human sign-off for the reviewer",
            "guardrails", "apfm-agentic-review pipeline", "reviewer architecture on AgentCore",
        )
        val finding = SlideCheck.check(slides.indices.toList(), prove.id, candidates(prove, review), edgeStart = false, edgeEnd = false) { slides[it] }
        assertEquals(SlideFinding(review.id, slides.indices.toSet()), finding)
    }

    @Test
    fun slidesThatFitTheSessionAreLeftAlone() = runTest {
        val slides = listOf("Prove what your agent did", "Signed receipts for every tool call", "attestation")
        assertNull(SlideCheck.check(slides.indices.toList(), prove.id, candidates(prove, review), true, true) { slides[it] })
        // No text at all: nothing to say
        assertNull(SlideCheck.check(slides.indices.toList(), prove.id, candidates(prove, review), true, true) { null })
    }

    @Test
    fun theDescriptionAndCompanyTellSessionsApartNotJustTheTitle() {
        val a = SessionId("a") to "Taming tools Ada Lovelace Contoso\nVector retrieval with pgvector and rerankers for long documents"
        val b = SessionId("b") to "Scaling out Grace Hopper Fabrikam\nKubernetes autoscaling with KEDA for bursty workloads"
        assertEquals(SessionId("a"), SlideTextMatcher.best("pgvector + rerankers over long documents", listOf(a, b)))
        assertEquals(SessionId("b"), SlideTextMatcher.best("Fabrikam · KEDA autoscaling", listOf(a, b)))
    }

    // B: an unbookmarked keynote 13:40-13:45 after a bookmarked talk that ended at 13:40
    @Test
    fun aPhotoInsideAnotherRunningSessionIsNotPreselectedToTheOneThatEnded() {
        val talk = session("talk", "13:00", "13:40", "Bookmarked talk")
        val keynote = session("keynote", "13:40", "13:45", "Closing keynote")
        val match = MediaMatcher.match(at("13:43"), listOf(talk, keynote), setOf(talk.id))
        assertIs<MediaMatch.Ask>(match)
        assertEquals(listOf(talk.id, keynote.id), match.candidates)
        assertEquals(keynote.id, match.likely)
    }

    @Test
    fun withSeveralRunningSessionsNothingIsPreselected() {
        val talk = session("talk", "13:00", "13:40", "Bookmarked talk")
        val a = session("a", "13:40", "13:50", "Parallel A")
        val b = session("b", "13:40", "13:50", "Parallel B")
        val match = MediaMatcher.match(at("13:43"), listOf(talk, a, b), setOf(talk.id))
        assertIs<MediaMatch.Ask>(match)
        assertNull(match.likely)
    }

    @Test
    fun aTalkThatJustEndedStillWinsWhenNothingElseIsRunning() {
        val talk = session("talk", "13:00", "13:40", "Bookmarked talk")
        assertEquals(MediaMatch.Matched(talk.id), MediaMatcher.match(at("13:43"), listOf(talk), setOf(talk.id)))
    }

    // C: short keynote slots that ran late; the closing slides of one went into the next slot's window
    private val first = session("first", "09:15", "09:20", "Why identity matters for agents", "Delegated authority and OAuth for agents, ID-JAG.")
    private val second = session("second", "09:25", "09:35", "Open registries for tool discovery", "A public registry of MCP servers with signed metadata.")

    @Test
    fun closingSlidesOfTheTalkBeforeAtTheStartOfAGroupAreQuestioned() = runTest {
        val slides = listOf(
            "ID-JAG delegated authority", "OAuth for agents: delegated authority", "Thank you · ID-JAG delegated authority", null,
            "Open registries", "Public registry of MCP servers", "signed metadata registry", "tool discovery", "registry search", "registry federation",
        )
        val finding = SlideCheck.check(slides.indices.toList(), second.id, candidates(first, second), edgeStart = true, edgeEnd = true) { slides[it] }
        // Only the photos that name the other talk; the one without text after them, and the talk's own photos, stay
        assertEquals(SlideFinding(first.id, setOf(0, 1, 2)), finding)
    }

    @Test
    fun theRunIsOnlyLookedForAtTheEdgesThatCanBeABoundary() = runTest {
        val slides = listOf("ID-JAG delegated authority", "OAuth for agents: delegated authority", "Open registries", "Public registry of MCP servers", "signed metadata registry")
        val finding = SlideCheck.check(slides.indices.toList(), second.id, candidates(first, second), edgeStart = false, edgeEnd = false) { slides[it] }
        assertNull(finding)
    }

    @Test
    fun theRunFollowsTheTimeOrderNotThePickOrder() = runTest {
        val slides = listOf("Open registries", "ID-JAG delegated authority", "registry of MCP servers", "OAuth delegated authority")
        // Taken in this order: 3, 1 (the other talk), then 0, 2
        val finding = SlideCheck.check(listOf(3, 1, 0, 2), second.id, candidates(first, second), true, true) { slides[it] }
        assertEquals(SlideFinding(first.id, setOf(3, 1)), finding)
    }

    // E: the same moment in two sessions
    private fun photo(second: Int) =
        PickedMedia("/tmp/$second.jpg", MediaKind.PHOTO, "jpg", takenAt = MediaTime.of(Instant.fromEpochSeconds(1_792_000_000L + second)))

    private fun group(session: String, vararg items: PickedMedia) =
        ImportGroup(MediaMatch.Matched(SessionId(session)), items.toList(), null, null, emptyList(), ImportAnswer.To(SessionId(session)))

    @Test
    fun photosTakenAtTheSameMomentInTwoSessionsAreFlaggedBothWays() {
        val state = ImportState(groups = listOf(group("a", photo(1), photo(2), photo(3)), group("b", photo(2), photo(3), photo(9))))
        assertEquals(mapOf(0 to (2 to SessionId("b")), 1 to (2 to SessionId("a"))), state.sameMoment)
    }

    @Test
    fun photosGoingToTheSameSessionAreNotFlagged() {
        assertEquals(emptyMap(), ImportState(groups = listOf(group("a", photo(1)), group("a", photo(1)))).sameMoment)
    }

    @Test
    fun theSlideWarningShowsOnlyWhileTheAnswerIsAnotherSession() {
        val g = group("prove", photo(1), photo(2)).copy(suggested = SessionId("review"))
        val flags = ImportState(groups = listOf(g)).flags
        assertEquals(SessionId("review"), flags.getValue(0).session)
        assertEquals(setOf(0, 1), flags.getValue(0).items)
        assertNotEquals(true, ImportState(groups = listOf(g.copy(answer = ImportAnswer.To(SessionId("review"))))).flags[0]?.bySlide)
    }
}
