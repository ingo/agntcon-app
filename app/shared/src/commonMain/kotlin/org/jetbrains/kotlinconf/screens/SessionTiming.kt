package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.minus

/** Where a session stands relative to now, for the banner on its page. */
sealed interface SessionTiming {
    /** Later today. [soon] when it starts within [SOON_MINUTES]. */
    data class StartsIn(val minutes: Int, val soon: Boolean) : SessionTiming

    /** On another day. */
    data class StartsOn(val startsAt: LocalDateTime) : SessionTiming

    data class Live(val minutesLeft: Int) : SessionTiming

    data object Ended : SessionTiming
}

internal const val SOON_MINUTES = 15

fun sessionTiming(startsAt: LocalDateTime, endsAt: LocalDateTime, now: LocalDateTime): SessionTiming = when {
    now >= endsAt -> SessionTiming.Ended
    now >= startsAt -> SessionTiming.Live((endsAt - now).inWholeMinutes.toInt())
    startsAt.date != now.date -> SessionTiming.StartsOn(startsAt)
    else -> {
        val minutes = minutesUntil(startsAt, now)
        SessionTiming.StartsIn(minutes, soon = minutes <= SOON_MINUTES)
    }
}

/**
 * Whole minutes from [now] until [time], rounded up, so a session starting at 11:45 reads "in 1 min" at 11:44:30
 * rather than "in 0 min". Used everywhere a start time is shown as "in N min", so the numbers agree.
 */
fun minutesUntil(time: LocalDateTime, now: LocalDateTime): Int {
    val seconds = (time - now).inWholeSeconds
    return ((seconds + 59).floorDiv(60)).toInt()
}
