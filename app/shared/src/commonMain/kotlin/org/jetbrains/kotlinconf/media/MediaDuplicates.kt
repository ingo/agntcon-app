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
