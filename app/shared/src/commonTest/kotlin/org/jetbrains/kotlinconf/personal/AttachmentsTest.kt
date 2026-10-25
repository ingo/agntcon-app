package org.jetbrains.kotlinconf.personal

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.observable.makeObservable
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.storage.AssetStorage
import org.jetbrains.kotlinconf.storage.YearlyStorageImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Photos and videos on sessions: which ones, in what order, kept per event next to the notes. */
@OptIn(ExperimentalSettingsApi::class)
class AttachmentsTest {
    private val settings = MapSettings().makeObservable()
    private val noAssets = object : AssetStorage {
        override suspend fun read(key: String): String? = null
        override suspend fun write(key: String, content: String) {}
    }

    private fun photo(id: String, at: Long) = Attachment(id, MediaKind.PHOTO, "$id.jpg", addedAt = at)
    private fun video(id: String, at: Long) = Attachment(id, MediaKind.VIDEO, "$id.mov", thumbnail = "$id-still.jpg", addedAt = at)

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
    fun aVideoIsShownByItsStillAndAPhotoByItself() {
        assertEquals("v-still.jpg", video("v", 0).preview)
        assertEquals("p.jpg", photo("p", 0).preview)
        assertNull(Attachment("x", MediaKind.VIDEO, "x.mov").preview)
    }

    @Test
    fun attachmentsAreStoredPerEventYear() = runTest {
        val storage2026 = YearlyStorageImpl(2026, settings, noAssets)
        storage2026.setAttachments(emptyMap<String, List<Attachment>>().withAdded(SessionId("s1"), listOf(video("v", 5))))

        assertEquals(listOf(video("v", 5)), YearlyStorageImpl(2026, settings, noAssets).getAttachments().first()["s1"])
        assertEquals(emptyMap(), YearlyStorageImpl(2025, settings, noAssets).getAttachments().first())
    }

    @Test
    fun unreadableAttachmentsReadAsNone() = runTest {
        settings["2026_attachments"] = "{broken"

        assertEquals(emptyMap(), YearlyStorageImpl(2026, settings, noAssets).getAttachments().first())
    }
}
