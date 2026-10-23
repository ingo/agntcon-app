package org.jetbrains.kotlinconf.event

import kotlinx.datetime.TimeZone
import org.jetbrains.kotlinconf.sessionize.SessionizeRules

/**
 * Everything that makes a build of the app specific to one conference. The app reads the
 * conference's public Sessionize feed ([sessionizeId]) directly; there is no server of its own.
 */
data class EventProfile(
    /** Matches the `event` Gradle property. */
    val id: String,
    val name: String,
    /** Scopes the locally stored bookmarks, notes and caches of this conference. */
    val year: Int,
    /** IANA zone id of the venue, e.g. "America/Los_Angeles". */
    val timeZoneId: String,
    /** The id in `https://sessionize.com/api/v2/{id}/view/All`. */
    val sessionizeId: String,
    /** The event's Sessionize web app (`https://<event>.sessionize.com`), for bookmark sync. */
    val sessionizeAppUrl: String? = null,
    val sessionizeRules: SessionizeRules = SessionizeRules(),
    val aboutDescription: String = "",
    val websiteUrl: String? = null,
    val codeOfConductUrl: String? = null,
    val venueAddress: String? = null,
    /** A floor plan shipped with the app. */
    val venueMap: VenueMap? = null,
    /** Sessions in a room whose name contains this are exhibitor talks; the directory lists them by company. */
    val showcaseRoom: String? = null,
    val social: SocialLinks = SocialLinks(),
    val appStoreUrl: String? = null,
    val playStoreUrl: String? = null,
    /** Colors and logo; null keeps the default look. */
    val theme: EventTheme? = null,
) {
    val timeZone: TimeZone get() = TimeZone.of(timeZoneId)

    val sessionizeUrl: String get() = "https://sessionize.com/api/v2/$sessionizeId/view/All"
}

data class SocialLinks(
    val twitter: String? = null,
    val slack: String? = null,
    val bluesky: String? = null,
)
