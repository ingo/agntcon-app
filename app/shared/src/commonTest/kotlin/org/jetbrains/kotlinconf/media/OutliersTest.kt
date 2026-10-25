package org.jetbrains.kotlinconf.media

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals

/** A photo or two in the middle of a series that went to a different session is flagged. */
class OutliersTest {
    private val a = SessionId("a")
    private val b = SessionId("b")
    private val c = SessionId("c")
    private fun at(time: String) = LocalDateTime.parse("2026-10-23T$time")

    @Test
    fun oneOrTwoInTheMiddleOfASeriesAreFlagged() {
        val items = listOf(
            at("10:00") to a, at("10:02") to a, at("10:04") to b, at("10:05") to a, at("10:07") to a,
        )
        assertEquals(mapOf(2 to a), findOutliers(items))
        val two = listOf(at("10:00") to a, at("10:03") to b, at("10:04") to b, at("10:06") to a)
        assertEquals(mapOf(1 to a, 2 to a), findOutliers(two))
    }

    @Test
    fun aRealChangeOfSessionIsNotFlagged() {
        // Three or more in a row, or different sessions on each side: the attendee moved on
        val three = listOf(at("10:00") to a, at("10:02") to b, at("10:03") to b, at("10:04") to b, at("10:06") to a)
        assertEquals(emptyMap(), findOutliers(three))
        val moved = listOf(at("10:00") to a, at("10:30") to b, at("11:00") to c)
        assertEquals(emptyMap(), findOutliers(moved))
    }

    @Test
    fun farApartInTimeIsNotFlagged() {
        // A photo in a break between two parts of the same workshop may well be something else
        val items = listOf(at("10:00") to a, at("10:30") to b, at("11:00") to a)
        assertEquals(emptyMap(), findOutliers(items))
    }

    @Test
    fun photosNotAddedOrUndatedAreIgnored() {
        val items = listOf(at("10:00") to a, at("10:01") to null, at("10:02") to b, null to b, at("10:03") to a)
        assertEquals(mapOf(2 to a), findOutliers(items))
    }

    @Test
    fun theSimulatorCase() {
        // 10:16 and 10:18 both answered "How Anthropic…", then 10:18 changed to "Predictable Correctness" by mistake;
        // 10:20–10:24 went to "How Anthropic…" too
        val anthropic = SessionId("h")
        val predictable = SessionId("p")
        val items = listOf(
            at("10:16") to anthropic, at("10:18") to predictable,
            at("10:20") to anthropic, at("10:22") to anthropic, at("10:24") to anthropic,
        )
        assertEquals(mapOf(1 to anthropic), findOutliers(items))
    }

    @Test
    fun theOrderOfTheListDoesNotMatterOnlyTheTime() {
        val items = listOf(at("10:05") to a, at("10:04") to b, at("10:00") to a)
        assertEquals(mapOf(1 to a), findOutliers(items))
    }
}
