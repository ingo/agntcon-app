package org.jetbrains.kotlinconf

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface TimeProvider {
    fun now(): LocalDateTime
    val time: StateFlow<LocalDateTime>
    suspend fun run(): Nothing

    fun getNotificationTime(notificationTime: LocalDateTime): LocalDateTime = notificationTime
    fun getNotificationDelay(notificationTime: LocalDateTime): Duration = notificationTime - now()
}

operator fun LocalDateTime.minus(other: LocalDateTime): Duration =
    toInstant(EVENT_TIME_ZONE) - other.toInstant(EVENT_TIME_ZONE)

operator fun LocalDateTime.minus(duration: Duration): LocalDateTime =
    (toInstant(EVENT_TIME_ZONE) - duration).toLocalDateTime(EVENT_TIME_ZONE)

/** The device clock in the event's time zone, ticking just after each whole minute. */
class DeviceTimeProvider : TimeProvider {
    override fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(EVENT_TIME_ZONE)

    final override val time: StateFlow<LocalDateTime>
        field = MutableStateFlow(now())

    override suspend fun run(): Nothing {
        time.value = now()

        while (true) {
            // Tick just after each whole minute, so "starts in 1 min" turns into "happening now" at 11:45:00,
            // not up to a minute later depending on when the app was opened
            val intoMinute = Clock.System.now().toEpochMilliseconds().mod(60_000L)
            delay((60_000L - intoMinute + 50).milliseconds)
            time.value = now()
        }
    }
}
