package org.jetbrains.kotlinconf

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AgendaTest {
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

    private fun slotsOf(vararg sessions: Session): List<Pair<String, List<String>>> =
        Conference(sessions.toList())
            .buildAgenda(favorites = emptySet(), votes = emptyList(), now = now)
            .single()
            .timeSlots
            .map { it.title to it.sessions.map { s -> s.id.id } }

    @Test
    fun allDaySessionDoesNotSwallowTheDay() {
        val slots = slotsOf(
            session("registration", "07:30", "19:00"),
            session("zen", "07:30", "20:05"),
            session("keynote", "09:00", "09:05"),
            session("coffee", "10:10", "10:35"),
            session("talk", "10:35", "11:00"),
        )

        assertEquals(
            listOf(
                "07:30 – 19:00" to listOf("registration"),
                "07:30 – 20:05" to listOf("zen"),
                "09:00 – 09:05" to listOf("keynote"),
                "10:10 – 10:35" to listOf("coffee"),
                "10:35 – 11:00" to listOf("talk"),
            ),
            slots,
        )
    }

    @Test
    fun lightningTalksJoinTheSlotAroundThem() {
        val slots = slotsOf(
            session("talk", "10:00", "10:45"),
            session("lightning1", "10:00", "10:15"),
            session("lightning2", "10:15", "10:30"),
        )

        assertEquals(listOf("10:00 – 10:45" to listOf("talk", "lightning1", "lightning2")), slots)
    }
}
