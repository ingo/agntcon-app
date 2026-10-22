package org.jetbrains.kotlinconf.event

import org.jetbrains.kotlinconf.sessionize.RoomData
import org.jetbrains.kotlinconf.sessionize.SessionizeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VenueMapTest {
    @Test
    fun feedRoomsAreMatchedWithoutTheirLevel() {
        val feed = SessionizeData(
            rooms = listOf(
                RoomData(name = "210C (Concourse Level)", id = 1),
                RoomData(name = "LL20A (Lower Level)", id = 2),
                RoomData(name = "231", id = 3),
                RoomData(name = "Grand Ballroom (Concourse Level)", id = 4),
                RoomData(name = "Solutions Showcase (Concourse Level)", id = 5),
            ),
        )
        val map = SanJoseMcEneryConventionCenter.toMapData(feed, venueAddress = null)

        assertEquals(setOf("210C (Concourse Level)", "LL20A (Lower Level)", "231", "Grand Ballroom (Concourse Level)"), map.rooms.keys)
        assertEquals(0, map.rooms.getValue("210C (Concourse Level)").floorIndex)
        assertEquals(1, map.rooms.getValue("LL20A (Lower Level)").floorIndex)
        assertTrue(map.floors.all { it.svgPathLight.startsWith(BUNDLED_ASSET_PREFIX) })
    }

    @Test
    fun everyRoomIsOnAFloorAndInsideTheImage() {
        val venue = SanJoseMcEneryConventionCenter
        for ((name, room) in venue.rooms) {
            assertTrue(room.floorIndex in venue.floors.indices, name)
            assertTrue(room.x in 0f..1f && room.y in 0f..1f, name)
        }
    }

    @Test
    fun readsThePngSize() {
        val header = byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10,
            0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
            0, 0, 6, 0x9F.toByte(), 0, 0, 4, 0x88.toByte(), 8, 6,
        )
        assertEquals(1695 to 1160, pngSize(header))
    }

    @Test
    fun rasterMapsAreRecognisedAndPlainSvgsAreNot() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
        val wrapped = RASTER_MAP_MARKER + """<svg><image xlink:href="data:image/png;base64,${kotlin.io.encoding.Base64.encode(png)}"/></svg>"""

        assertEquals(png.toList(), rasterMapPng(wrapped)?.toList())
        assertEquals(null, rasterMapPng("""<svg><image href="data:image/png;base64,AAAA"/></svg>"""))
    }
}
