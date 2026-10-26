package org.jetbrains.kotlinconf.media

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.isServiceEvent
import org.jetbrains.kotlinconf.minus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Where a photo or video from the library goes, decided from when it was taken. */
sealed interface MediaMatch {
    /** One session fits: the one the attendee was in, or the only one running then. */
    data class Matched(val session: SessionId) : MediaMatch

    /**
     * Several sessions could be it (a clash, parallel talks none of which is marked, or a bookmarked session that ran
     * long or started early): the attendee picks. [candidates] are in time order; [likely] is preselected.
     */
    data class Ask(val candidates: List<SessionId>, val likely: SessionId? = null) : MediaMatch

    /** Taken outside any session, in a break or the evening: the sessions just before and after are offered. */
    data class Between(val before: SessionId?, val after: SessionId?) : MediaMatch

    /** The file does not say when it was taken. */
    data object NoTime : MediaMatch

    val needsAnswer: Boolean get() = this !is Matched
}

/**
 * Matches photos and videos to sessions by the time they were taken. A session counts from a few minutes before
 * it starts (people take a photo of the title slide early) to a quarter of an hour after it ends (questions, the
 * speaker's last slide). Breaks and other service events are never offered. Sessions the attendee bookmarked, noted,
 * rated or added media to ([attended]) win over the ones they did not mark. All-day items (registration, an exhibition
 * hall, a quiet room) run alongside everything, so they only count when nothing shorter fits.
 */
object MediaMatcher {
    val EARLY = 5.minutes
    val LATE = 15.minutes
    val ALL_DAY = 3.hours

    /** How far a bookmarked session may have run over, or the next one started early, and still be the likely one. */
    val RAN_LONG = 30.minutes
    val STARTED_EARLY = 20.minutes

    fun match(at: LocalDateTime?, sessions: List<SessionCardView>, attended: Set<SessionId>): MediaMatch {
        if (at == null) return MediaMatch.NoTime
        val (allDay, talks) = sessions.filterNot { it.isServiceEvent }.partition { it.endsAt - it.startsAt > ALL_DAY }

        fun inside(list: List<SessionCardView>) = list.filter { at >= it.startsAt && at < it.endsAt }
        fun near(list: List<SessionCardView>) = list.filter { it.startsAt - at <= EARLY && at - it.endsAt < LATE }
        val mine = talks.filter { it.id in attended }
        // The attendee's sessions just before and after on that day: the schedule slipping is the usual reason a
        // photo does not fall inside one
        val previous = mine.filter { it.startsAt.date == at.date && it.endsAt <= at }.maxByOrNull { it.endsAt }
        val next = mine.filter { it.startsAt.date == at.date && it.startsAt > at }.minByOrNull { it.startsAt }

        fun oneOrAsk(list: List<SessionCardView>): MediaMatch? = when (list.size) {
            0 -> null
            1 -> MediaMatch.Matched(list.single().id)
            // A clash: the one that started last is the talk sat down for; a true tie has no likely one
            else -> MediaMatch.Ask(
                timeOrder(list),
                likely = list.maxOf { it.startsAt }.let { latest -> list.filter { it.startsAt == latest }.singleOrNull()?.id },
            )
        }

        val ranLong = previous?.takeIf { at - it.endsAt <= RAN_LONG }
        val earlyStart = next?.takeIf { it.startsAt - at <= STARTED_EARLY }
        val drifted = listOfNotNull(ranLong, earlyStart)

        // Just before or after several of the attendee's sessions: the closest one in time is likely (a talk ran long)
        fun closestOrAsk(list: List<SessionCardView>): MediaMatch? = when (list.size) {
            0 -> null
            1 -> MediaMatch.Matched(list.single().id)
            else -> {
                val gaps = list.associateWith { gap(at, it).coerceAtLeast(kotlin.time.Duration.ZERO) }
                val closest = gaps.values.min()
                MediaMatch.Ask(timeOrder(list), likely = gaps.filterValues { it == closest }.keys.singleOrNull()?.id)
            }
        }

        // Sessions running at that moment that the candidates do not cover: a photo inside another session is not
        // silently filed under one that has ended, or that has not started yet
        fun running(candidates: List<SessionCardView>) = inside(talks).filter { r -> candidates.none { it.id == r.id } }

        /**
         * The candidates and whatever else is running, to ask. The one running is the likely answer when it is the
         * only one and every candidate is already over (the photo is inside it, the candidates are not).
         */
        fun askWithRunning(candidates: List<SessionCardView>): MediaMatch? {
            val others = running(candidates).takeIf { it.isNotEmpty() } ?: return null
            val likely = others.singleOrNull()?.takeIf { candidates.all { c -> at >= c.endsAt } }
            return MediaMatch.Ask(timeOrder(candidates + others), likely = likely?.id)
        }

        val nearMine = near(mine)
        return oneOrAsk(inside(mine))
            ?: nearMine.takeIf { it.isNotEmpty() }?.let { askWithRunning(it) }
            ?: closestOrAsk(nearMine)
            // Just outside a bookmarked session: it ran long or the next started early. Offer them, the closer one
            // preselected, unless something else is running (then see askWithRunning)
            ?: drifted.takeIf { it.isNotEmpty() }?.let { near ->
                askWithRunning(near) ?: MediaMatch.Ask(timeOrder(near), likely = near.minByOrNull { gap(at, it) }!!.id)
            }
            // Nothing marked: the only talk running then (a keynote) is a safe guess, parallel talks are not
            ?: inside(talks).singleOrNull()?.let { MediaMatch.Matched(it.id) }
            ?: inside(talks).takeIf { it.isNotEmpty() }?.let { MediaMatch.Ask(timeOrder(it + listOfNotNull(previous, next))) }
            ?: near(talks).takeIf { it.isNotEmpty() }?.let { MediaMatch.Ask(timeOrder(it + listOfNotNull(previous, next))) }
            ?: oneOrAsk(inside(allDay.filter { it.id in attended }))
            ?: between(at, talks, attended)
    }

