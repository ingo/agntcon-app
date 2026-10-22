package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.buildAgenda
import org.jetbrains.kotlinconf.minus
import org.jetbrains.kotlinconf.sessionize.SessionizeRules
import org.jetbrains.kotlinconf.sessionize.parseSessionizeData
import org.jetbrains.kotlinconf.sessionize.toConference
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The schedule list and the personal calendar, built from real feeds with many random sets of
 * bookmarks, checked against rules that must always hold. Covers what hand-written examples miss,
 * like the duplicate list keys that crashed the bookmarks view.
 */
class RealFeedScheduleTest {
    private class Event(val file: String, val zone: String, val rules: SessionizeRules = SessionizeRules())

    private val events = listOf(
        Event("agntcon-mcpcon-na-2026.json", "America/Los_Angeles"),
        Event("kotlinconf-2026.json", "Europe/Berlin", SessionizeRules.KOTLINCONF),
    )

    private fun conference(event: Event): Conference =
        parseSessionizeData(File(System.getProperty("sessionize.testdata"), event.file).readText())
            .toConference(TimeZone.of(event.zone), event.rules)

    /** No bookmarks, all bookmarked, and seeded random subsets of different sizes. */
    private fun favoriteSets(conference: Conference): List<Set<SessionId>> {
        val ids = conference.sessions.map { it.id }
        val random = Random(42)
        return listOf(emptySet<SessionId>(), ids.toSet()) +
            List(40) { i -> ids.filter { random.nextDouble() < (i + 1) / 41.0 }.toSet() }
    }

    // Before, during and after the event
    private val nows = listOf("2026-01-01T00:00", "2026-05-21T11:00", "2026-10-22T11:00", "2027-01-01T00:00")
        .map(LocalDateTime::parse)

    @Test
    fun everySessionIsInExactlyOneTimeSlotThatContainsIt() = forEachCase { event, conference, favorites, now ->
        val days = conference.buildAgenda(favorites, emptyList(), now)
        val placed = days.flatMap { day -> day.timeSlots.flatMap { slot -> slot.sessions.map { slot to it } } }

        assertEquals(conference.sessions.size, placed.size, "${event.file}: sessions lost or duplicated")
        assertEquals(conference.sessions.map { it.id }.toSet(), placed.map { it.second.id }.toSet())
        for ((slot, session) in placed) {
            assertTrue(session.startsAt >= slot.startsAt && session.endsAt <= slot.endsAt, "${session.title} outside ${slot.title}")
            // A long session (registration, an exhibition) must not swallow the talks inside it
            if (!session.isLightning) {
                assertTrue(slot.startsAt == session.startsAt && slot.endsAt == session.endsAt, "${session.title} filed under ${slot.title}")
            }
        }
    }

    @Test
    fun scheduleListKeysAreUnique() = forEachCase { event, conference, favorites, now ->
        val days = conference.buildAgenda(favorites, emptyList(), now)
        for (bookmarkedOnly in listOf(false, true)) {
            val keys = buildNonSearchItems(days, now, bookmarkedOnly).first.map { it.listKey }
            val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (duplicates.isNotEmpty()) {
                fail("${event.file} (bookmarked only: $bookmarkedOnly, ${favorites.size} bookmarks): duplicate keys $duplicates")
            }
        }
    }

    @Test
    fun myScheduleIsConsistent() = forEachCase { event, conference, favorites, now ->
        val days = buildMySchedule(conference.buildAgenda(favorites, emptyList(), now))
        for (day in days) {
            val where = "${event.file} ${day.date} (${favorites.size} bookmarks)"
            val timedFavorites = day.sessions.filter { it.isFavorite }

            // Every bookmarked session is shown once: on the timeline or in the all-day strip
            assertEquals(timedFavorites.map { it.id.id }.sorted(), day.booked.map { it.session.id.id }.sorted(), where)
            assertTrue(day.allDay.all { it.isFavorite && it.endsAt - it.startsAt >= ALL_DAY_MIN }, where)

            for (block in day.booked) {
                assertTrue(block.column in 0..<block.columns, "$where: column ${block.column} of ${block.columns}")
                val actual = day.booked.filter { it !== block && it.session.overlaps(block.session) }.map { it.session.id }.toSet()
                assertEquals(actual, block.overlapsWith.map { it.id }.toSet(), "$where: clashes of ${block.session.title}")
            }
            // Sessions sharing a column never overlap, so nothing is drawn on top of anything else
            for (a in day.booked) for (b in day.booked) {
                if (a !== b && a.column == b.column && a.session.overlaps(b.session)) {
                    fail("$where: ${a.session.title} and ${b.session.title} share column ${a.column}")
                }
            }
            assertEquals(day.booked.count { it.overlapsWith.isNotEmpty() }, day.clashCount, where)

            // Free time lies inside the day, outside every bookmarked session, in order, never too short
            for ((a, b) in day.free.zipWithNext()) assertTrue(a.endsAt <= b.startsAt, "$where: free blocks overlap")
            for (free in day.free) {
                assertTrue(free.startsAt >= day.startsAt && free.endsAt <= day.endsAt, "$where: free outside the day")
                assertTrue(free.endsAt - free.startsAt >= FREE_MIN, "$where: free block too short")
                assertTrue(
                    timedFavorites.none { it.startsAt < free.endsAt && free.startsAt < it.endsAt },
                    "$where: free ${free.startsAt.time}-${free.endsAt.time} overlaps a bookmarked session",
                )
                assertTrue(free.available.all { !it.isFavorite && it.startsAt >= free.startsAt && it.startsAt < free.endsAt }, where)
            }
            // Each session you could add appears in at most one free block
            val offered = day.free.flatMap { it.available.map { s -> s.id } }
            assertEquals(offered.size, offered.toSet().size, "$where: session offered twice")
        }
    }

    @Test
    fun agntconFreeTimeFollowsTheRounds() {
        val conference = conference(events.first())
        // The case from the first try-out: talks at 10:35 and 14:20 bookmarked, nothing in between
        val favorites = conference.sessions
            .filter { it.title.startsWith("Don't Merge That") || it.title.startsWith("Before the Agent Writes Code") }
            .map { it.id }
            .toSet()
        assertEquals(2, favorites.size)

        val day = buildMySchedule(conference.buildAgenda(favorites, emptyList(), nows[2]))
            .single { it.date.toString() == "2026-10-22" }
        assertEquals(
            listOf("11:00-11:35", "11:35-12:10", "12:10-13:25", "13:25-14:20"),
            day.free
                .filter { it.startsAt.time.toString() >= "11:00" && it.endsAt.time.toString() <= "14:20" }
                .map { "${it.startsAt.time}-${it.endsAt.time}" },
        )
    }

    private fun SessionCardView.overlaps(other: SessionCardView): Boolean =
        startsAt < other.endsAt && other.startsAt < endsAt

    private fun forEachCase(check: (Event, Conference, Set<SessionId>, LocalDateTime) -> Unit) {
        for (event in events) {
            val conference = conference(event)
            for (favorites in favoriteSets(conference)) for (now in nows) check(event, conference, favorites, now)
        }
    }
}
