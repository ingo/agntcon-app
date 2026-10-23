package org.jetbrains.kotlinconf

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
class Conference(
    val sessions: List<Session> = emptyList(),
    val speakers: List<Speaker> = emptyList(),
)

@Serializable
class Speaker(
    val id: SpeakerId,
    val name: String,
    val position: String,
    val description: String,
    val photoUrl: String,
    /** The speaker's company, when the event collects it (a Sessionize question). */
    val company: String = "",
    /** The company's website, when known. */
    val companyUrl: String? = null,
    /** LinkedIn, website, blog and other links the speaker published. */
    val links: List<SpeakerLink> = emptyList(),
)

@Serializable
class SpeakerLink(
    /** Sessionize's link type: LinkedIn, Twitter, Blog, Company_Website, Sessionize, Other, ... */
    val type: String,
    val title: String,
    val url: String,
) {
    val isLinkedIn: Boolean get() = type.equals(LINKEDIN, ignoreCase = true)

    companion object {
        const val LINKEDIN = "LinkedIn"
        const val COMPANY_WEBSITE = "Company_Website"
        const val SESSIONIZE = "Sessionize"
    }
}

/** The speaker's LinkedIn profile, or any other link worth keeping when there is none. */
val Speaker.contactLink: SpeakerLink?
    get() = links.firstOrNull { it.isLinkedIn }
        ?: links.firstOrNull { it.type != SpeakerLink.SESSIONIZE && it.type != SpeakerLink.COMPANY_WEBSITE }

@Serializable
class Session(
    val id: SessionId,
    val title: String,
    val description: String,
    val speakerIds: List<SpeakerId>,
    val location: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val tags: List<String>? = null,
    /** The session's recording, once published. */
    val videoUrl: String? = null,
    /** The speakers' slides (or poster), when uploaded to Sessionize. */
    val slidesUrl: String? = null,
)

@Serializable
@JvmInline
value class SpeakerId(val id: String)

@Serializable
@JvmInline
value class SessionId(val id: String)
