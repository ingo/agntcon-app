package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.personal.Attachment

/** A picked photo or video with the fingerprints used to recognise it. */
data class Fingerprint(val sourceId: String?, val hash: String?)

/**
 * Recognises photos and videos that are already attached to a session, or picked twice in one go. The same item has
 * the same library id ([Fingerprint.sourceId]) every time it is picked; a file added some other way (the camera, an
 * older version of the app) is recognised by its content ([Fingerprint.hash]).
 */
class MediaDuplicates(existing: Collection<Attachment>) {
    private val seenIds = existing.mapNotNullTo(mutableSetOf()) { it.sourceId }
    private val seenHashes = existing.mapNotNullTo(mutableSetOf()) { it.contentHash }

    /** True if [fingerprint] was seen before; otherwise it is remembered, so a second copy in the same batch counts. */
    fun isDuplicate(fingerprint: Fingerprint): Boolean {
        val byId = fingerprint.sourceId != null && fingerprint.sourceId in seenIds
        val byHash = fingerprint.hash != null && fingerprint.hash in seenHashes
        if (byId || byHash) return true
        fingerprint.sourceId?.let { seenIds += it }
        fingerprint.hash?.let { seenHashes += it }
        return false
    }
}

/** The attachments left after [removeStoredDuplicates], and the ones taken out, whose files can be deleted. */
data class StoredDuplicates(val kept: Map<String, List<Attachment>>, val removed: List<Attachment>)

/**
 * Takes out photos and videos stored more than once, in one session or in different ones (older versions of the app did
 * not check). Attachments without a [Attachment.contentHash] get one from [hashes] (by attachment id) where it is known.
 * Of each set of copies, the one added first stays; it is the one a session lists first. Running it again changes
 * nothing.
 */
fun removeStoredDuplicates(
    all: Map<String, List<Attachment>>,
    hashes: Map<String, String> = emptyMap(),
): StoredDuplicates {
    val backfilled = all.mapValues { (_, list) ->
        list.map { if (it.contentHash == null && it.id in hashes) it.copy(contentHash = hashes[it.id]) else it }
    }
    val seen = MediaDuplicates(emptyList())
    val removedIds = backfilled.values.flatten()
        .sortedBy { it.addedAt }
        .filter { seen.isDuplicate(Fingerprint(it.sourceId, it.contentHash)) }
        .mapTo(mutableSetOf()) { it.id }
    val kept = backfilled.mapValues { (_, list) -> list.filterNot { it.id in removedIds } }.filterValues { it.isNotEmpty() }
    val removed = all.values.flatten().filter { it.id in removedIds }
    return StoredDuplicates(kept, removed)
}
