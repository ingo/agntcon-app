package org.jetbrains.kotlinconf.event

import org.jetbrains.kotlinconf.sessionize.SessionizeRules

/**
 * The conferences this app can be built for. Pick one with `-Pevent=<id>`
 * (or `ORG_GRADLE_PROJECT_event=<id>` for Xcode builds).
 *
 * To add a conference that publishes its schedule on Sessionize, add a profile with its
 * Sessionize API id. On an event's public schedule page the id appears in embed URLs such as
 * `https://sessionize.com/api/v2/{id}/view/GridSmart`; the organiser can also enable the API.
 */
object EventProfiles {
    val AgntconMcpconNa2026 = EventProfile(
        id = "agntcon-mcpcon-na-2026",
        name = "AGNTCon + MCPCon North America 2026",
        year = 2026,
        timeZoneId = "America/Los_Angeles",
        sessionizeId = "asqam7rn",
        sessionizeAppUrl = "https://agntcon-mcpcon-na-2026.sessionize.com",
        // Speakers upload slides (PDF or PowerPoint) or a poster image here
        sessionizeRules = SessionizeRules(slidesQuestion = "Presentation or Poster"),
        aboutDescription = "October 22–23, 2026\nSan Jose McEnery Convention Center, San Jose, CA\n\n" +
            "The AI agents conference: agentic AI infrastructure, production agent systems, and MCP.",
        websiteUrl = "https://events.linuxfoundation.org/agntcon-mcpcon-north-america/",
        codeOfConductUrl = "https://events.linuxfoundation.org/agntcon-mcpcon-north-america/attend/code-of-conduct/",
        venueAddress = "San Jose McEnery Convention Center, 150 W San Carlos St, San Jose, CA 95113",
        venueMap = SanJoseMcEneryConventionCenter,
        showcaseRoom = "Solutions Showcase",
        theme = AgntconTheme.theme,
    )

    val all: List<EventProfile> = listOf(AgntconMcpconNa2026)

    fun byId(id: String): EventProfile =
        all.find { it.id == id } ?: error("Unknown event '$id'. Known events: ${all.joinToString { it.id }}")
}

/** The conference this build is for. */
val ActiveEvent: EventProfile = EventProfiles.byId(ACTIVE_EVENT_ID)
