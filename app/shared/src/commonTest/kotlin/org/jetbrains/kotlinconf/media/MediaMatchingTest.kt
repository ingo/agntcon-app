package org.jetbrains.kotlinconf.media

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.asSessionCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Photos from the library matched to sessions by when they were taken. */
class MediaMatchingTest {
    private val pacific = TimeZone.of("America/Los_Angeles")

    private fun session(id: String, start: String, end: String, room: String = "230C", tags: List<String> = listOf("Breakout"), speakers: List<String> = listOf("s"), favorite: Boolean = false): SessionCardView =
        Session(
            id = SessionId(id),
            title = "Session $id",
            description = "",
            speakerIds = speakers.map(::SpeakerId),
            location = room,
            startsAt = LocalDateTime.parse("2026-10-22T$start"),
            endsAt = LocalDateTime.parse("2026-10-22T$end"),
            tags = tags,
        ).asSessionCard(emptyMap(), LocalDateTime(2026, 10, 1, 0, 0), if (favorite) setOf(SessionId(id)) else emptySet())

    private fun at(time: String) = LocalDateTime.parse("2026-10-22T$time")

    private val keynote = session("k", "09:00", "09:30", room = "Ballroom")
    private val coffee = session("c", "09:30", "10:00", tags = emptyList(), speakers = emptyList())
    private val trackA = session("a", "10:00", "10:30", room = "230A")
    private val trackB = session("b", "10:00", "10:30", room = "230B")
    private val trackC = session("cc", "10:00", "10:30", room = "230C")
    private val afternoon = session("p", "14:00", "14:45")
    private val registration = session("r", "07:30", "19:00", room = "Lobby", tags = listOf("Registration"))
    private val all = listOf(registration, keynote, coffee, trackA, trackB, trackC, afternoon)

    private fun match(time: String?, attended: Set<String> = emptySet()) =
        MediaMatcher.match(time?.let(::at), all, attended.map(::SessionId).toSet())

    @Test
    fun theSessionYouWereInWins() {
        assertEquals(MediaMatch.Matched(SessionId("b")), match("10:12", attended = setOf("b")))
    }

    @Test
    fun theOnlyTalkRunningIsASafeGuess() {
        assertEquals(MediaMatch.Matched(SessionId("k")), match("09:10"))
    }

    @Test
    fun parallelTalksWithNothingMarkedAsk() {
        assertEquals(MediaMatch.Ask(listOf(SessionId("a"), SessionId("b"), SessionId("cc"))), match("10:12"))
    }

    @Test
    fun aClashBetweenTwoMarkedSessionsAsksBetweenThoseTwo() {
        // Both started at 10:00: no likely one
        assertEquals(MediaMatch.Ask(listOf(SessionId("a"), SessionId("cc"))), match("10:12", attended = setOf("cc", "a")))
    }

    @Test
    fun aFewMinutesBeforeOrAfterStillCount() {
        // The title slide before the start, the speaker's last slide during questions
        assertEquals(MediaMatch.Matched(SessionId("p")), match("13:56", attended = setOf("p")))
        assertEquals(MediaMatch.Matched(SessionId("p")), match("14:55", attended = setOf("p")))
    }

    @Test
    fun aSessionRunningWinsOverTheEndOfTheOneBefore() {
        val late = session("l", "09:50", "10:02", room = "Ballroom")
        val sessions = listOf(late, trackA)
        // 10:05 is in the end-of-session grace of "l" and inside "a": the one running wins
        assertEquals(MediaMatch.Matched(SessionId("a")), MediaMatcher.match(at("10:05"), sessions, setOf(SessionId("l"), SessionId("a"))))
    }

    @Test
    fun aBookmarkedSessionThatRanLongOrStartedEarlyIsTheLikelyOne() {
        // 09:50: the keynote ended 20 minutes ago, the bookmarked talk starts in 10. The closer one is preselected
        assertEquals(MediaMatch.Ask(listOf(SessionId("k"), SessionId("b")), likely = SessionId("b")), match("09:50", attended = setOf("k", "b")))
        // 10:50: 20 minutes after the bookmarked talk ended, it ran long
        assertEquals(MediaMatch.Ask(listOf(SessionId("a")), likely = SessionId("a")), match("10:50", attended = setOf("a")))
        // 13:45: a quarter of an hour before the bookmarked afternoon talk, it started early
        assertEquals(MediaMatch.Ask(listOf(SessionId("p")), likely = SessionId("p")), match("13:45", attended = setOf("p")))
    }

