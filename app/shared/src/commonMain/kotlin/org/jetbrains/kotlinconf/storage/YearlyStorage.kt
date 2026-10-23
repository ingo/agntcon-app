package org.jetbrains.kotlinconf.storage

import org.jetbrains.kotlinconf.sync.SessionizeSyncState
import kotlinx.coroutines.flow.Flow
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.ConferenceInfo
import org.jetbrains.kotlinconf.NotificationSettings
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.personal.SessionRating

interface YearlyStorage {
    fun getConferenceCache(): Flow<Conference?>
    suspend fun setConferenceCache(value: Conference)

    fun getConferenceInfoCache(): Flow<ConferenceInfo?>
    suspend fun setConferenceInfoCache(value: ConferenceInfo)

    fun getFavorites(): Flow<Set<SessionId>>
    suspend fun setFavorites(value: Set<SessionId>)

    /** The attendee's private notes on sessions and speakers. */
    fun getNotes(): Flow<PersonalNotes>
    suspend fun setNotes(value: PersonalNotes)

    /** Speakers the attendee marked as met. */
    fun getMetSpeakers(): Flow<Set<SpeakerId>>
    suspend fun setMetSpeakers(value: Set<SpeakerId>)

    /** The attendee's private session ratings, by session id. These never leave the device. */
    fun getRatings(): Flow<Map<String, SessionRating>>
    suspend fun setRatings(value: Map<String, SessionRating>)

    fun getNotificationSettings(): Flow<NotificationSettings?>
    suspend fun setNotificationSettings(value: NotificationSettings)

    fun getSessionizeSync(): Flow<SessionizeSyncState?>
    suspend fun setSessionizeSync(value: SessionizeSyncState?)

}
