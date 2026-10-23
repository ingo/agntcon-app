package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.buildAgenda
import org.jetbrains.kotlinconf.SessionState
import kotlin.test.assertNull
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
            Conference(sessions).buildAgenda(favorites.map(::SessionId).toSet(), now)
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
    fun singleTrackFreeTimeSplitsAtEachTalk() {
        val day = day("keynote", "lunch")

        val gaps = day.free.map { "${it.startsAt.time}-${it.endsAt.time}" to it.available.map { s -> s.id.id } }
        assertEquals(
            listOf(
                "09:30-10:00" to emptyList(),
                "10:00-10:30" to listOf("a"),
                "10:30-10:45" to listOf("b"),
                "10:45-11:30" to listOf("c"),
                "11:30-12:00" to listOf("d"),
                "13:00-14:00" to emptyList(),
                "14:00-15:00" to listOf("e"),
            ),
            gaps,
        )
    }

    @Test
    fun freeTimeFollowsTheConferenceRounds() {
        // The AGNTCon shape: parallel rounds at 11:10 and 11:45, lunch 12:10-13:25 with short demos inside it
        val sessions = listOf(
            session("before", "10:35", "11:00"),
            session("r1a", "11:10", "11:35"), session("r1b", "11:10", "11:35"), session("r1c", "11:10", "11:35"),
            session("r2a", "11:45", "12:10"), session("r2b", "11:45", "12:10"), session("r2c", "11:45", "12:10"),
            session("lunch", "12:10", "13:25"), session("signing", "12:10", "13:25"), session("meetup", "12:10", "13:25"),
            session("demo1", "12:15", "12:25"), session("demo2", "12:15", "12:25"), session("demo3", "12:15", "12:25"),
            session("keynote", "13:25", "13:35"),
            session("after", "14:20", "14:45"), session("after2", "14:20", "14:45"), session("after3", "14:20", "14:45"),
        )
        val day = buildMySchedule(
            Conference(sessions).buildAgenda(setOf(SessionId("before"), SessionId("after")), now)
        ).single()

        assertEquals(
            listOf("11:00-11:35", "11:35-12:10", "12:10-13:25", "13:25-14:20"),
            day.free.filter { it.startsAt.hour in 11..13 }.map { "${it.startsAt.time}-${it.endsAt.time}" },
        )
        assertEquals(
            listOf("lunch", "meetup", "signing", "demo1", "demo2", "demo3"),
            day.free.single { it.startsAt.hour == 12 }.available.map { it.id.id }.let { ids ->
                ids.take(3).sorted() + ids.drop(3).sorted()
            },
        )
    }

    @Test
    fun nowLineIsPlacedByMinutesFromTheStartOfTheTimeline() {
        val day = day("keynote", "lunch") // timeline 09:00 to 15:00

        assertEquals(0, nowOffsetMinutes(day, LocalDateTime.parse("2026-10-22T09:00")))
        assertEquals(90, nowOffsetMinutes(day, LocalDateTime.parse("2026-10-22T10:30")))
        assertEquals(359, nowOffsetMinutes(day, LocalDateTime.parse("2026-10-22T14:59")))
    }

    @Test
    fun noNowLineOutsideTheHoursOfTheDay() {
        val day = day("keynote", "lunch")

        assertNull(nowOffsetMinutes(day, LocalDateTime.parse("2026-10-22T08:59")))
        assertNull(nowOffsetMinutes(day, LocalDateTime.parse("2026-10-22T15:00")))
        assertNull(nowOffsetMinutes(day, LocalDateTime.parse("2026-10-21T10:30")))
        assertNull(nowOffsetMinutes(day, LocalDateTime.parse("2026-10-23T10:30")))
    }

    @Test
    fun bookedSessionsKnowIfTheyAreOverLiveOrUpcoming() {
        val at = LocalDateTime.parse("2026-10-22T10:15")
        val booked = setOf("keynote", "a", "e").map(::SessionId).toSet()
        val day = buildMySchedule(Conference(sessions).buildAgenda(booked, at)).single()

        val states = day.booked.associate { it.session.id.id to it.session.state }
        assertEquals(SessionState.Past, states.getValue("keynote"))
        assertEquals(SessionState.Live, states.getValue("a"))
        assertEquals(SessionState.Upcoming, states.getValue("e"))
    }
}
