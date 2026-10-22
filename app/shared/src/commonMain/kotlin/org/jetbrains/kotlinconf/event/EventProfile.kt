package org.jetbrains.kotlinconf.event

import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.sessionize.SessionizeRules

/**
 * Everything that makes a build of the app specific to one conference.
 *
 * With [apiUrl] set, the app talks to the conference backend (schedule, conference info, voting,
 * feedback, documents, maps). Without it, the app reads the public Sessionize feed for [sessionizeId]
 * directly and the backend-only features are hidden.
 */
data class EventProfile(
    /** Matches the `event` Gradle property. */
    val id: String,
    val name: String,
    /** Scopes locally stored favorites and settings; the backend's year for backend events. */
    val year: Int,
    /** IANA zone id of the venue, e.g. "America/Los_Angeles". */
    val timeZoneId: String,
    val apiUrl: String? = null,
    val stagingApiUrl: String? = null,
    /** The id in `https://sessionize.com/api/v2/{id}/view/All`. Required without [apiUrl]. */
    val sessionizeId: String? = null,
    /** The event's Sessionize web app (`https://<event>.sessionize.com`), for bookmark sync. */
    val sessionizeAppUrl: String? = null,
    val sessionizeRules: SessionizeRules = SessionizeRules(),
    val aboutDescription: String = "",
    val websiteUrl: String? = null,
    val codeOfConductUrl: String? = null,
    val venueAddress: String? = null,
    val social: SocialLinks = SocialLinks(),
    val appStoreUrl: String? = null,
    val playStoreUrl: String? = null,
    /** Colors and logo; null keeps the KotlinConf look. */
    val theme: EventTheme? = null,
) {
    init {
        require(apiUrl != null || sessionizeId != null) { "Event '$id' needs an apiUrl or a sessionizeId" }
    }

    val hasBackend: Boolean get() = apiUrl != null

    val timeZone: TimeZone get() = TimeZone.of(timeZoneId)

    val sessionizeUrl: String? get() = sessionizeId?.let { "https://sessionize.com/api/v2/$it/view/All" }
}

data class SocialLinks(
    val twitter: String? = null,
    val slack: String? = null,
    val bluesky: String? = null,
)
