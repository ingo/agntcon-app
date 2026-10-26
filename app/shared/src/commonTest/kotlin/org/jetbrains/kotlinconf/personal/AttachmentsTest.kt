package org.jetbrains.kotlinconf.personal

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.observable.makeObservable
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.storage.YearlyStorageImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Photos and videos on sessions: which ones, in what order, kept per event next to the notes. */
@OptIn(ExperimentalSettingsApi::class)
class AttachmentsTest {
    private val settings = MapSettings().makeObservable()

    private fun photo(id: String, at: Long) = Attachment(id, MediaKind.PHOTO, "$id.jpg", addedAt = at)
    private fun video(id: String, at: Long) = Attachment(id, MediaKind.VIDEO, "$id.mov", thumbnail = "$id-still.jpg", addedAt = at)

    @Test
    fun choosingAnotherCleanUpMakesThePhotoBeCleanedUpAgain() {
        val done = photo("a", 1).copy(cleanChecked = true, cleaned = "a-clean.jpg")

        val dark = done.withCleanUpMode(CleanUpMode.DARK)
        assertEquals(CleanUpMode.DARK, dark.cleanMode)
        assertEquals(false, dark.cleanChecked)
        // The old copy stays until the new one is ready
        assertEquals("a-clean.jpg", dark.cleaned)

        // The same choice again changes nothing, and going back to automatic is a choice too
        assertSame(dark, dark.withCleanUpMode(CleanUpMode.DARK))
        assertNull(dark.copy(cleanChecked = true).withCleanUpMode(null).cleanMode)
        assertEquals(false, dark.copy(cleanChecked = true).withCleanUpMode(null).cleanChecked)
    }

    @Test
    fun aPhotoStoredByAnOlderVersionHasNoCleanUpChoiceAndNoTextYet() {
        assertNull(photo("a", 1).cleanMode)
        assertNull(photo("a", 1).text)
        assertEquals(false, photo("a", 1).textChecked)
    }

    @Test
    fun attachmentsKeepTheOrderTheyWereAddedIn() {
        val s1 = SessionId("s1")
        val all = emptyMap<String, List<Attachment>>()
            .withAdded(s1, listOf(photo("b", 20)))
            .withAdded(s1, listOf(photo("a", 10), video("c", 30)))

        assertEquals(listOf("a", "b", "c"), all.getValue("s1").map { it.id })
    }

    @Test
    fun addingTheSameAttachmentTwiceKeepsOne() {
        val s1 = SessionId("s1")
        val all = emptyMap<String, List<Attachment>>()
            .withAdded(s1, listOf(photo("a", 10)))
            .withAdded(s1, listOf(photo("a", 10)))

        assertEquals(1, all.getValue("s1").size)
    }

    @Test
    fun removingTheLastAttachmentRemovesTheSession() {
        val s1 = SessionId("s1")
        val all = emptyMap<String, List<Attachment>>().withAdded(s1, listOf(photo("a", 10), photo("b", 20)))

        assertEquals(listOf("b"), all.withRemoved(s1, "a").getValue("s1").map { it.id })
        assertEquals(emptyMap(), all.withRemoved(s1, "a").withRemoved(s1, "b"))
    }

    @Test
    fun aPhotoOnTheWrongTalkMovesToTheRightOneInTheOrderItWasAdded() {
        val s1 = SessionId("s1")
        val s2 = SessionId("s2")
        val all = emptyMap<String, List<Attachment>>()
            .withAdded(s1, listOf(photo("a", 10), photo("b", 20)))
            .withAdded(s2, listOf(photo("c", 5), photo("d", 30)))

        val moved = all.withMoved(s1, s2, "b")

        assertEquals(listOf("a"), moved.getValue("s1").map { it.id })
        assertEquals(listOf("c", "b", "d"), moved.getValue("s2").map { it.id })
        // Its files go with it unchanged
        assertEquals(photo("b", 20), moved.getValue("s2")[1])
    }

    @Test
    fun movingTheLastPhotoAwayRemovesTheSessionAndAMoveToAnEmptySessionAddsIt() {
        val s1 = SessionId("s1")
        val s2 = SessionId("s2")
        val all = emptyMap<String, List<Attachment>>().withAdded(s1, listOf(photo("a", 10)))

        assertEquals(mapOf("s2" to listOf(photo("a", 10))), all.withMoved(s1, s2, "a"))
    }

