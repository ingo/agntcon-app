package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.buildAgenda
import kotlin.test.Test
import kotlin.test.assertEquals

class MyScheduleTest {
    private val now = LocalDateTime(2026, 10, 1, 0, 0)

    private fun session(id: String, start: String, end: String) = Session(
        id = SessionId(id),
        title = id,
        description = "",
        speakerIds = emptyList(),
        location = "Room",
        startsAt = LocalDateTime.parse("2026-10-22T$start"),
        endsAt = LocalDateTime.parse("2026-10-22T$end"),
    )

    private val sessions = listOf(
        session("registration", "07:30", "19:00"),
        session("keynote", "09:00", "09:30"),
        session("a", "10:00", "10:45"),
        session("b", "10:30", "11:00"),
        session("c", "10:45", "11:15"),
        session("lunch", "12:00", "13:00"),
        session("d", "11:30", "11:55"),
        session("e", "14:00", "14:30"),
    )

    private fun day(vararg favorites: String): MyScheduleDay =
        buildMySchedule(
            Conference(sessions).buildAgenda(favorites.map(::SessionId).toSet(), emptyList(), now)
        ).single()

    @Test
    fun allDaySessionsStayOffTheTimeline() {
        val day = day("registration", "keynote")

        assertEquals(listOf("registration"), day.allDay.map { it.id.id })
        assertEquals(listOf("keynote"), day.booked.map { it.session.id.id })
        assertEquals(0, day.clashCount)
        // Timeline spans the day's timed sessions on whole hours
        assertEquals(LocalDateTime.parse("2026-10-22T09:00"), day.startsAt)
        assertEquals(LocalDateTime.parse("2026-10-22T15:00"), day.endsAt)
    }

    @Test
    fun overlappingSessionsShareTheWidthAndAreClashes() {
        val day = day("a", "b", "c")

        val byId = day.booked.associateBy { it.session.id.id }
        assertEquals(2, byId.getValue("a").columns)
        assertEquals(0, byId.getValue("a").column)
        assertEquals(1, byId.getValue("b").column)
        // c starts when a ends, so it reuses a's column
        assertEquals(0, byId.getValue("c").column)
        assertEquals(listOf("b"), byId.getValue("a").overlapsWith.map { it.id.id })
        assertEquals(listOf("a", "c"), byId.getValue("b").overlapsWith.map { it.id.id })
        assertEquals(3, day.clashCount)
    }

    @Test
    fun freeGapsListTheSessionsThatFit() {
        val day = day("keynote", "lunch")

        val gaps = day.free.map { "${it.startsAt.time}-${it.endsAt.time}" to it.available.map { s -> s.id.id } }
        assertEquals(
            listOf(
                "09:30-12:00" to listOf("a", "b", "c", "d"),
                "13:00-15:00" to listOf("e"),
            ),
            gaps,
        )
    }
}
