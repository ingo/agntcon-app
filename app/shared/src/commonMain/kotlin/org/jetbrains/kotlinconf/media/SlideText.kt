package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.SessionId

/**
 * The text on a photo (a slide, a title card), read on the device; null where the platform cannot read text or
 * nothing was found. Nothing leaves the phone.
 */
expect suspend fun recognizeText(path: String): String?

/**
 * Picks the session a slide photo belongs to from the words on it. Only words that tell the candidates apart count:
 * a word in two candidates' titles says nothing. A candidate wins with at least two such words on the slide and more
 * than any other candidate; otherwise there is no suggestion and the attendee picks.
 */
object SlideTextMatcher {
    private val STOP = setOf(
        "with", "from", "that", "this", "your", "what", "when", "where", "which", "into", "over", "they", "their",
        "have", "will", "about", "more", "than", "then", "them", "these", "those", "using", "building", "build",
        "how", "for", "and", "the", "are", "you", "our", "can", "not", "all", "its", "new", "way", "why",
        "session", "talk", "keynote", "workshop", "panel", "demo", "theater", "agent", "agents", "agentic",
    )

    fun words(text: String): Set<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }
            .filter { it.length >= 4 && it !in STOP }
            .toSet()

    /** [candidates] are each session's title and speaker names. */
    fun best(slideText: String, candidates: List<Pair<SessionId, String>>): SessionId? {
        if (candidates.size < 2) return null
        val onSlide = words(slideText)
        if (onSlide.isEmpty()) return null
        val perCandidate = candidates.map { (id, text) -> id to words(text) }
        val counts = perCandidate.flatMap { it.second }.groupingBy { it }.eachCount()
        val scores = perCandidate.map { (id, ws) -> id to ws.count { counts[it] == 1 && it in onSlide } }
            .sortedByDescending { it.second }
        val (winner, top) = scores[0]
        val second = scores.getOrNull(1)?.second ?: 0
        return if (top >= 2 && top > second) winner else null
    }
}