    @Test
    fun movingToTheSameSessionOrAnUnknownPhotoChangesNothing() {
        val s1 = SessionId("s1")
        val all = emptyMap<String, List<Attachment>>().withAdded(s1, listOf(photo("a", 10)))

        assertEquals(all, all.withMoved(s1, s1, "a"))
        assertEquals(all, all.withMoved(s1, SessionId("s2"), "x"))
        assertEquals(all, all.withMoved(SessionId("s3"), s1, "a"))
    }

    @Test
    fun aVideoIsShownByItsStillAndAPhotoByItself() {
        assertEquals("v-still.jpg", video("v", 0).preview)
        assertEquals("p.jpg", photo("p", 0).preview)
        assertNull(Attachment("x", MediaKind.VIDEO, "x.mov").preview)
    }

    @Test
    fun attachmentsAreStoredPerEventYear() = runTest {
        val storage2026 = YearlyStorageImpl(2026, settings)
        storage2026.setAttachments(emptyMap<String, List<Attachment>>().withAdded(SessionId("s1"), listOf(video("v", 5))))

        assertEquals(listOf(video("v", 5)), YearlyStorageImpl(2026, settings).getAttachments().first()["s1"])
        assertEquals(emptyMap(), YearlyStorageImpl(2025, settings).getAttachments().first())
    }

    @Test
    fun unreadableAttachmentsReadAsNone() = runTest {
        settings["2026_attachments"] = "{broken"

        assertEquals(emptyMap(), YearlyStorageImpl(2026, settings).getAttachments().first())
    }


    @Test
    fun attachmentsStoredBeforeHighlightsStillLoad() {
        // As an older version wrote it: no highlighted, no takenAt. The storage decodes with these settings.
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = """{"s1":[{"id":"a","kind":"PHOTO","file":"a.jpg","addedAt":5,"cleaned":"a-c.jpg","cleanChecked":true,"cleanVersion":2}]}"""

        val loaded = json.decodeFromString<Map<String, List<Attachment>>>(old).getValue("s1").single()

        assertEquals("a-c.jpg", loaded.cleaned)
        assertEquals(false, loaded.highlighted)
        assertNull(loaded.takenAt)
        assertEquals(5L, loaded.orderTime)
    }

    @Test
    fun highlightingIsPerPhotoAndKeepsTheRest() {
        val all = mapOf("s1" to listOf(photo("a", 1), photo("b", 2)))

        val starred = all.withUpdated("b") { it.withHighlighted(true) }

        assertEquals(listOf(false, true), starred.getValue("s1").map { it.highlighted })
        val b = starred.getValue("s1")[1]
        assertSame(b, b.withHighlighted(true))
        assertEquals(false, b.withHighlighted(false).highlighted)
        assertEquals(b.copy(highlighted = false), b.withHighlighted(false))
    }

    @Test
    fun theTimeATakenPhotoWasShotWinsOverWhenItWasAdded() {
        assertEquals(100L, photo("a", 500).copy(takenAt = 100).orderTime)
        assertEquals(500L, photo("a", 500).orderTime)
    }

    @Test
    fun starsFromThePhotoLibraryOnlyAddStars() {
        val hearted = photo("a", 1).copy(sourceId = "lib-a")
        val alreadyStarred = photo("b", 2).copy(sourceId = "lib-b", highlighted = true)
        val ownStar = photo("c", 3).copy(sourceId = "lib-c", highlighted = true)
        val notHearted = photo("d", 4).copy(sourceId = "lib-d")
        val noSource = photo("e", 5)
        val aVideo = video("f", 6).copy(sourceId = "lib-f")
        val all = mapOf("s1" to listOf(hearted, alreadyStarred, ownStar, notHearted, noSource, aVideo))

        val (next, starred) = all.withStarsFrom(setOf("lib-a", "lib-b", "lib-f"))

        // One new star; the star set in the app on c stays although c is not hearted; videos are never starred
        assertEquals(1, starred)
        assertEquals(listOf(true, true, true, false, false, false), next.getValue("s1").map { it.highlighted })
        assertEquals(0, next.withStarsFrom(setOf("lib-a")).second)
    }
}
