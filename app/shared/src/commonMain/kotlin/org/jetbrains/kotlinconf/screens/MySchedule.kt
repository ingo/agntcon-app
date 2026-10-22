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
    /** Sessions not bookmarked yet that start in this block. */
    val available: List<SessionCardView>,
)

/** Sessions at least this long are shown as all-day items. */
internal val ALL_DAY_MIN: Duration = 3.hours

/** Shorter gaps between bookmarked sessions are not worth showing. */
internal val FREE_MIN: Duration = 15.minutes

/**
 * A time at which at least this many sessions start, or this many end, is one of the conference's
 * own breaks (the start of a breakout round, the end of lunch). Free time is split at these.
 */
internal const val BREAK_MIN_SESSIONS: Int = 3

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

    val breaks = naturalBreaks(timed)
    return gaps
        .flatMap { (start, end) -> splitAtBreaks(start, end, breaks) }
        .map { (start, end) ->
            FreeBlock(
                startsAt = start,
                endsAt = end,
                available = timed
                    .filter { !it.isFavorite && it.startsAt >= start && it.startsAt < end }
                    .sortedWith(compareBy({ it.startsAt }, { it.endsAt })),
            )
        }
}

/**
 * The day's slot grid: times when several talks start or end together, such as the start of a
 * breakout round or the end of lunch. Short items (demos, lightning talks) do not set the grid.
 */
internal fun naturalBreaks(timed: List<SessionCardView>): List<LocalDateTime> {
    val talks = timed.filterNot { it.isLightning }.ifEmpty { timed }
    val starts = talks.groupingBy { it.startsAt }.eachCount()
    val ends = talks.groupingBy { it.endsAt }.eachCount()
    val busy = starts.filterValues { it >= BREAK_MIN_SESSIONS }.keys + ends.filterValues { it >= BREAK_MIN_SESSIONS }.keys
    // A single-track day has no such pile-ups; there every talk start is a break
    return busy.ifEmpty { starts.keys }.sorted()
}

/**
 * Splits free time at the breaks inside it. A piece shorter than [FREE_MIN] joins the piece after
 * it (you are free from its start), or the one before it at the end of the free time.
 */
internal fun splitAtBreaks(
    start: LocalDateTime,
    end: LocalDateTime,
    breaks: List<LocalDateTime>,
): List<Pair<LocalDateTime, LocalDateTime>> {
    val points = listOf(start) + breaks.filter { it > start && it < end } + end
    val pieces = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
    var pieceStart = start
    for (to in points.drop(1)) {
        if (to - pieceStart >= FREE_MIN) {
            pieces += pieceStart to to
            pieceStart = to
        }
    }
    if (pieceStart < end) {
        val last = pieces.removeLastOrNull()
        pieces += (last?.first ?: pieceStart) to end
    }
    return pieces
}

/** "What's up now": the bookmarked sessions of the current conference day, seen from [UpNext.at]. */
data class UpNext(
    val day: MyScheduleDay,
    val at: LocalDateTime,
    /** Bookmarked sessions in progress (more than one means a clash to resolve). */
    val current: List<SessionCardView>,
    /** The next bookmarked session(s): the earliest start after [at], with any that overlap it. */
    val next: List<SessionCardView>,
    /** Free time between now (or the end of the current session) and [next], worth filling. */
    val freeBeforeNext: FreeBlock?,
    val later: List<SessionCardView>,
    val earlier: List<SessionCardView>,
)

/**
 * The day to look at: today during the conference, the first day before it, null after it.
 * Before the conference, the view starts at the beginning of the first day.
 */
fun upNext(days: List<MyScheduleDay>, now: LocalDateTime): UpNext? {
    val day = days.find { it.date == now.date }
        ?: days.firstOrNull()?.takeIf { now.date < it.date }
        ?: return null
    val at = if (day.date == now.date) now else day.startsAt
    val booked = day.booked.map { it.session }.sortedWith(compareBy({ it.startsAt }, { it.endsAt }))

    val current = booked.filter { it.startsAt <= at && at < it.endsAt }
    val upcoming = booked.filter { it.startsAt > at }
    val first = upcoming.firstOrNull()
    val next = if (first == null) emptyList() else upcoming.filter { it.startsAt < first.endsAt }
    val later = upcoming - next.toSet()

    // Free time ahead: from the end of what's on now (or now) until the next session
    val freeFrom = current.maxOfOrNull { it.endsAt } ?: at
    val freeBeforeNext = day.free.firstOrNull { free ->
        free.endsAt > freeFrom && (first == null || free.startsAt < first.startsAt) && free.available.any { it.startsAt >= at }
    }?.let { free -> free.copy(available = free.available.filter { it.startsAt >= at }) }

    return UpNext(
        day = day,
        at = at,
        current = current,
        next = next,
        freeBeforeNext = freeBeforeNext,
        later = later,
        earlier = booked.filter { it.endsAt <= at },
    )
}

/**
 * The floor or area in a room name, when the event writes it in parentheses,
 * e.g. "LL20A (Lower Level)" -> "Lower Level".
 */
fun roomLevel(location: String): String? =
    Regex("""\(([^()]+)\)\s*$""").find(location)?.groupValues?.get(1)?.trim()

/** The room name without the level, e.g. "LL20A (Lower Level)" -> "LL20A". */
fun roomName(location: String): String =
    location.replace(Regex("""\s*\([^()]+\)\s*$"""), "").ifBlank { location }
