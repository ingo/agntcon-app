package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.asSessionCard
import org.jetbrains.kotlinconf.event.EventProfiles
import org.jetbrains.kotlinconf.sessionize.parseSessionizeData
import org.jetbrains.kotlinconf.sessionize.toConference
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The slides and recording links on the session page, from the AGNTCon profile and its real feed. */
class SessionLinksTest {
    private val event = EventProfiles.AgntconMcpconNa2026

    private val cards = parseSessionizeData(
        File(System.getProperty("sessionize.testdata"), "agntcon-mcpcon-na-2026.json").readText()
    ).toConference(event.timeZone, event.sessionizeRules).let { conference ->
        val speakers = conference.speakers.associateBy { it.id }
        conference.sessions.map { it.asSessionCard(speakers, LocalDateTime(2026, 10, 22, 11, 0), emptySet(), null) }
    }

    @Test
    fun everySessionWithAnUploadShowsSlides() {
        val withSlides = cards.filter { it.slidesUrl != null }
        assertEquals(67, withSlides.size)
        assertTrue(withSlides.any { it.slidesUrl!!.endsWith(".pptx") }, "PowerPoint decks count as slides")
        assertTrue(cards.filter { it.title == "Prompt à la Carte" }.all { it.slidesUrl == null }, "an event logo is not slides")
    }

    @Test
    fun noRecordingLinkBeforeRecordingsArePublished() {
        assertTrue(cards.all { it.videoUrl == null })
    }
}
