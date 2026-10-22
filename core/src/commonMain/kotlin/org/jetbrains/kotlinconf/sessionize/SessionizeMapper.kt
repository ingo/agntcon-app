package org.jetbrains.kotlinconf.sessionize

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.TagInfo
import kotlin.time.Instant

/**
 * Event-specific rules applied when turning a Sessionize feed into a [Conference].
 * The defaults apply no special treatment; [KOTLINCONF] reproduces the original KotlinConf behaviour.
 */
@Serializable
data class SessionizeRules(
    /** Sessions carrying any of these tags are dropped. */
    val excludedTags: Set<String> = emptySet(),
    /**
     * Sessions with this tag that are split into "… Part 1", "… Part 2" in the same room
     * are merged into one session spanning all parts. Null disables merging.
     */
    val multipartWorkshopTag: String? = null,
    /** Suffixes removed from room names (e.g. " (Lightning talks)"). */
    val roomSuffixesToStrip: List<String> = emptyList(),
    /**
     * Sessionize category group titles used for the schedule filters. Groups not listed are
     * assigned by title: one containing "level" becomes [TagInfo.levels], one containing
     * "format" or "type" becomes [TagInfo.formats], the rest become [TagInfo.categories].
     */
    val levelGroups: List<String> = emptyList(),
    val formatGroups: List<String> = emptyList(),
    val categoryGroups: List<String> = emptyList(),
) {
    companion object {
        val KOTLINCONF = SessionizeRules(
            excludedTags = setOf("Interview"),
            multipartWorkshopTag = "Workshop",
            roomSuffixesToStrip = listOf(" (Lightning talks)"),
        )
    }
}

val SessionizeJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun parseSessionizeData(json: String): SessionizeData = SessionizeJson.decodeFromString(json)

/**
 * Parses a Sessionize timestamp. UTC or offset timestamps are converted into [timeZone];
 * timestamps without an offset are already event-local and are taken as they are.
 */
fun parseSessionizeTime(value: String, timeZone: TimeZone): LocalDateTime {
    val hasOffset = value.endsWith("Z") || value.substringAfter('T').let { '+' in it || '-' in it }
    return if (hasOffset) {
        Instant.parse(value).toLocalDateTime(timeZone)
    } else {
        LocalDateTime.parse(value)
    }
}

fun SessionizeData.toConference(
    timeZone: TimeZone,
    rules: SessionizeRules = SessionizeRules(),
): Conference {
    val tagNames: Map<Int, String> = categories
        .flatMap { it.items }
        .associate { it.id to it.name }
    val roomNames: Map<Int, String> = rooms.associate { it.id to it.name }

    val sessions = sessions.mapNotNull { session ->
        val startsAt = session.startsAt ?: return@mapNotNull null
        val endsAt = session.endsAt ?: return@mapNotNull null
        val tags: List<String> = session.categoryItems.mapNotNull { tagNames[it] }

        if (tags.any { it in rules.excludedTags }) return@mapNotNull null

        val room = session.roomId?.let { roomNames[it] } ?: "unknown"
        Session(
            id = session.id,
            title = session.displayTitle,
            description = session.descriptionText ?: "",
            speakerIds = session.speakers,
            location = rules.roomSuffixesToStrip.fold(room) { name, suffix -> name.removeSuffix(suffix) },
            startsAt = parseSessionizeTime(startsAt, timeZone),
            endsAt = parseSessionizeTime(endsAt, timeZone),
            tags = tags,
            videoUrl = session.recordingUrl,
        )
    }.let { list ->
        rules.multipartWorkshopTag?.let { list.mergeWorkshops(it) } ?: list
    }

    val speakers = speakers.map {
        Speaker(
            id = it.id,
            name = it.fullName,
            position = it.tagLine ?: "",
            description = it.bio ?: "",
            photoUrl = it.profilePicture ?: "",
        )
    }

    return Conference(sessions, speakers)
}

/**
 * Builds the schedule filter groups from the Sessionize categories, keeping Sessionize's sort order.
 */
fun SessionizeData.toTagInfo(rules: SessionizeRules = SessionizeRules()): TagInfo {
    val levels = mutableListOf<String>()
    val formats = mutableListOf<String>()
    val topics = mutableListOf<String>()

    for (group in categories.sortedBy { it.sort }) {
        val title = group.title
        val lower = title.lowercase()
        val target = when {
            title in rules.levelGroups -> levels
            title in rules.formatGroups -> formats
            title in rules.categoryGroups -> topics
            rules.categoryGroups.isNotEmpty() -> null
            "level" in lower -> levels
            "format" in lower || "type" in lower -> formats
            else -> topics
        } ?: continue
        group.items.sortedBy { it.sort }
            .map { it.name }
            .filterNotTo(target) { it in rules.excludedTags }
    }

    return TagInfo(categories = topics.distinct(), levels = levels.distinct(), formats = formats.distinct())
}

private fun List<Session>.mergeWorkshops(workshopTag: String): List<Session> {
    val (workshopParts, nonWorkshop) = partition { it.tags?.contains(workshopTag) == true }

    val workshops = workshopParts.groupBy { it.location }.values.flatMap { parts ->
        // Workshops not split into parts are kept as they are
        val first = parts.find { it.title.contains("Part 1") } ?: return@flatMap parts
        val merged = Session(
            id = first.id,
            title = first.title.substringBefore(". Part").trim(),
            description = first.description,
            speakerIds = parts.flatMap { it.speakerIds }.distinct(),
            location = first.location,
            startsAt = parts.minOf { it.startsAt },
            endsAt = parts.maxOf { it.endsAt },
            tags = first.tags,
        )
        listOf(merged)
    }

    return nonWorkshop + workshops
}
