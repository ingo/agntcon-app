package org.jetbrains.kotlinconf.storage

import org.jetbrains.kotlinconf.sync.SessionizeSyncState
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import com.russhwolf.settings.set
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.ConferenceInfo
import org.jetbrains.kotlinconf.NotificationSettings
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.personal.SessionRating
import org.jetbrains.kotlinconf.di.Year
import org.jetbrains.kotlinconf.di.YearScope


@ContributesBinding(YearScope::class)
@SingleIn(YearScope::class)
@OptIn(ExperimentalSettingsApi::class)
class YearlyStorageImpl(
    @Year private val year: Int,
    private val settings: ObservableSettings,
) : YearlyStorage {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private inline fun <reified T> String?.decodeOrNull(): T? {
        if (this == null) return null
        return try {
            json.decodeFromString<T>(this)
        } catch (_: SerializationException) {
            null
        }
    }

    private fun key(name: String): String = "${year}_$name"

    val CONFERENCE_CACHE = key("conferenceCache")
    val CONFERENCE_INFO_CACHE = key("conferenceInfoCache")
    val FAVORITES = key("favorites")
    val NOTIFICATION_SETTINGS = key("notificationSettings")
    val SESSIONIZE_SYNC = key("sessionizeSync")
    val NOTES = key("notes")
    val MET_SPEAKERS = key("metSpeakers")
    val RATINGS = key("ratings")

    override fun getConferenceCache(): Flow<Conference?> = settings.getStringOrNullFlow(CONFERENCE_CACHE).map { it.decodeOrNull<Conference>() }
    override suspend fun setConferenceCache(value: Conference) = settings.set(CONFERENCE_CACHE, json.encodeToString(value))

    override fun getConferenceInfoCache(): Flow<ConferenceInfo?> = settings.getStringOrNullFlow(CONFERENCE_INFO_CACHE).map { it.decodeOrNull<ConferenceInfo>() }
    override suspend fun setConferenceInfoCache(value: ConferenceInfo) = settings.set(CONFERENCE_INFO_CACHE, json.encodeToString(value))

    override fun getFavorites(): Flow<Set<SessionId>> = settings.getStringOrNullFlow(FAVORITES).map { it.decodeOrNull<Set<SessionId>>() ?: emptySet() }
    override suspend fun setFavorites(value: Set<SessionId>) = settings.set(FAVORITES, json.encodeToString(value))

    override fun getNotes(): Flow<PersonalNotes> = settings.getStringOrNullFlow(NOTES).map { it.decodeOrNull<PersonalNotes>() ?: PersonalNotes() }
    override suspend fun setNotes(value: PersonalNotes) = settings.set(NOTES, json.encodeToString(value))

    override fun getMetSpeakers(): Flow<Set<SpeakerId>> = settings.getStringOrNullFlow(MET_SPEAKERS).map { it.decodeOrNull<Set<SpeakerId>>() ?: emptySet() }
    override suspend fun setMetSpeakers(value: Set<SpeakerId>) = settings.set(MET_SPEAKERS, json.encodeToString(value))

    override fun getRatings(): Flow<Map<String, SessionRating>> = settings.getStringOrNullFlow(RATINGS).map { it.decodeOrNull<Map<String, SessionRating>>() ?: emptyMap() }
    override suspend fun setRatings(value: Map<String, SessionRating>) = settings.set(RATINGS, json.encodeToString(value))

    override fun getNotificationSettings(): Flow<NotificationSettings?> = settings.getStringOrNullFlow(NOTIFICATION_SETTINGS).map { it.decodeOrNull<NotificationSettings>() }
    override suspend fun setNotificationSettings(value: NotificationSettings) = settings.set(NOTIFICATION_SETTINGS, json.encodeToString(value))

    override fun getSessionizeSync(): Flow<SessionizeSyncState?> = settings.getStringOrNullFlow(SESSIONIZE_SYNC).map { it.decodeOrNull<SessionizeSyncState>() }
    override suspend fun setSessionizeSync(value: SessionizeSyncState?) {
        if (value == null) settings.remove(SESSIONIZE_SYNC) else settings.set(SESSIONIZE_SYNC, json.encodeToString(value))
    }

}
