package org.jetbrains.kotlinconf

import org.jetbrains.kotlinconf.event.ActiveEvent

object URLs {
    // API Endpoints: the conference backend, or Sessionize for events without one
    val PRODUCTION_URL: String get() = ActiveEvent.apiUrl ?: SESSIONIZE_URL
    val STAGING_URL: String get() = ActiveEvent.stagingApiUrl ?: PRODUCTION_URL
    const val SESSIONIZE_URL = "https://sessionize.com"
    const val LOCAL_URL = "http://0.0.0.0:8080"
    const val ANDROID_LOCAL_URL = "http://10.0.2.2:8080"

    // Conference website
    val CONFERENCE_HOMEPAGE: String? get() = ActiveEvent.websiteUrl

    // Social Media URLs
    val TWITTER: String? get() = ActiveEvent.social.twitter
    val SLACK: String? get() = ActiveEvent.social.slack
    val BLUESKY: String? get() = ActiveEvent.social.bluesky

    // Repository URL
    const val GITHUB_REPO = "https://github.com/JetBrains/kotlinconf-app"

    // App stores
    val PLAY_STORE: String? get() = ActiveEvent.playStoreUrl
    val APP_STORE: String? get() = ActiveEvent.appStoreUrl

    // Other
    const val JUNIE_LANDING_PAGE = "https://www.jetbrains.com/junie/"

}
