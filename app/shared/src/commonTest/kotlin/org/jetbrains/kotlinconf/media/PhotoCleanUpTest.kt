package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the trip report says while the photos are cleaned up in the background. */
class PhotoCleanUpTest {
    private fun photo(id: String, version: Int) = Attachment(id, MediaKind.PHOTO, "$id.jpg", cleanChecked = true, cleanVersion = version)
    private fun video(id: String) = Attachment(id, MediaKind.VIDEO, "$id.mov")
    private val current = { a: Attachment -> a.cleanVersion < SCREEN_CLEAN_UP_VERSION }

    @Test
    fun photosCleanedByAnOlderVersionAreStillToDo() {
        val progress = cleanUpProgress(
            listOf(photo("a", SCREEN_CLEAN_UP_VERSION), photo("b", SCREEN_CLEAN_UP_VERSION - 1), photo("c", 0)),
            current,
        )
        assertEquals(PhotoCleanUp(done = 1, total = 3), progress)
        assertTrue(progress!!.working)
    }

    @Test
    fun whenEveryPhotoIsDoneNothingIsWorking() {
        val progress = cleanUpProgress(listOf(photo("a", SCREEN_CLEAN_UP_VERSION), photo("b", SCREEN_CLEAN_UP_VERSION)), current)
        assertEquals(PhotoCleanUp(done = 2, total = 2), progress)
        assertFalse(progress!!.working)
    }

    @Test
    fun videosAreNotCountedAndNoPhotoMeansNothingToShow() {
        assertEquals(PhotoCleanUp(1, 1), cleanUpProgress(listOf(video("v"), photo("a", SCREEN_CLEAN_UP_VERSION)), current))
        assertNull(cleanUpProgress(listOf(video("v")), current))
        assertNull(cleanUpProgress(emptyList(), current))
    }
}
