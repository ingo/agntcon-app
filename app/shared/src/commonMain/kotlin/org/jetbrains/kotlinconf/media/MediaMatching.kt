package org.jetbrains.kotlinconf.media

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.isServiceEvent
import org.jetbrains.kotlinconf.minus
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Where a photo or video from the library goes, decided from when it was taken. */
sealed interface MediaMatch {
    /** One session fits: the one the attendee was in, or the only one running then. */
    data class Matched(val session: SessionId) : MediaMatch

    /** Several sessions could be it (a clash, or parallel talks none of which is marked): the attendee picks. */
    data class Ask(val candidates: List<SessionId>) : MediaMatch

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

    fun match(at: LocalDateTime?, sessions: List<SessionCardView>, attended: Set<SessionId>): MediaMatch {
        if (at == null) return MediaMatch.NoTime
        val (allDay, talks) = sessions.filterNot { it.isServiceEvent }.partition { it.endsAt - it.startsAt > ALL_DAY }

        fun inside(list: List<SessionCardView>) = list.filter { at >= it.startsAt && at < it.endsAt }
        fun near(list: List<SessionCardView>) = list.filter { it.startsAt - at <= EARLY && at - it.endsAt < LATE }
        fun oneOrAsk(list: List<SessionCardView>): MediaMatch? = when (list.size) {
            0 -> null
            1 -> MediaMatch.Matched(list.single().id)
            else -> MediaMatch.Ask(ordered(list, attended, at))
        }
        fun askAll(list: List<SessionCardView>): MediaMatch? = list.takeIf { it.isNotEmpty() }?.let { MediaMatch.Ask(ordered(it, attended, at)) }

        val mine = talks.filter { it.id in attended }
        return oneOrAsk(inside(mine))
            ?: oneOrAsk(near(mine))
            // Nothing marked: the only talk running then (a keynote) is a safe guess, parallel talks are not
            ?: inside(talks).singleOrNull()?.let { MediaMatch.Matched(it.id) }
            ?: askAll(inside(talks))
            ?: askAll(near(talks))
            ?: oneOrAsk(inside(allDay.filter { it.id in attended }))
            ?: between(at, talks, attended)
    }

    private fun between(at: LocalDateTime, talks: List<SessionCardView>, attended: Set<SessionId>): MediaMatch {
        val sameDay = talks.filter { it.startsAt.date == at.date }
        val pool = sameDay.filter { it.id in attended }.ifEmpty { sameDay }
        val before = pool.filter { it.endsAt <= at }.maxByOrNull { it.endsAt }
        val after = pool.filter { it.startsAt > at }.minByOrNull { it.startsAt }
        return MediaMatch.Between(before?.id, after?.id)
    }

    /** Marked sessions first, then the one that started most recently before the photo (the talk you sat down for). */
    private fun ordered(list: List<SessionCardView>, attended: Set<SessionId>, at: LocalDateTime): List<SessionId> =
        list.sortedWith(compareBy({ it.id !in attended }, { (at - it.startsAt).absoluteValue }, { it.title.lowercase() })).map { it.id }
}

/** Photos and videos with the same answer to give, shown together: one question per group, not per photo. */
data class MediaGroup(val match: MediaMatch, val items: List<Int>)

/**
 * Groups the items (by index) that share a match: all photos from one session, or all taken in the same
 * ambiguous slot. Questions come first, in the order the photos were taken; then the matched sessions.
 */
fun groupMatches(matches: List<MediaMatch>, times: List<LocalDateTime?>): List<MediaGroup> {
    val groups = matches.indices.groupBy { matches[it] }.map { (match, items) -> MediaGroup(match, items) }
    fun firstTime(group: MediaGroup) = group.items.mapNotNull { times[it] }.minOrNull()
    val (questions, matched) = groups.partition { it.match.needsAnswer }
    return questions.sortedWith(compareBy(nullsLast()) { firstTime(it) }) +
        matched.sortedWith(compareBy(nullsLast()) { firstTime(it) })
}
