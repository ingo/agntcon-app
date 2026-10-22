package org.jetbrains.kotlinconf.screens

import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.isServiceEvent
import org.jetbrains.kotlinconf.utils.containsDiacritics
import org.jetbrains.kotlinconf.utils.removeDiacritics

/** How a session matched the schedule search; [matched] is false when it should be left out. */
class SessionMatch(
    val matched: Boolean,
    val tagMatches: List<String> = emptyList(),
    val titleHighlights: List<IntRange> = emptyList(),
    val speakerHighlights: List<IntRange> = emptyList(),
    /** The text matched in the description or the company, which the card does not show. */
    val matchedHiddenText: Boolean = false,
)

/** The search text and filters of the schedule's search, ready to test sessions against. */
class SessionSearch(
    query: String,
    private val tags: List<String> = emptyList(),
    private val company: String? = null,
) {
    private val diacriticsSearch = query.containsDiacritics()
    private val isBlank = query.isBlank()
    private val regex = Regex.escape(query).toRegex(RegexOption.IGNORE_CASE)

    private fun String.forSearch() = if (diacriticsSearch) this else removeDiacritics()

    /**
     * Title and speakers are highlighted on the card. The description and the speakers' companies
     * match too, so "kubernetes" or "okta" find sessions that never say it in the title.
     */
    fun match(session: SessionCardView): SessionMatch {
        if (session.isServiceEvent) return SessionMatch(matched = false)
        if (!session.tags.containsAll(tags)) return SessionMatch(matched = false)
        if (company != null && company !in session.companies) return SessionMatch(matched = false)

        val titleHighlights = regex.findAll(session.title.forSearch()).map { it.range }.toList()
        val speakerHighlights = regex.findAll(session.speakerLine.forSearch()).map { it.range }.toList()
        val hidden = !isBlank && (
            regex.containsMatchIn(session.description.forSearch()) ||
                session.companies.any { regex.containsMatchIn(it.forSearch()) }
            )

        return SessionMatch(
            matched = isBlank || titleHighlights.isNotEmpty() || speakerHighlights.isNotEmpty() || hidden,
            tagMatches = tags,
            titleHighlights = titleHighlights,
            speakerHighlights = speakerHighlights,
            matchedHiddenText = hidden,
        )
    }
}
