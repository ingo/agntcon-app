package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reading the slide to tell parallel sessions apart; the four talks from TestFlight feedback at 13:45 on Friday. */
class SlideTextMatcherTest {
    private val hermes = SessionId("h") to "Hermes Agent: The Agent That Grows With You Jeffrey Quesnelle"
    private val idJag = SessionId("j") to "ID-JAG: Solving OAuth Sprawl for Enterprise AI Agents Aaron Parecki"
    private val discovery = SessionId("d") to "Interoperable Agent Discovery: AI Catalog, ARD, and the AGNTCY Directory Luca Muscariello"
    private val sdlc = SessionId("s") to "The Agentic SDLC: How We Shipped AI-Native Software at a Legacy Company Dana Smith"
    private val all = listOf(hermes, idJag, discovery, sdlc)

    @Test
    fun aTitleSlideNamesItsSession() {
        assertEquals(SessionId("j"), SlideTextMatcher.best("ID-JAG\nSolving OAuth sprawl\nAaron Parecki · Okta", all))
    }

    @Test
    fun aContentSlideWithDistinctWordsIsEnough() {
        assertEquals(
            SessionId("d"),
            SlideTextMatcher.best("How it fits together\nAGNTCY Directory  →  catalog lookup\nagent records", all),
        )
    }

    @Test
    fun wordsSharedByCandidatesDoNotCount() {
        // "agent" and "enterprise" alone say nothing useful
        assertNull(SlideTextMatcher.best("What an agent looks like in the enterprise", all))
    }

    @Test
    fun oneMatchingWordIsNotEnough() {
        assertNull(SlideTextMatcher.best("OAuth", all))
    }

    @Test
    fun aTieIsNoSuggestion() {
        assertNull(SlideTextMatcher.best("Hermes grows · AGNTCY directory", all))
    }

    @Test
    fun hyphenatedNamesCountAsOneWord() {
        // The content slide from the simulator check: "ID-JAG" twice, nothing else from the titles
        assertEquals(setOf("id-jag", "token", "exchange"), SlideTextMatcher.words("ID-JAG token exchange"))
        assertEquals(SessionId("j"), SlideTextMatcher.best("Present the ID-JAG to the resource AS · Solving sprawl", all))
    }

    @Test
    fun noTextNoSuggestion() {
        assertNull(SlideTextMatcher.best("", all))
        assertNull(SlideTextMatcher.best("Solving OAuth sprawl", listOf(idJag)))
    }
}
