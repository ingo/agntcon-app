package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.isServiceEvent

/**
 * The sessions a photo or video on [from] can be moved to, in time order: the talks of the same day first (a photo
 * on the wrong talk almost always belongs to one just before or after), then those of the other days.
 */
data class MoveTargets(val sameDay: List<SessionCardView>, val otherDays: List<SessionCardView>) {
    companion object {
        fun of(from: SessionId, sessions: List<SessionCardView>): MoveTargets {
            val day = sessions.find { it.id == from }?.startsAt?.date
            val talks = sessions
                .filter { it.id != from && !it.isServiceEvent }
                .sortedWith(compareBy({ it.startsAt }, { it.endsAt }, { it.title.lowercase() }))
            val (sameDay, otherDays) = talks.partition { it.startsAt.date == day }
            return MoveTargets(sameDay, otherDays)
        }
    }
}
