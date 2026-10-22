package org.jetbrains.kotlinconf.personal

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId

/**
 * What the attendee keeps for themself: notes, who they met and how a session was. All of it stays
 * on the device, per event (see [org.jetbrains.kotlinconf.storage.YearlyStorage]). Keys are the
 * Sessionize ids as plain strings so the stored JSON stays simple.
 */
@Serializable
data class PersonalNotes(
    val sessions: Map<String, String> = emptyMap(),
    val speakers: Map<String, String> = emptyMap(),
) {
    fun sessionNote(id: SessionId): String = sessions[id.id].orEmpty()
    fun speakerNote(id: SpeakerId): String = speakers[id.id].orEmpty()

    fun withSessionNote(id: SessionId, text: String) = copy(sessions = sessions.with(id.id, text))
    fun withSpeakerNote(id: SpeakerId, text: String) = copy(speakers = speakers.with(id.id, text))

    private fun Map<String, String>.with(key: String, text: String): Map<String, String> =
        if (text.isBlank()) this - key else this + (key to text.trim())
}

@Serializable
enum class Thumb { UP, DOWN }

/** A private rating: a thumb and an optional one-line takeaway. */
@Serializable
data class SessionRating(
    val thumb: Thumb? = null,
    val takeaway: String = "",
) {
    val isEmpty: Boolean get() = thumb == null && takeaway.isBlank()
}

/** Sets the rating of a session; a rating with neither a thumb nor a takeaway is removed. */
fun Map<String, SessionRating>.withRating(id: SessionId, rating: SessionRating): Map<String, SessionRating> {
    val clean = rating.copy(takeaway = rating.takeaway.trim().lineSequence().firstOrNull().orEmpty().trim())
    return if (clean.isEmpty) this - id.id else this + (id.id to clean)
}

/** Marks a speaker as met, or not. */
fun Set<SpeakerId>.withMet(id: SpeakerId, met: Boolean): Set<SpeakerId> = if (met) this + id else this - id
