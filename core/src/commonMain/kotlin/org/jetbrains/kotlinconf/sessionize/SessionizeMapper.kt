package org.jetbrains.kotlinconf.sessionize

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.SpeakerLink
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
    /**
     * The session question whose file uploads are the speakers' slides, e.g. "Presentation or Poster".
     * Any https upload to that question counts. Null picks file uploads that are PDFs on
     * `sessionize.com/download` from any question. Questions with "logo" in their name never count.
     */
    val slidesQuestion: String? = null,
    /** Speaker questions that hold the company, the job title and the company's website. */
    val companyQuestion: String = "Company",
    val titleQuestion: String = "Speaker Title",
    val companyUrlQuestion: String = "Company URL",
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
    val slidesQuestionIds: Set<Int> = slidesQuestionIds(rules)

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
            videoUrl = session.recordingUrl?.trim()?.takeIf { it.isNotEmpty() },
            slidesUrl = session.slidesUrl(slidesQuestionIds, rules),
        )
    }.let { list ->
        rules.multipartWorkshopTag?.let { list.mergeWorkshops(it) } ?: list
    }

    val speakers = speakers.map { it.toSpeaker(this, rules) }.withConsistentCompanies()

    return Conference(sessions, speakers)
}

private fun SpeakerData.toSpeaker(feed: SessionizeData, rules: SessionizeRules): Speaker {
    fun answer(question: String): String? = feed.questions
        .filter { it.question.trim().equals(question.trim(), ignoreCase = true) }
        .firstNotNullOfOrNull { q -> questionAnswers.firstOrNull { it.questionId == q.id }?.answerValue }
        ?.trim()?.takeIf { it.isNotEmpty() }

    val company = answer(rules.companyQuestion) ?: ""
    val jobTitle = answer(rules.titleQuestion)
    val links = links.mapNotNull { link ->
        val url = link.url.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) null
        else SpeakerLink(type = link.linkType, title = link.title.trim().ifEmpty { link.linkType.replace('_', ' ') }, url = url)
    }
    val companyUrl = answer(rules.companyUrlQuestion)?.takeIf { it.isWebsite() }
        ?: links.firstOrNull { it.type == SpeakerLink.COMPANY_WEBSITE }?.url

    return Speaker(
        id = id,
        name = fullName,
        position = displayPosition(tagLine, jobTitle, company),
        description = bio ?: "",
        photoUrl = profilePicture ?: "",
        company = company,
        companyUrl = companyUrl,
        links = links,
    )
}

/** "Vstorm" and "VStorm" are one company: every spelling becomes the most common one (the first on a tie). */
internal fun List<Speaker>.withConsistentCompanies(): List<Speaker> {
    val spelling: Map<String, String> = filter { it.company.isNotEmpty() }
        .groupBy { it.company.lowercase() }
        .mapValues { (_, speakers) ->
            speakers.groupingBy { it.company }.eachCount().entries.maxByOrNull { it.value }!!.key
        }
    if (spelling.isEmpty()) return this
    return map { speaker ->
        val company = spelling[speaker.company.lowercase()] ?: return@map speaker
        if (company == speaker.company) speaker else Speaker(
            id = speaker.id,
            name = speaker.name,
            position = speaker.position,
            description = speaker.description,
            photoUrl = speaker.photoUrl,
            company = company,
            companyUrl = speaker.companyUrl,
            links = speaker.links,
        )
    }
}

/**
 * The line shown under a speaker's name: their tag line, or job title and company when there is none.
 * A company the tag line leaves out is added, so what is shown is also what a search finds.
 */
internal fun displayPosition(tagLine: String?, jobTitle: String?, company: String): String {
    // Events that collect no company or job title show the tag line exactly as Sessionize has it
    if (company.isEmpty() && jobTitle == null) return tagLine ?: ""
    val line = tagLine?.trim().orEmpty().ifEmpty { jobTitle.orEmpty() }
    return when {
        company.isEmpty() -> line
        line.isEmpty() -> company
        line.contains(company, ignoreCase = true) -> line
        else -> "$line, $company"
    }
}

/** A web page, not a logo or other image (some speakers put their logo in the website question). */
private fun String.isWebsite(): Boolean =
    (startsWith("https://") || startsWith("http://")) &&
        listOf(".svg", ".png", ".jpg", ".jpeg", ".gif", ".webp").none { substringBefore('?').endsWith(it, ignoreCase = true) }

private fun SessionizeData.slidesQuestionIds(rules: SessionizeRules): Set<Int> = questions
    .filter { it.questionType == FILE_UPLOAD && "logo" !in it.question.lowercase() }
    .filter { rules.slidesQuestion == null || it.question.trim().equals(rules.slidesQuestion.trim(), ignoreCase = true) }
    .mapTo(mutableSetOf()) { it.id }

private fun SessionData.slidesUrl(questionIds: Set<Int>, rules: SessionizeRules): String? = questionAnswers
    .filter { it.questionId in questionIds }
    .mapNotNull { it.answerValue?.trim() }
    .firstOrNull { url ->
        if (rules.slidesQuestion != null) url.startsWith("https://") else url.isSessionizePdf()
    }

private fun String.isSessionizePdf(): Boolean =
    startsWith(SESSIONIZE_DOWNLOADS) && endsWith(".pdf", ignoreCase = true)

private const val FILE_UPLOAD = "File_upload"
private const val SESSIONIZE_DOWNLOADS = "https://sessionize.com/download/"

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
            videoUrl = parts.firstNotNullOfOrNull { it.videoUrl },
            slidesUrl = parts.firstNotNullOfOrNull { it.slidesUrl },
        )
        listOf(merged)
    }

    return nonWorkshop + workshops
}
