package org.jetbrains.kotlinconf.media

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * When a photo or video was taken. Either an exact moment ([instant], from a video's creation date or a photo
 * with a time zone offset) or only the camera's clock reading ([local], a photo without an offset), which is
 * taken to be the time where the event happened: phones set their clock to where they are.
 */
data class MediaTime(val instant: Instant? = null, val local: LocalDateTime? = null) {
    /** The time in the event's time zone, to compare with the schedule. */
    fun inZone(timeZone: TimeZone): LocalDateTime? = instant?.toLocalDateTime(timeZone) ?: local

    companion object {
        fun of(instant: Instant): MediaTime = MediaTime(instant = instant)

        /**
         * A photo's EXIF `DateTimeOriginal` ("2026:10:22 14:22:33") with its optional `OffsetTimeOriginal`
         * ("-07:00"). Null when the date is missing or unreadable.
         */
        fun fromExif(dateTime: String?, offset: String?): MediaTime? {
            val local = parseExifDateTime(dateTime ?: return null) ?: return null
            val utcOffset = offset?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { UtcOffset.parse(it) }.getOrNull() }
            return if (utcOffset != null) MediaTime(instant = local.toInstant(utcOffset)) else MediaTime(local = local)
        }

        /** Android's video date ("20261022T142233.000Z"), always UTC. */
        fun fromAndroidVideoDate(value: String?): MediaTime? {
            val match = Regex("""^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})""").find(value?.trim() ?: return null) ?: return null
            val (y, mo, d, h, mi, s) = match.destructured
            val local = runCatching { LocalDateTime(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt()) }.getOrNull()
                ?: return null
            // A camera without a clock reports 1904 or 1970; that is no date at all
            if (local.year < 2000) return null
            return MediaTime(instant = local.toInstant(UtcOffset.ZERO))
        }

        private fun parseExifDateTime(value: String): LocalDateTime? {
            val match = Regex("""^(\d{4})[:\-](\d{2})[:\-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})""").find(value.trim()) ?: return null
            val (y, mo, d, h, mi, s) = match.destructured
            if (y.toInt() < 2000) return null
            return runCatching { LocalDateTime(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt()) }.getOrNull()
        }
    }
}
