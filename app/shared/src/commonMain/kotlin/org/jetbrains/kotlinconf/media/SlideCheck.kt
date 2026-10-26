package org.jetbrains.kotlinconf.media

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.isServiceEvent
import org.jetbrains.kotlinconf.minus
import kotlin.time.Duration.Companion.minutes

/** Photos of a group that the text on them says belong to [session] ([items] are positions in the group). */
data class SlideFinding(val session: SessionId, val items: Set<Int>)

/**
 * Checks what the slides in a group of photos say against the session the group is filed under, whatever the time
 * said. It catches a talk that was matched by time but is not the one on the slides (the wrong room), and photos at
 * the start or end of a group that show the neighbouring talk (a talk that overran its slot). It only reports: the
 * attendee decides.
 */
object SlideCheck {
    /** How many photos are read to judge a whole group. */
    const val SAMPLE = 5

    /** How many photos at the start or end of a group are read, one after the other, to find where a run stops. */
    const val MAX_EDGE = 12

    /** How far around a group's photos another session still counts as running or nearby. */
    val NEARBY = 30.minutes

    /** The sessions the slides could be from: everything on that day that ran around the photos, parallel ones too. */
    fun candidates(from: LocalDateTime?, to: LocalDateTime?, sessions: List<SessionCardView>): List<SessionCardView> {
        if (from == null || to == null) return emptyList()
        return sessions.filterNot { it.isServiceEvent }
            .filter { it.endsAt - it.startsAt <= MediaMatcher.ALL_DAY }
            .filter { it.startsAt.date == from.date && it.startsAt - to <= NEARBY && from - it.endsAt < NEARBY }
    }

    /**
     * [order] lists the group's positions in the order the photos were taken; [read] gives the text on one of them.
     * [current] is the session the group is filed under (null when none is chosen yet). [edgeStart] and [edgeEnd] say
     * whether the first or last photos are close enough to the session's start or end to be another talk's slides.
     */
    suspend fun check(
        order: List<Int>,
        current: SessionId?,
        candidates: List<Pair<SessionId, String>>,
        edgeStart: Boolean,
        edgeEnd: Boolean,
        read: suspend (Int) -> String?,
    ): SlideFinding? {
        if (order.isEmpty() || candidates.size < 2) return null
        val cache = mutableMapOf<Int, SessionId?>()
        // The session a photo's slide names, or null when there is no text or it names none clearly
        suspend fun verdict(pos: Int): SessionId? = if (pos in cache) cache.getValue(pos) else
            (read(order[pos])?.let { SlideTextMatcher.named(it, current, candidates) }).also { cache[pos] = it }

        // The whole group: an even spread of photos all name one other session and none names the current one
        val sample = if (order.size <= SAMPLE) order.indices.toList()
        else (0 until SAMPLE).map { it * (order.size - 1) / (SAMPLE - 1) }.distinct()
        val named = sample.mapNotNull { verdict(it) }
        val other = named.filter { it != current }.groupingBy { it }.eachCount().maxByOrNull { it.value }
        if (other != null && named.all { it == other.key } && other.value >= minOf(2, sample.size)) {
            return SlideFinding(other.key, order.toSet())
        }
        if (current == null) return null

        // Runs at the edges: read one photo after the other until the slides stop naming the other session
        suspend fun run(positions: List<Int>, wanted: SessionId?): Pair<SessionId, List<Int>>? {
            var target = wanted
            var last = -1
            var unknown = 0
            for ((n, pos) in positions.take(MAX_EDGE).withIndex()) {
                val v = verdict(pos)
                if (v == current) break
                if (v == null) { if (++unknown > 2) break else continue }
                if (target != null && v != target) break
                target = v
                last = n
                unknown = 0
            }
            return target?.takeIf { last >= 0 }?.let { it to positions.take(last + 1) }
        }
        val leading = if (edgeStart) run(order.indices.toList(), null) else null
        val trailing = if (edgeEnd) run(order.indices.reversed().toList(), leading?.first) else null
        val target = leading?.first ?: trailing?.first ?: return null
        val flagged = (leading?.second.orEmpty() + trailing?.second.orEmpty()).map { order[it] }.toSet()
        return SlideFinding(target, flagged)
    }
}
