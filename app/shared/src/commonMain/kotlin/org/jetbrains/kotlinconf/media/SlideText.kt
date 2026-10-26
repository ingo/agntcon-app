package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId

/**
 * The text on a photo (a slide, a title card), read on the device; null where the platform cannot read text or
 * nothing was found. Nothing leaves the phone.
 */
expect suspend fun recognizeText(path: String): String?

/**
 * Picks the session a slide photo belongs to from the words on it. A word only counts for the sessions that have it:
 * in one session's text it is worth a full point, in two it is worth half and in more it says nothing. A candidate
 * wins with at least two points and more than any other; otherwise there is no suggestion and the attendee picks.
 * A session's text is its title, speakers, their companies and its description (see [candidateText]).
 */
object SlideTextMatcher {
    private val STOP = setOf(
        "with", "from", "that", "this", "your", "what", "when", "where", "which", "into", "over", "they", "their",
        "have", "will", "about", "more", "than", "then", "them", "these", "those", "using", "building", "build",
        "how", "for", "and", "the", "are", "you", "our", "can", "not", "all", "its", "new", "way", "why",
        "session", "talk", "keynote", "workshop", "panel", "demo", "theater", "agent", "agents", "agentic",
    )

    /** The points a slide needs to name a session. */
    const val MIN_SCORE = 2.0

    fun words(text: String): Set<String> =
        // Hyphenated names ("ID-JAG", "A2A-native") count as one word
        Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*").findAll(text.lowercase()).map { it.value }
            .filter { it.length >= 4 && it !in STOP }
            .toSet()

    /** What a slide can say about [session]: title, speakers, companies and description. */
    fun candidateText(session: SessionCardView): String =
        listOf(session.title, session.speakerLine, session.companies.joinToString(" "), session.description)
            .filter { it.isNotBlank() }.joinToString("\n")

    /** Each candidate's points for the words on the slide; empty when there is nothing to compare. */
    fun scores(slideText: String, candidates: List<Pair<SessionId, String>>): Map<SessionId, Double> {
        val onSlide = words(slideText)
        if (onSlide.isEmpty() || candidates.isEmpty()) return emptyMap()
        val perCandidate = candidates.map { (id, text) -> id to words(text) }
        val counts = perCandidate.flatMap { it.second }.groupingBy { it }.eachCount()
        return perCandidate.associate { (id, ws) ->
            id to ws.sumOf { w ->
                if (w !in onSlide) 0.0 else when (counts[w]) { 1 -> 1.0; 2 -> 0.5; else -> 0.0 }
            }
        }
    }

    /** [candidates] are each session's text, see [candidateText]. */
    fun best(slideText: String, candidates: List<Pair<SessionId, String>>): SessionId? {
        if (candidates.size < 2) return null
        val ranked = scores(slideText, candidates).entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)?.value ?: 0.0
        return if (top.value >= MIN_SCORE && top.value > second) top.key else null
    }

    /**
     * What a slide says about a photo filed under [current]: the session it names, if it clearly names one
     * ([best]); a different one only when it also scores at least twice as much as [current] does, so a slide that
     * fits the current session is left alone.
     */
    fun named(slideText: String, current: SessionId?, candidates: List<Pair<SessionId, String>>): SessionId? {
        val winner = best(slideText, candidates) ?: return null
        if (current == null || winner == current) return winner
        val all = scores(slideText, candidates)
        return winner.takeIf { (all[it] ?: 0.0) >= 2 * (all[current] ?: 0.0) }
    }
}
