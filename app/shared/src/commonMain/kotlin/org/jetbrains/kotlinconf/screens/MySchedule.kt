package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.kotlinconf.Day
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.minus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** One day of the personal calendar: bookmarked sessions on a timeline, the gaps between them, and clashes. */
data class MyScheduleDay(
    val date: LocalDate,
    /** Timeline bounds, on whole hours around the day's sessions. */
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    /** Bookmarked sessions that run most of the day (registration, an exhibition); kept off the timeline. */
    val allDay: List<SessionCardView>,
    val booked: List<BookedBlock>,
    val free: List<FreeBlock>,
    /** All of the day's timed sessions, for picking what to add to a gap. */
    val sessions: List<SessionCardView>,
) {
    val clashCount: Int get() = booked.count { it.overlapsWith.isNotEmpty() }
}

data class BookedBlock(
    val session: SessionCardView,
    /** Position among sessions that overlap it: column [column] of [columns]. */
    val column: Int,
    val columns: Int,
    val overlapsWith: List<SessionCardView>,
)

data class FreeBlock(
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    /** Sessions that fit completely in this gap. */
    val available: List<SessionCardView>,
)

/** Sessions at least this long are shown as all-day items. */
internal val ALL_DAY_MIN: Duration = 3.hours

/** Shorter gaps between bookmarked sessions are not worth showing. */
internal val FREE_MIN: Duration = 15.minutes

fun buildMySchedule(agenda: List<Day>): List<MyScheduleDay> = agenda.mapNotNull { day ->
    val sessions = day.timeSlots.flatMap { it.sessions }.distinctBy { it.id }
    val (allDaySessions, timed) = sessions.partition { it.endsAt - it.startsAt >= ALL_DAY_MIN }
    if (timed.isEmpty()) return@mapNotNull null

    val startsAt = LocalDateTime(day.date, LocalTime(timed.minOf { it.startsAt }.hour, 0))
    val lastEnd = timed.maxOf { it.endsAt }
    val endHour = if (lastEnd.minute == 0) lastEnd.hour else lastEnd.hour + 1
    val endsAt = if (endHour >= 24) LocalDateTime(day.date, LocalTime(23, 59)) else LocalDateTime(day.date, LocalTime(endHour, 0))

    val bookmarked = timed.filter { it.isFavorite }.sortedWith(compareBy({ it.startsAt }, { it.endsAt }))

    MyScheduleDay(
        date = day.date,
        startsAt = startsAt,
        endsAt = endsAt,
        allDay = allDaySessions.filter { it.isFavorite },
        booked = layOut(bookmarked),
        free = freeGaps(bookmarked, timed, startsAt, endsAt),
        sessions = timed.sortedWith(compareBy({ it.startsAt }, { it.endsAt })),
    )
}

private fun SessionCardView.overlaps(other: SessionCardView): Boolean =
    startsAt < other.endsAt && other.startsAt < endsAt

/** Places overlapping sessions side by side: each group of overlapping sessions shares its width. */
private fun layOut(sorted: List<SessionCardView>): List<BookedBlock> {
    val result = mutableListOf<BookedBlock>()
    var group = mutableListOf<SessionCardView>()
    var groupEnd: LocalDateTime? = null

    fun flush() {
        if (group.isEmpty()) return
        val columnEnds = mutableListOf<LocalDateTime>()
        val columnOf = group.associateWith { session ->
            val free = columnEnds.indexOfFirst { it <= session.startsAt }
            if (free >= 0) {
                columnEnds[free] = session.endsAt
                free
            } else {
                columnEnds += session.endsAt
                columnEnds.lastIndex
            }
        }
        group.mapTo(result) { session ->
            BookedBlock(
                session = session,
                column = columnOf.getValue(session),
                columns = columnEnds.size,
                overlapsWith = group.filter { it != session && it.overlaps(session) },
            )
        }
        group = mutableListOf()
    }

    for (session in sorted) {
        val end = groupEnd
        if (end != null && session.startsAt >= end) {
            flush()
            groupEnd = null
        }
        group += session
        groupEnd = maxOf(groupEnd ?: session.endsAt, session.endsAt)
    }
    flush()
    return result
}

private fun freeGaps(
    bookmarked: List<SessionCardView>,
    timed: List<SessionCardView>,
    dayStart: LocalDateTime,
    dayEnd: LocalDateTime,
): List<FreeBlock> {
    val gaps = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
    var cursor = dayStart
    for (session in bookmarked) {
        if (session.startsAt - cursor >= FREE_MIN) gaps += cursor to session.startsAt
        if (session.endsAt > cursor) cursor = session.endsAt
    }
    if (dayEnd - cursor >= FREE_MIN) gaps += cursor to dayEnd

    return gaps.map { (start, end) ->
        FreeBlock(
            startsAt = start,
            endsAt = end,
            available = timed
                .filter { !it.isFavorite && it.startsAt >= start && it.endsAt <= end }
                .sortedWith(compareBy({ it.startsAt }, { it.endsAt })),
        )
    }
}
