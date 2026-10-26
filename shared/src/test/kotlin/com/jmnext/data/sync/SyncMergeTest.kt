package com.jmnext.data.sync

import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.HistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncMergeTest {

    private fun hist(aid: String, at: Long) = HistoryEntry(aid = aid, updatedAt = at)
    private fun fav(aid: String, at: Long) = FavoriteEntry(aid = aid, addedAt = at)

    @Test
    fun newerHistoryWins() {
        val r = mergeSyncPayloads(
            SyncPayload(history = listOf(hist("1", 100))),
            SyncPayload(history = listOf(hist("1", 200))),
            now = 1000,
        )
        assertEquals(1, r.payload.history.size)
        assertEquals(200, r.payload.history.first().updatedAt)
        assertEquals(1, r.historyConflicts)
    }

    @Test
    fun sameTimestampNoConflict() {
        val r = mergeSyncPayloads(
            SyncPayload(history = listOf(hist("1", 100))),
            SyncPayload(history = listOf(hist("1", 100))),
            now = 1000,
        )
        assertEquals(0, r.historyConflicts)
    }

    @Test
    fun unionOfBothSides() {
        val r = mergeSyncPayloads(
            SyncPayload(history = listOf(hist("a", 10)), favorites = listOf(fav("x", 10))),
            SyncPayload(history = listOf(hist("b", 20)), favorites = listOf(fav("y", 20))),
            now = 1000,
        )
        assertEquals(setOf("a", "b"), r.payload.history.map { it.aid }.toSet())
        assertEquals(setOf("x", "y"), r.payload.favorites.map { it.aid }.toSet())
    }

    @Test
    fun blockedIsUnion() {
        val r = mergeSyncPayloads(
            SyncPayload(blocked = BlockedPayload(tags = listOf("ntr"))),
            SyncPayload(blocked = BlockedPayload(tags = listOf("guro"), words = listOf("广告"))),
            now = 1000,
        )
        assertEquals(listOf("guro", "ntr"), r.payload.blocked.tags)
        assertEquals(listOf("广告"), r.payload.blocked.words)
    }

    @Test
    fun remoteNullKeepsLocal() {
        val r = mergeSyncPayloads(SyncPayload(history = listOf(hist("1", 5))), null, now = 777)
        assertEquals(777, r.payload.updatedAt)
        assertEquals(1, r.payload.history.size)
    }

    @Test
    fun limitTrimsOldest() {
        val local = SyncPayload(history = (1..10).map { hist("$it", it.toLong()) })
        val r = mergeSyncPayloads(local, null, limit = 3, now = 1000)
        assertEquals(3, r.payload.history.size)
        assertEquals("10", r.payload.history.first().aid)
    }

    @Test
    fun favoritesOrderedDesc() {
        val r = mergeSyncPayloads(
            SyncPayload(favorites = listOf(fav("a", 1), fav("b", 9))),
            SyncPayload(),
            now = 100,
        )
        assertEquals(listOf("b", "a"), r.payload.favorites.map { it.aid })
        assertEquals(100L, r.payload.updatedAt)
    }
}
