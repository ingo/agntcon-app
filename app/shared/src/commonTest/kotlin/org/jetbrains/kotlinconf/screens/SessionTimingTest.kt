package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTimingTest {
    private val start = LocalDateTime.parse("2026-10-22T11:45")
    private val end = LocalDateTime.parse("2026-10-22T12:10")

    private fun at(time: String) = sessionTiming(start, end, LocalDateTime.parse(time))

    @Test
    fun beforeTheSession() {
        assertEquals(SessionTiming.StartsIn(35, soon = false), at("2026-10-22T11:10"))
        assertEquals(SessionTiming.StartsIn(10, soon = true), at("2026-10-22T11:35"))
        assertEquals(SessionTiming.StartsIn(1, soon = true), at("2026-10-22T11:44:30"))
    }

    @Test
    fun duringAndAfter() {
        assertEquals(SessionTiming.Live(10), at("2026-10-22T12:00"))
        assertEquals(SessionTiming.Live(25), at("2026-10-22T11:45"))
        assertEquals(SessionTiming.Ended, at("2026-10-22T12:10"))
    }

    @Test
    fun anotherDay() {
        assertEquals(SessionTiming.StartsOn(start), at("2026-10-21T18:00"))
    }

    @Test
    fun minutesRoundUpTheSameWayEverywhere() {
        // 4 min 30 s before 11:45 reads "in 5 min" both in the banner and next to the session's time
        assertEquals(5, minutesUntil(start, LocalDateTime.parse("2026-10-22T11:40:30")))
        assertEquals(1, minutesUntil(start, LocalDateTime.parse("2026-10-22T11:44:59")))
        assertEquals(0, minutesUntil(start, start))
        assertEquals(-1, minutesUntil(start, LocalDateTime.parse("2026-10-22T11:46")))
    }
}