    @Test
    fun justAfterOneBookmarkedTalkAndJustBeforeTheNextTheOneThatJustEndedIsLikely() {
        val first = session("f", "09:20", "09:25", room = "Ballroom")
        val second = session("s", "09:30", "09:40", room = "Ballroom")
        // 09:26: one minute after the first ended, four before the second starts: it ran long
        assertEquals(
            MediaMatch.Ask(listOf(SessionId("f"), SessionId("s")), likely = SessionId("f")),
            MediaMatcher.match(at("09:26"), listOf(first, second), setOf(SessionId("f"), SessionId("s"))),
        )
    }

    @Test
    fun breaksAreNeverOfferedAndTimeFarFromSessionsAsksAboutTheNeighbours() {
        assertEquals(MediaMatch.Between(SessionId("b"), null), match("12:00", attended = setOf("k", "b")))
        // Nothing marked: the neighbours among all talks that day
        assertEquals(MediaMatch.Between(SessionId("a"), SessionId("p")), match("12:00"))
    }

    @Test
    fun allDayItemsOnlyCountWhenNothingShorterFits() {
        // Registration runs all day but the keynote is the safe guess
        assertEquals(MediaMatch.Matched(SessionId("k")), match("09:10"))
        // Marked all-day item and nothing shorter marked: it is the answer
        assertEquals(MediaMatch.Matched(SessionId("r")), match("12:00", attended = setOf("r")))
        // Never offered as a parallel choice
        assertEquals(MediaMatch.Ask(listOf(SessionId("a"), SessionId("b"), SessionId("cc"))), match("10:12"))
    }

    @Test
    fun choicesAreInTimeOrder() {
        val early = session("e", "09:45", "10:45", room = "Hall E")
        val sessions = listOf(trackA, early)
        assertEquals(MediaMatch.Ask(listOf(SessionId("e"), SessionId("a"))), MediaMatcher.match(at("10:10"), sessions, emptySet()))
    }

    @Test
    fun noDateAsks() {
        assertEquals(MediaMatch.NoTime, match(null))
    }

    @Test
    fun photosWithTheSameAnswerAreOneGroupInTheOrderTheyWereTaken() {
        val times = listOf(at("14:10"), at("10:12"), at("10:20"), null, at("09:05"))
        val matches = times.map { MediaMatcher.match(it, all, emptySet()) }
        val groups = groupMatches(matches, times)

        assertEquals(
            listOf(
                MediaGroup(MediaMatch.Matched(SessionId("k")), listOf(4)),
                MediaGroup(MediaMatch.Ask(listOf(SessionId("a"), SessionId("b"), SessionId("cc"))), listOf(1, 2)),
                MediaGroup(MediaMatch.Matched(SessionId("p")), listOf(0)),
                MediaGroup(MediaMatch.NoTime, listOf(3)),
            ),
            groups,
        )
    }

    @Test
    fun exifTimeWithAnOffsetIsAnExactMoment() {
        val time = MediaTime.fromExif("2026:10:22 14:22:33", "-07:00")!!
        assertEquals(Instant.parse("2026-10-22T21:22:33Z"), time.instant)
        assertEquals(at("14:22:33"), time.inZone(pacific))
        // Looked at from Berlin it is still the same moment, shown in the event's zone
        assertEquals(at("14:22:33"), MediaTime.fromExif("2026:10:22 23:22:33", "+02:00")!!.inZone(pacific))
    }

    @Test
    fun exifTimeWithoutAnOffsetIsTheEventsLocalTime() {
        assertEquals(at("14:22:33"), MediaTime.fromExif("2026:10:22 14:22:33", null)!!.inZone(pacific))
        assertEquals(at("14:22:33"), MediaTime.fromExif("2026-10-22T14:22:33", "")!!.inZone(pacific))
    }

    @Test
    fun missingOrBogusDatesAreNoDate() {
        assertNull(MediaTime.fromExif(null, null))
        assertNull(MediaTime.fromExif("    :  :     :  :  ", null))
        assertNull(MediaTime.fromExif("0000:00:00 00:00:00", null))
        assertNull(MediaTime.fromAndroidVideoDate("19040101T000000.000Z"))
        assertNull(MediaTime.fromAndroidVideoDate(null))
    }

    @Test
    fun androidVideoDatesAreUtc() {
        assertEquals(at("14:22:33"), MediaTime.fromAndroidVideoDate("20261022T212233.000Z")!!.inZone(pacific))
    }
}
