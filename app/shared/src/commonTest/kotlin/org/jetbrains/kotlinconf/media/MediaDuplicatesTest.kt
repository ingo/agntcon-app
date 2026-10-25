package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
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

/** Copies stored twice by older versions of the app are taken out once, keeping the one added first. */
class StoredDuplicatesTest {
    private fun photo(id: String, addedAt: Long, sourceId: String? = null, hash: String? = null) = Attachment(
        id, MediaKind.PHOTO, "$id.jpg", thumbnail = "$id-thumb.jpg", addedAt = addedAt,
        sourceId = sourceId, contentHash = hash, cleaned = "$id-clean.jpg",
    )

    @Test
    fun theSameLibraryItemStoredTwiceKeepsTheFirst() {
        val all = mapOf("s1" to listOf(photo("a", 1, "LIB-1", "h1"), photo("b", 2, "LIB-1", "re-encoded")))
        val result = removeStoredDuplicates(all)
        assertEquals(listOf("a"), result.kept.getValue("s1").map { it.id })
        assertEquals(listOf("b"), result.removed.map { it.id })
    }

    @Test
    fun theSameFileStoredTwiceKeepsTheFirst() {
        val all = mapOf("s1" to listOf(photo("b", 2, hash = "h1"), photo("a", 1, hash = "h1"), photo("c", 3, hash = "h2")))
        val result = removeStoredDuplicates(all)
        assertEquals(listOf("a", "c"), result.kept.getValue("s1").map { it.id })
        assertEquals(listOf("b"), result.removed.map { it.id })
    }

    @Test
    fun photosWithoutAFingerprintAreComparedByTheirNewHash() {
        val all = mapOf("s1" to listOf(photo("a", 1), photo("b", 2), photo("c", 3)))
        val result = removeStoredDuplicates(all, hashes = mapOf("a" to "h1", "b" to "h1", "c" to "h2"))
        assertEquals(listOf("a", "c"), result.kept.getValue("s1").map { it.id })
        // The hash is kept, so the next check need not read the file again
        assertEquals(listOf("h1", "h2"), result.kept.getValue("s1").map { it.contentHash })
        assertEquals(listOf("b"), result.removed.map { it.id })
    }

    @Test
    fun photosWithoutAnyFingerprintAreLeftAlone() {
        val all = mapOf("s1" to listOf(photo("a", 1), photo("b", 2)))
        val result = removeStoredDuplicates(all)
        assertEquals(all, result.kept)
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun copiesInDifferentSessionsAreRemovedAndAnEmptySessionGoesToo() {
        val all = mapOf(
            "s1" to listOf(photo("a", 1, "LIB-1", "h1")),
            "s2" to listOf(photo("b", 5, "LIB-1", "h1")),
            "s3" to listOf(photo("c", 6, hash = "h1"), photo("d", 7, "LIB-4", "h4")),
        )
        val result = removeStoredDuplicates(all)
        assertEquals(setOf("s1", "s3"), result.kept.keys)
        assertEquals(listOf("d"), result.kept.getValue("s3").map { it.id })
        assertEquals(listOf("b", "c"), result.removed.map { it.id })
    }

    @Test
    fun theFilesOfARemovedCopyAreAllListedForDeletion() {
        val all = mapOf("s1" to listOf(photo("a", 1, "LIB-1"), photo("b", 2, "LIB-1")))
        val removed = removeStoredDuplicates(all).removed.single()
        assertEquals(setOf("b.jpg", "b-clean.jpg", "b-thumb.jpg"), removed.files.toSet())
    }

    @Test
    fun runningItAgainChangesNothing() {
        val all = mapOf(
            "s1" to listOf(photo("a", 1), photo("b", 2, "LIB-1"), photo("c", 3, "LIB-1")),
            "s2" to listOf(photo("d", 4)),
        )
        val once = removeStoredDuplicates(all, hashes = mapOf("a" to "h1", "d" to "h1"))
        val twice = removeStoredDuplicates(once.kept)
        assertEquals(once.kept, twice.kept)
        assertTrue(twice.removed.isEmpty())
    }
}
