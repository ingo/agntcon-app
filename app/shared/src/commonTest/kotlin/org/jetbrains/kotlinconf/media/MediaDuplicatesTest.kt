package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A photo added a second time, from the library or as the same file, is left out. */
class MediaDuplicatesTest {
    private val existing = listOf(
        Attachment("a", MediaKind.PHOTO, "a.jpg", sourceId = "LIB-1", contentHash = "h1"),
        // Added by an older version: no library id, only the content
        Attachment("b", MediaKind.PHOTO, "b.jpg", contentHash = "h2"),
        Attachment("c", MediaKind.VIDEO, "c.mov"),
    )

    @Test
    fun theSameLibraryItemIsADuplicateEvenIfItsBytesDiffer() {
        assertTrue(MediaDuplicates(existing).isDuplicate(Fingerprint("LIB-1", "re-encoded")))
    }

    @Test
    fun theSameFileIsADuplicateWithoutALibraryId() {
        // A camera photo, or one picked before ids were kept
        assertTrue(MediaDuplicates(existing).isDuplicate(Fingerprint(null, "h2")))
        assertTrue(MediaDuplicates(existing).isDuplicate(Fingerprint("LIB-9", "h2")))
    }

    @Test
    fun aNewItemIsKept() {
        assertFalse(MediaDuplicates(existing).isDuplicate(Fingerprint("LIB-2", "h3")))
        assertFalse(MediaDuplicates(existing).isDuplicate(Fingerprint(null, null)))
    }

    @Test
    fun pickingTheSameItemTwiceInOneGoKeepsOne() {
        val seen = MediaDuplicates(existing)
        assertFalse(seen.isDuplicate(Fingerprint("LIB-5", "h5")))
        assertTrue(seen.isDuplicate(Fingerprint("LIB-5", "h5")))
        assertTrue(seen.isDuplicate(Fingerprint(null, "h5")))
    }
}
