package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.buildAgenda
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpNextTest {
    private fun session(id: String, start: String, end: String, room: String = "Room") = Session(
        id = SessionId(id),
        title = id,
        description = "",
        speakerIds = emptyList(),
        location = room,
        startsAt = LocalDateTime.parse("2026-10-22T$start"),
        endsAt = LocalDateTime.parse("2026-10-22T$end"),
    )

    private val conference = Conference(
        listOf(
            session("a", "10:00", "10:45", "LL20A (Lower Level)"),
            session("b", "11:00", "11:30", "210A (Concourse Level)"),
            session("c", "11:00", "11:20", "210B (Concourse Level)"),
            session("e", "11:40", "12:00"),
            session("f", "13:00", "13:30"),
            session("d", "14:00", "14:30"),
        )
    )
    private val favorites = setOf("a", "b", "c", "d").map(::SessionId).toSet()

    private fun at(time: String): UpNext? {
        val now = LocalDateTime.parse("2026-10-22T$time")
        return upNext(buildMySchedule(conference.buildAgenda(favorites, emptyList(), now)), now)
    }

    private fun List<org.jetbrains.kotlinconf.SessionCardView>.ids() = map { it.id.id }

    @Test
    fun duringASessionShowsItAndTheOverlappingNextOnes() {
        val upNext = at("10:20")!!
        assertEquals(listOf("a"), upNext.current.ids())
        assertEquals(listOf("c", "b"), upNext.next.ids())
        assertEquals(listOf("d"), upNext.later.ids())
        // The 15 minutes after a have nothing starting in them
        assertNull(upNext.freeBeforeNext)
    }

    @Test
    fun freeTimeBeforeTheNextSessionOffersWhatStartsFromNow() {
        val upNext = at("12:00")!!
        assertEquals(emptyList(), upNext.current.ids())
        assertEquals(listOf("d"), upNext.next.ids())
        assertEquals(listOf("f"), upNext.freeBeforeNext!!.available.ids())
        assertEquals(listOf("a", "c", "b"), upNext.earlier.ids())
    }

    @Test
    fun beforeTheConferenceShowsTheFirstDayFromItsStart() {
        val now = LocalDateTime.parse("2026-10-01T09:00")
        val upNext = upNext(buildMySchedule(conference.buildAgenda(favorites, emptyList(), now)), now)!!
        assertEquals(ConferenceMoment.BeforeConference, upNext.moment)
        assertEquals(LocalDateTime.parse("2026-10-22T10:00"), upNext.at)
        assertEquals(listOf("a"), upNext.current.ids())
    }

    @Test
    fun withNothingBookmarkedShowsWhatIsOnAndTheNextRound() {
        val now = LocalDateTime.parse("2026-10-22T10:20")
        val upNext = upNext(buildMySchedule(conference.buildAgenda(emptySet(), emptyList(), now)), now)!!
        assertEquals(true, upNext.nothingBookmarked)
        assertEquals(listOf("a"), upNext.happening.ids())
        // b and c both start at 11:00
        assertEquals(setOf("b", "c"), upNext.startingNext.ids().toSet())
    }

    @Test
    fun betweenConferenceDaysShowsTheNextDay() {
        val twoDays = Conference(
            listOf(
                session("a", "10:00", "10:45"),
                Session(
                    id = SessionId("z"), title = "z", description = "", speakerIds = emptyList(), location = "Room",
                    startsAt = LocalDateTime.parse("2026-10-24T09:00"), endsAt = LocalDateTime.parse("2026-10-24T09:30"),
                ),
            )
        )
        val now = LocalDateTime.parse("2026-10-23T12:00")
        val upNext = upNext(buildMySchedule(twoDays.buildAgenda(emptySet(), emptyList(), now)), now)!!
        assertEquals(ConferenceMoment.BetweenDays, upNext.moment)
        assertEquals("2026-10-24", upNext.day.date.toString())
    }

    @Test
    fun afterTheConferenceThereIsNothing() {
        val now = LocalDateTime.parse("2026-11-01T09:00")
        assertNull(upNext(buildMySchedule(conference.buildAgenda(favorites, emptyList(), now)), now))
    }

    @Test
    fun roomLevels() {
        assertEquals("Lower Level", roomLevel("LL20A (Lower Level)"))
        assertEquals("LL20A", roomName("LL20A (Lower Level)"))
        assertNull(roomLevel("Room 5"))
        assertEquals("Room 5", roomName("Room 5"))
    }
}
