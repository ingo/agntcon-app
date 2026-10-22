package org.jetbrains.kotlinconf.event

import org.jetbrains.kotlinconf.FloorData
import org.jetbrains.kotlinconf.MapData
import org.jetbrains.kotlinconf.RoomData
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.screens.roomName
import org.jetbrains.kotlinconf.sessionize.SessionizeData
import kotlin.io.encoding.Base64

/**
 * A venue floor plan shipped with the app, for conferences without a backend to serve one.
 *
 * Each floor is a PNG under `composeResources/files`. Rooms are keyed by their name without the level
 * ("210C", "LL20A", "Grand Ballroom") and placed by their center as a fraction of the floor image's width
 * and height, so they match the feed's room names whatever level suffix the organizers use.
 */
class VenueMap(
    val floors: List<VenueFloor>,
    val rooms: Map<String, VenueRoom>,
    val defaultFloorIndex: Int = 0,
)

class VenueFloor(val name: String, val shortName: String, val image: String)

class VenueRoom(val floorIndex: Int, val x: Float, val y: Float)

/** Asset paths with this prefix are read from the app's resources rather than downloaded. */
const val BUNDLED_ASSET_PREFIX = "bundled:"

/** The map for the feed's rooms: each feed room ("210C (Concourse Level)") that the venue map places. */
fun VenueMap.toMapData(feed: SessionizeData, venueAddress: String?): MapData = MapData(
    venueAddress = venueAddress,
    floors = floors.map { FloorData(it.name, it.shortName, BUNDLED_ASSET_PREFIX + it.image, BUNDLED_ASSET_PREFIX + it.image) },
    rooms = feed.rooms.mapNotNull { room ->
        val place = rooms[room.name] ?: rooms[roomName(room.name)] ?: return@mapNotNull null
        room.name to RoomData(place.floorIndex, place.x, place.y)
    }.toMap(),
    defaultFloorIndex = defaultFloorIndex,
    defaultOffsetX = 0.5f,
    defaultOffsetY = 0.5f,
    initialZoom = 0.6f,
    minZoom = 0.5f,
    maxZoom = 2.5f,
)

/** A bundled PNG floor plan wrapped in an SVG, so the map draws it like the backend's SVG floors. */
suspend fun bundledMapSvg(path: String): String {
    val png = Res.readBytes(path.removePrefix(BUNDLED_ASSET_PREFIX))
    val (width, height) = pngSize(png)
    return RASTER_MAP_MARKER + """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" """ +
        """width="$width" height="$height" viewBox="0 0 $width $height">""" +
        """<image width="$width" height="$height" xlink:href="data:image/png;base64,${Base64.encode(png)}"/></svg>"""
}

/**
 * Starts a [bundledMapSvg] document. Renderers that cannot draw an SVG's embedded image (Skia) read the PNG
 * with [rasterMapPng] and draw it themselves.
 */
const val RASTER_MAP_MARKER = "<!--raster-map-->"

/** The PNG inside a [bundledMapSvg] document, or null for any other SVG. */
fun rasterMapPng(svg: String): ByteArray? {
    if (!svg.startsWith(RASTER_MAP_MARKER)) return null
    val start = svg.indexOf("base64,").takeIf { it >= 0 }?.plus("base64,".length) ?: return null
    val end = svg.indexOf('"', start).takeIf { it > start } ?: return null
    return Base64.decode(svg.substring(start, end))
}

/** Width and height from a PNG's header. */
internal fun pngSize(png: ByteArray): Pair<Int, Int> {
    require(png.size > 24 && png[1] == 'P'.code.toByte() && png[2] == 'N'.code.toByte()) { "Not a PNG" }
    fun int(at: Int) = (0..3).fold(0) { acc, i -> (acc shl 8) or (png[at + i].toInt() and 0xFF) }
    return int(16) to int(20)
}

/** San Jose McEnery Convention Center, from Team San Jose's floor plan (sanjose.org). */
internal val SanJoseMcEneryConventionCenter = VenueMap(
    floors = listOf(
        VenueFloor("Concourse level", "Concourse", "files/maps/sjmcc-concourse.png"),
        VenueFloor("Street & lower level", "Street", "files/maps/sjmcc-street.png"),
    ),
    rooms = mapOf(
        "210A" to VenueRoom(0, 0.2796f, 0.4345f),
        "210B" to VenueRoom(0, 0.2796f, 0.3759f),
        "210C" to VenueRoom(0, 0.2796f, 0.3190f),
        "230A" to VenueRoom(0, 0.8696f, 0.3414f),
        "230C" to VenueRoom(0, 0.8696f, 0.2569f),
        "231" to VenueRoom(0, 0.8531f, 0.3914f),
        "Grand Ballroom" to VenueRoom(0, 0.6077f, 0.7672f),
        "LL20A" to VenueRoom(1, 0.5298f, 0.5607f),
        "LL20C" to VenueRoom(1, 0.5298f, 0.6785f),
        "LL21A" to VenueRoom(1, 0.6041f, 0.5308f),
        "LL21D" to VenueRoom(1, 0.6041f, 0.6729f),
        "The Hub" to VenueRoom(1, 0.3634f, 0.4075f),
    ),
)
