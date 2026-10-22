package org.jetbrains.kotlinconf

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.screens.minutesUntil
import org.jetbrains.kotlinconf.utils.DateTimeFormatting

data class Day(
    val date: LocalDate,
    val timeSlots: List<TimeSlot>
)

data class TimeSlot(
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val state: SessionState,
    val sessions: List<SessionCardView>,
) {
    val title: String = DateTimeFormatting.timeToTime(startsAt, endsAt)
}

fun Conference.buildAgenda(
    favorites: Set<SessionId>,
    votes: List<VoteInfo>,
    now: LocalDateTime,
): List<Day> {
    val votesBySessionId = votes.associateBy { it.sessionId }
    return sessions
        .groupBy { it.startsAt.date }
        .map { (date, sessions) ->
            Day(
                date = date,
                timeSlots = sessions.groupByTime(
                    conference = this,
                    now = now,
                    favorites = favorites,
                    votes = votesBySessionId,
                )
            )
        }
        .sortedBy { it.date }
}

private data class SlotTimes(
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
) {
    fun contains(start: LocalDateTime, end: LocalDateTime): Boolean = start >= startsAt && end <= endsAt
}

fun List<Session>.groupByTime(
    conference: Conference,
    now: LocalDateTime,
    favorites: Set<SessionId>,
    votes: Map<SessionId, VoteInfo>,
): List<TimeSlot> {
    val regularSlots: List<SlotTimes> = filterNot { it.isLightning }
        .map { SlotTimes(it.startsAt, it.endsAt) }
        .distinct()
    // Slots spanning other slots (all-day registration, an exhibition hall) don't take in shorter sessions
    val enclosingSlots = regularSlots.filter { slot ->
        regularSlots.none { other -> other != slot && slot.contains(other.startsAt, other.endsAt) }
    }

    // Regular sessions get their own slot; lightning talks join the smallest slot around them, or get their own
    val slotOf: Map<Session, SlotTimes> = associateWith { session ->
        val own = SlotTimes(session.startsAt, session.endsAt)
        if (!session.isLightning) return@associateWith own
        enclosingSlots
            .filter { it.contains(session.startsAt, session.endsAt) }
            .minByOrNull { it.endsAt - it.startsAt }
            ?: own
    }

    val slotsToSessions: Map<SlotTimes, MutableList<SessionCardView>> = slotOf.values
        .distinct()
        .sortedWith(compareBy({ it.startsAt }, { it.endsAt }))
        .associateWith { mutableListOf() }

    val speakersById = conference.speakers.associateBy { it.id }

    this.forEach { session ->
        slotsToSessions.getValue(slotOf.getValue(session)).add(
            session.asSessionCard(speakersById, now, favorites, votes[session.id])
        )
    }

    return slotsToSessions.mapNotNull { (slot, sessions) ->
        if (sessions.isNotEmpty()) {
            TimeSlot(
                startsAt = slot.startsAt,
                endsAt = slot.endsAt,
                state = SessionState.from(slot.startsAt, slot.endsAt, now),
                sessions = sessions.sortedBy { it.isLightning },
            )
        } else {
            null
        }
    }
}

fun Session.asSessionCard(
    speakersById: Map<SpeakerId, Speaker>,
    now: LocalDateTime,
    favorites: Set<SessionId>,
    vote: VoteInfo?,
): SessionCardView {
    return SessionCardView(
        id = id,
        title = title,
        speakerLine = speakerIds.mapNotNull { speakersById[it] }.joinToString { it.name },
        locationLine = location,
        isFavorite = id in favorites,
        startsAt = startsAt,
        endsAt = endsAt,
        state = SessionState.from(startsAt, endsAt, now),
        speakerIds = speakerIds,
        vote = vote?.score,
        description = description,
        tags = tags?.toSet() ?: emptySet(),
        startsInMinutes = minutesUntil(startsAt, now).let { diff ->
            if (diff in 1..30) diff else null
        },
        videoUrl = videoUrl,
        slidesUrl = slidesUrl,
        companies = speakerIds.mapNotNull { speakersById[it]?.company?.takeIf { company -> company.isNotBlank() } }.distinct(),
    )
}
