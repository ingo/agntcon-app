package org.jetbrains.kotlinconf.personal

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.observable.makeObservable
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.storage.YearlyStorageImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Notes, who the attendee met and ratings: kept per event year in the device's settings. */
@OptIn(ExperimentalSettingsApi::class)
class PersonalStorageTest {
    private val settings: ObservableSettings = MapSettings().makeObservable()

    private fun storage(year: Int = 2026, settings: ObservableSettings = this.settings) =
        YearlyStorageImpl(year, settings)

    @Test
    fun nothingStoredYetReadsAsEmpty() = runTest {
        val storage = storage()

        assertEquals(PersonalNotes(), storage.getNotes().first())
        assertEquals(emptySet(), storage.getMetSpeakers().first())
        assertEquals(emptyMap(), storage.getRatings().first())
    }

    @Test
    fun notesOnSessionsAndSpeakersAreStoredSeparately() = runTest {
        val storage = storage()
        val notes = PersonalNotes()
            .withSessionNote(SessionId("s1"), "Ask about the auth flow")
            .withSpeakerNote(SpeakerId("p1"), "Met at the coffee stand.\nFollow up on MCP registry")
        storage.setNotes(notes)

        // A new storage over the same settings, as after an app restart
        val reloaded = storage().getNotes().first()
        assertEquals("Ask about the auth flow", reloaded.sessionNote(SessionId("s1")))
        assertEquals("Met at the coffee stand.\nFollow up on MCP registry", reloaded.speakerNote(SpeakerId("p1")))
        assertEquals("", reloaded.sessionNote(SessionId("p1")), "a speaker's id is not a session's")
        assertEquals("", reloaded.speakerNote(SpeakerId("s1")))
    }

    @Test
    fun blankNoteRemovesTheNote() {
        val notes = PersonalNotes().withSessionNote(SessionId("s1"), "  keep  ")
        assertEquals("keep", notes.sessionNote(SessionId("s1")))

        val cleared = notes.withSessionNote(SessionId("s1"), "  \n ")
        assertEquals(emptyMap(), cleared.sessions)
    }

    @Test
    fun metSpeakersCanBeMarkedAndUnmarked() = runTest {
        val storage = storage()
        storage.setMetSpeakers(emptySet<SpeakerId>().withMet(SpeakerId("a"), true).withMet(SpeakerId("b"), true))
        assertEquals(setOf(SpeakerId("a"), SpeakerId("b")), storage.getMetSpeakers().first())

        storage.setMetSpeakers(storage.getMetSpeakers().first().withMet(SpeakerId("a"), false))
        assertEquals(setOf(SpeakerId("b")), storage().getMetSpeakers().first())
    }

    @Test
    fun ratingsKeepThumbAndOneLineTakeaway() = runTest {
        val storage = storage()
        val ratings = emptyMap<String, SessionRating>()
            .withRating(SessionId("s1"), SessionRating(Thumb.UP, " Use OAuth resource indicators \nsecond line ignored"))
            .withRating(SessionId("s2"), SessionRating(Thumb.DOWN))
            .withRating(SessionId("s3"), SessionRating(null, "No thumb, only words"))
        storage.setRatings(ratings)

        val reloaded = storage().getRatings().first()
        assertEquals(SessionRating(Thumb.UP, "Use OAuth resource indicators"), reloaded["s1"])
        assertEquals(SessionRating(Thumb.DOWN, ""), reloaded["s2"])
        assertEquals(SessionRating(null, "No thumb, only words"), reloaded["s3"])
    }

    @Test
    fun aRatingWithNeitherThumbNorTakeawayIsRemoved() {
        val rated = emptyMap<String, SessionRating>().withRating(SessionId("s1"), SessionRating(Thumb.UP))

        assertEquals(emptyMap(), rated.withRating(SessionId("s1"), SessionRating(null, "  ")))
        assertTrue(SessionRating().isEmpty)
    }

    @Test
    fun eachEventYearKeepsItsOwnData() = runTest {
        storage(2025).setNotes(PersonalNotes().withSessionNote(SessionId("s1"), "last year"))
        storage(2026).setNotes(PersonalNotes().withSessionNote(SessionId("s1"), "this year"))

        assertEquals("last year", storage(2025).getNotes().first().sessionNote(SessionId("s1")))
        assertEquals("this year", storage(2026).getNotes().first().sessionNote(SessionId("s1")))
    }

    @Test
    fun unreadableStoredDataReadsAsEmptyInsteadOfCrashing() = runTest {
        settings["2026_notes"] = "{not json"
        settings["2026_metSpeakers"] = "42"
        settings["2026_ratings"] = "[1,2]"
        val storage = storage()

        assertEquals(PersonalNotes(), storage.getNotes().first())
        assertEquals(emptySet(), storage.getMetSpeakers().first())
        assertEquals(emptyMap(), storage.getRatings().first())
    }

    @Test
    fun storedDataWithFieldsFromAnotherVersionStillLoads() = runTest {
        settings["2026_ratings"] = """{"s1":{"thumb":"UP","takeaway":"x","stars":5}}"""
        settings["2026_notes"] = """{"sessions":{"s1":"n"},"speakers":{},"tags":[]}"""

        assertEquals(SessionRating(Thumb.UP, "x"), storage().getRatings().first()["s1"])
        assertEquals("n", storage().getNotes().first().sessionNote(SessionId("s1")))
    }
}
