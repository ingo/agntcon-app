package org.jetbrains.kotlinconf

import org.jetbrains.kotlinconf.event.ActiveEvent

object URLs {
    // Conference website
    val CONFERENCE_HOMEPAGE: String? get() = ActiveEvent.websiteUrl

    // Social Media URLs
    val TWITTER: String? get() = ActiveEvent.social.twitter
    val SLACK: String? get() = ActiveEvent.social.slack
    val BLUESKY: String? get() = ActiveEvent.social.bluesky

    // Repository URL
    const val GITHUB_REPO = "https://github.com/ingo/agntcon-app"

    // App stores
    val PLAY_STORE: String? get() = ActiveEvent.playStoreUrl
    val APP_STORE: String? get() = ActiveEvent.appStoreUrl
}
