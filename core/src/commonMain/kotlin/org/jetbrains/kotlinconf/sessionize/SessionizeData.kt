package org.jetbrains.kotlinconf.sessionize

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId

/**
 * The Sessionize API v2 "All" view (`https://sessionize.com/api/v2/{id}/view/All`).
 *
 * Times are kept as strings: depending on the event's API settings Sessionize sends either
 * UTC (`2026-05-20T06:00:00Z`) or event-local time without an offset (`2026-10-22T09:00:00`).
 * [parseSessionizeTime] handles both.
 */
@Serializable
class SessionizeData(
    val sessions: List<SessionData> = emptyList(),
    val rooms: List<RoomData> = emptyList(),
    val speakers: List<SpeakerData> = emptyList(),
    val questions: List<QuestionData> = emptyList(),
    val categories: List<CategoryData> = emptyList(),
)

@Serializable
data class SessionData(
    val id: SessionId,
    val isServiceSession: Boolean = false,
    val isPlenumSession: Boolean = false,
    val speakers: List<SpeakerId> = emptyList(),
    @SerialName("description")
    val descriptionText: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val title: String,
    val roomId: Int? = null,
    val questionAnswers: List<QuestionAnswerData> = emptyList(),
    val categoryItems: List<Int> = emptyList(),
    val liveUrl: String? = null,
    val recordingUrl: String? = null,
) {
    val displayTitle: String get() = title.trim()
}

@Serializable
class RoomData(
    val name: String,
    val id: Int,
    val sort: Int = 0,
)

@Serializable
class SpeakerData(
    val id: SpeakerId,
    val firstName: String = "",
    val lastName: String = "",
    val profilePicture: String? = null,
    val tagLine: String? = null,
    val isTopSpeaker: Boolean = false,
    val bio: String? = null,
    val fullName: String,
    val links: List<LinkData> = emptyList(),
    val categoryItems: List<Int> = emptyList(),
    val questionAnswers: List<QuestionAnswerData> = emptyList(),
)

@Serializable
class QuestionData(
    val question: String,
    val id: Int,
    val sort: Int = 0,
    val questionType: String = "",
)

@Serializable
class CategoryData(
    val id: Int,
    val sort: Int = 0,
    val title: String,
    val items: List<CategoryItemData> = emptyList(),
)

@Serializable
class QuestionAnswerData(
    val questionId: Int,
    val answerValue: String? = null,
)

@Serializable
class LinkData(
    val linkType: String = "",
    val title: String = "",
    val url: String,
)

@Serializable
class CategoryItemData(
    val name: String,
    val id: Int,
    val sort: Int = 0,
)
