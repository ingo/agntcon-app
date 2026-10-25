package org.jetbrains.kotlinconf.media

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.asSessionCard
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sessions offered when moving a photo that landed on the wrong talk. */
class MoveTargetsTest {
    private fun session(id: String, day: Int, start: String, end: String, title: String = "Session $id", service: Boolean = false): SessionCardView =
        Session(
            id = SessionId(id),
            title = title,
            description = "",
            speakerIds = if (service) emptyList() else listOf(SpeakerId("s")),
            location = "230C",
            startsAt = LocalDateTime.parse("2026-10-$day" + "T$start"),
            endsAt = LocalDateTime.parse("2026-10-$day" + "T$end"),
            tags = if (service) emptyList() else listOf("Breakout"),
        ).asSessionCard(emptyMap(), LocalDateTime(2026, 10, 1, 0, 0), emptySet())

    private val dayOneAfternoon = session("p", 22, "14:00", "14:45")
    private val dayOneMorning = session("m", 22, "10:00", "10:30", title = "b")
    private val dayOneParallel = session("n", 22, "10:00", "10:30", title = "A")
    private val current = session("c", 22, "11:00", "11:30")
    private val lunch = session("l", 22, "12:00", "13:00", service = true)
    private val dayTwo = session("d", 23, "09:00", "09:30")
    private val all = listOf(dayTwo, dayOneAfternoon, current, lunch, dayOneMorning, dayOneParallel)

    @Test
    fun theTalksOfTheSameDayComeFirstInTimeOrderWithoutTheCurrentOne() {
        val targets = MoveTargets.of(current.id, all)

        assertEquals(listOf("n", "m", "p"), targets.sameDay.map { it.id.id })
        assertEquals(listOf("d"), targets.otherDays.map { it.id.id })
    }

    @Test
    fun breaksAndMealsAreNotOffered() {
        val targets = MoveTargets.of(current.id, all)

        assertEquals(false, (targets.sameDay + targets.otherDays).any { it.id == lunch.id })
    }

    @Test
    fun aSessionNoLongerInTheScheduleOffersEveryTalkAsAnotherDay() {
        val targets = MoveTargets.of(SessionId("gone"), all)

        assertEquals(emptyList(), targets.sameDay)
        assertEquals(listOf("n", "m", "c", "p", "d"), targets.otherDays.map { it.id.id })
    }
}
