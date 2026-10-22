package org.jetbrains.kotlinconf.sync

import org.jetbrains.kotlinconf.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionizeSyncStateTest {
    private val start = SessionizeSyncState(deviceId = "d", linked = true)

    @Test
    fun changesAreRecordedLikeTheWebApp() {
        val state = start
            .withChange(SessionId("1"), bookmarked = true, now = 10)
            .withChange(SessionId("2"), bookmarked = false, now = 20)

        assertEquals(setOf("1"), state.toAdd)
        assertEquals(setOf("2"), state.toRemove)
        assertEquals(20, state.changedAt)
    }

    @Test
    fun undoingAChangeBeforeSyncCancelsIt() {
        val added = start.withChange(SessionId("1"), bookmarked = true, now = 10)
        val undone = added.withChange(SessionId("1"), bookmarked = false, now = 20)
        assertEquals(emptySet(), undone.toAdd)
        assertEquals(emptySet(), undone.toRemove)

        val removed = start.withChange(SessionId("2"), bookmarked = false, now = 10)
        val readded = removed.withChange(SessionId("2"), bookmarked = true, now = 20)
        assertEquals(emptySet(), readded.toAdd)
        assertEquals(emptySet(), readded.toRemove)
    }

    @Test
    fun changesMadeDuringASyncSurviveTheServerList() {
        val pending = start.copy(toAdd = setOf("9"), toRemove = setOf("2"))
        assertEquals(
            setOf("1", "3", "9").map(::SessionId).toSet(),
            mergeAfterSync(serverItems = setOf("1", "2", "3"), pending = pending),
        )
    }
}