    /** Minutes between a photo and a session it is not inside. */
    private fun gap(at: LocalDateTime, session: SessionCardView) =
        if (at < session.startsAt) session.startsAt - at else at - session.endsAt

    private fun between(at: LocalDateTime, talks: List<SessionCardView>, attended: Set<SessionId>): MediaMatch.Between {
        val sameDay = talks.filter { it.startsAt.date == at.date }
        val pool = sameDay.filter { it.id in attended }.ifEmpty { sameDay }
        val before = pool.filter { it.endsAt <= at }.maxByOrNull { it.endsAt }
        val after = pool.filter { it.startsAt > at }.minByOrNull { it.startsAt }
        return MediaMatch.Between(before?.id, after?.id)
    }

    /** In the order they happened, so the list reads like the schedule. */
    private fun timeOrder(list: List<SessionCardView>): List<SessionId> =
        list.distinctBy { it.id }.sortedWith(compareBy({ it.startsAt }, { it.endsAt }, { it.title.lowercase() })).map { it.id }
}

/** Photos and videos with the same answer to give, shown together: one question per group, not per photo. */
data class MediaGroup(val match: MediaMatch, val items: List<Int>)

/**
 * Groups the items (by index) that share a match: all photos from one session, or all taken in the same
 * ambiguous slot. Everything in the order the photos were taken, like the day itself; undated photos last.
 */
fun groupMatches(matches: List<MediaMatch>, times: List<LocalDateTime?>): List<MediaGroup> {
    val groups = matches.indices.groupBy { matches[it] }.map { (match, items) -> MediaGroup(match, items) }
    fun firstTime(group: MediaGroup) = group.items.mapNotNull { times[it] }.minOrNull()
    return groups.sortedWith(compareBy(nullsLast()) { firstTime(it) })
}

/**
 * Photos that are probably in the wrong session: one or two going to one session while the photos taken just before
 * and after them (within [maxGap]) went to another, the same one on both sides. Usually a slip while answering or
 * changing a group. [items] are each photo's time and assigned session (null when it is not being added; those are
 * ignored). Returns, by position in [items], the session the photo probably belongs to.
 */
fun findOutliers(
    items: List<Pair<LocalDateTime?, SessionId?>>,
    maxRun: Int = 2,
    maxGap: Duration = 15.minutes,
): Map<Int, SessionId> {
    val dated = items.indices
        .filter { items[it].first != null && items[it].second != null }
        .sortedBy { items[it].first }
    // Consecutive photos (in time) going to the same session
    val runs = mutableListOf<MutableList<Int>>()
    for (index in dated) {
        val last = runs.lastOrNull()
        if (last != null && items[last.last()].second == items[index].second) last += index else runs += mutableListOf(index)
    }
    val result = mutableMapOf<Int, SessionId>()
    for (r in 1 until runs.size - 1) {
        val (before, run, after) = Triple(runs[r - 1], runs[r], runs[r + 1])
        val around = items[before.last()].second
        if (run.size > maxRun || around != items[after.first()].second) continue
        val gapBefore = items[run.first()].first!! - items[before.last()].first!!
        val gapAfter = items[after.first()].first!! - items[run.last()].first!!
        if (gapBefore <= maxGap && gapAfter <= maxGap) run.forEach { result[it] = around!! }
    }
    return result
}
