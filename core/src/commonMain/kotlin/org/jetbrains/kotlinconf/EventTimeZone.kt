package org.jetbrains.kotlinconf

import kotlinx.datetime.TimeZone

/**
 * Time zone of the event. Session times are local to it.
 *
 * Set once at startup, before any schedule data is read: by the app from its event profile,
 * by the backend from `conference.timeZone`.
 */
var EVENT_TIME_ZONE: TimeZone = TimeZone.of("Europe/Berlin")
