package org.jetbrains.kotlinconf.directory

import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.isServiceEvent
import org.jetbrains.kotlinconf.utils.containsDiacritics
import org.jetbrains.kotlinconf.utils.removeDiacritics

/** A company and who from it speaks, for events whose speakers name their company. */
data class Company(
    val name: String,
    val url: String?,
    val speakers: List<Speaker>,
    /** Sessions given by its speakers. */
    val sessions: List<SessionCardView>,
    /** The sessions among them held in the exhibitor showcase. */
    val showcaseSessions: List<SessionCardView>,
)

/** Whether a session is an exhibitor talk: held in the event's showcase room. */
fun SessionCardView.isShowcase(showcaseRoom: String?): Boolean =
    showcaseRoom != null && locationLine.contains(showcaseRoom, ignoreCase = true)

/** Companies with at least one speaker, by name. Sessions without a speaker from a company belong to none. */
fun buildCompanies(
    speakers: List<Speaker>,
    sessions: List<SessionCardView>,
    showcaseRoom: String?,
): List<Company> {
    val talks = sessions.filterNot { it.isServiceEvent }
    return speakers
        .filter { it.company.isNotBlank() }
        .groupBy { it.company.lowercase() }
        .map { (_, group) ->
            val ids = group.mapTo(mutableSetOf()) { it.id }
            val companySessions = talks
                .filter { session -> session.speakerIds.any { it in ids } }
                .sortedWith(compareBy({ it.startsAt }, { it.title }))
            Company(
                name = group.first().company,
                url = group.firstNotNullOfOrNull { it.companyUrl },
                speakers = group.sortedBy { it.name.lowercase() },
                sessions = companySessions,
                showcaseSessions = companySessions.filter { it.isShowcase(showcaseRoom) },
            )
        }
        .sortedBy { it.name.lowercase() }
}

/** Companies whose name matches [query] (any case, accents ignored); everything for a blank query. */
fun List<Company>.search(query: String): List<Company> {
    if (query.isBlank()) return this
    val exact = query.containsDiacritics()
    val needle = query.trim().let { if (exact) it else it.removeDiacritics() }
    return filter { (if (exact) it.name else it.name.removeDiacritics()).contains(needle, ignoreCase = true) }
}

/** The companies with the most sessions first, for the schedule's company filter. */
fun companiesBySessionCount(sessions: List<SessionCardView>): List<String> = sessions
    .filterNot { it.isServiceEvent }
    .flatMap { it.companies }
    .groupingBy { it }.eachCount()
    .entries
    .sortedWith(compareBy({ -it.value }, { it.key.lowercase() }))
    .map { it.key }
