package org.jetbrains.kotlinconf.event

import org.jetbrains.kotlinconf.sessionize.parseSessionizeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventProfilesTest {
    @Test
    fun profilesAreUniqueAndUsable() {
        val ids = EventProfiles.all.map { it.id }
        assertEquals(ids.distinct(), ids)

        for (event in EventProfiles.all) {
            event.timeZone // throws for an unknown zone id
            assertTrue(event.hasBackend || event.sessionizeUrl != null, "${event.id} has no data source")
        }
    }

    @Test
    fun conferenceInfoForSessionizeEvents() {
        val feed = parseSessionizeData(
            """
            {"categories": [
              {"id": 1, "title": "Topic", "items": [{"id": 10, "name": "MCPCon"}]},
              {"id": 2, "title": "Audience Level", "items": [{"id": 20, "name": "Beginner"}]}
            ]}
            """.trimIndent()
        )

        val info = EventProfiles.AgntconMcpconNa2026.toConferenceInfo(feed)

        assertEquals("AGNTCon + MCPCon North America 2026", info.aboutHeader)
        assertEquals(listOf("MCPCon"), info.tags.categories)
        assertEquals(listOf("Beginner"), info.tags.levels)
        assertTrue(info.partners.isEmpty())
        assertTrue(info.mapData.floors.isEmpty())
        assertNotNull(info.mapData.venueAddress)
    }
}
