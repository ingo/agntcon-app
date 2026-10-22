package org.jetbrains.kotlinconf.event

import org.jetbrains.kotlinconf.sessionize.SessionizeRules

/**
 * The conferences this app can be built for. Pick one with `-Pevent=<id>`
 * (or `ORG_GRADLE_PROJECT_event=<id>` for Xcode builds); the default is `kotlinconf`.
 *
 * To add a conference that publishes its schedule on Sessionize, add a profile with its
 * Sessionize API id. On an event's public schedule page the id appears in embed URLs such as
 * `https://sessionize.com/api/v2/{id}/view/GridSmart`; the organiser can also enable the API.
 */
object EventProfiles {
    val KotlinConf = EventProfile(
        id = "kotlinconf",
        name = "KotlinConf",
        year = 2026,
        timeZoneId = "Europe/Berlin",
        apiUrl = "https://kotlinconf-app-prod.labs.jb.gg",
        stagingApiUrl = "https://kotlin-conf-staging.labs.jb.gg",
        sessionizeRules = SessionizeRules.KOTLINCONF,
        websiteUrl = "https://kotlinconf.com/",
        social = SocialLinks(
            twitter = "https://x.com/kotlinconf",
            slack = "https://slack-chats.kotlinlang.org/c/kotlinconf",
            bluesky = "https://bsky.app/profile/kotlinconf.com",
        ),
        appStoreUrl = "https://apps.apple.com/us/app/kotlinconf/id1299196584",
        playStoreUrl = "https://play.google.com/store/apps/details?id=com.jetbrains.kotlinconf",
    )

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
        theme = AgntconTheme.theme,
    )

    val all: List<EventProfile> = listOf(KotlinConf, AgntconMcpconNa2026)

    fun byId(id: String): EventProfile =
        all.find { it.id == id } ?: error("Unknown event '$id'. Known events: ${all.joinToString { it.id }}")
}

/** The conference this build is for. */
val ActiveEvent: EventProfile = EventProfiles.byId(ACTIVE_EVENT_ID)
