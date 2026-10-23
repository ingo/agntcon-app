package org.jetbrains.kotlinconf.event

import org.jetbrains.kotlinconf.ConferenceInfo
import org.jetbrains.kotlinconf.MapData
import org.jetbrains.kotlinconf.sessionize.SessionizeData
import org.jetbrains.kotlinconf.sessionize.toTagInfo

/**
 * Conference info for an event: the about text and venue come from the profile, the schedule
 * filters from the feed's categories, the map from the profile's venue map if it has one. The
 * schedule takes its days from the sessions.
 */
fun EventProfile.toConferenceInfo(feed: SessionizeData): ConferenceInfo = ConferenceInfo(
    aboutHeader = name,
    aboutDescription = aboutDescription,
    days = emptyList(),
    tags = feed.toTagInfo(sessionizeRules),
    mapData = venueMap?.toMapData(feed, venueAddress) ?: MapData(
        venueAddress = venueAddress,
        floors = emptyList(),
        rooms = emptyMap(),
        defaultFloorIndex = 0,
        defaultOffsetX = 0f,
        defaultOffsetY = 0f,
        initialZoom = 1f,
        minZoom = 1f,
        maxZoom = 1f,
    ),
)
