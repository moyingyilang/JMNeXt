package com.jmnext.data.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 内存版 KeyValueStore：只实现被测代码用到的两个方法。 */
private class FakeStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String, def: String?): String? = map[key] ?: def
    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
    override fun getBoolean(key: String, def: Boolean) = def
    override fun putBoolean(key: String, value: Boolean) = Unit
    override fun getInt(key: String, def: Int) = def
    override fun putInt(key: String, value: Int) = Unit
    override fun getLong(key: String, def: Long) = def
    override fun putLong(key: String, value: Long) = Unit
    override fun remove(key: String) { map.remove(key) }
    override fun clear() = map.clear()
    /** 测试用：直接塞坏数据。 */
    fun rawPut(key: String, value: String) { map[key] = value }
}

class LocalStoresTest {

    @Test
    fun historyKeepsOnlyLatestPerAlbum() {
        val s = HistoryStore(FakeStore())
        s.record("u1", HistoryEntry(aid = "1", chapterId = "c1", title = "A", updatedAt = 100))
        s.record("u1", HistoryEntry(aid = "1", chapterId = "c2", title = "A", updatedAt = 200))
        val all = s.all("u1")
        assertEquals(1, all.size, "同一本作品只应保留一条")
        assertEquals("c2", all[0].chapterId, "应保留最新那一话")
    }

    @Test
    fun historyIsNamespaceIsolated() {
        val kv = FakeStore()
        HistoryStore(kv).record("u1", HistoryEntry(aid = "1", updatedAt = 1))
        assertEquals(1, HistoryStore(kv).all("u1").size)
        assertEquals(0, HistoryStore(kv).all("u2").size, "换账号不应串数据")
    }

    @Test
    fun historyRespectsLimitAndDropsOldest() {
        val s = HistoryStore(FakeStore(), limit = 3)
        repeat(5) { i -> s.record("u1", HistoryEntry(aid = "$i", updatedAt = (i + 1) * 100L)) }
        val all = s.all("u1")
        assertEquals(3, all.size)
        assertEquals(listOf("4", "3", "2"), all.map { it.aid }, "应按时间倒序且淘汰最早的")
    }

    @Test
    fun corruptedJsonDegradesToEmpty() {
        val kv = FakeStore()
        kv.rawPut("history_local", "{not json")
        assertEquals(0, HistoryStore(kv).all("local").size, "坏数据应退化为空表而不是抛异常")
    }

    @Test
    fun favoriteToggleIsIdempotent() {
        val s = FavoriteStore(FakeStore())
        assertTrue(s.toggle("u1", FavoriteEntry(aid = "9", addedAt = 1)))
        assertTrue(s.has("u1", "9"))
        assertFalse(s.toggle("u1", FavoriteEntry(aid = "9", addedAt = 2)), "第二次应取消收藏")
        assertFalse(s.has("u1", "9"))
        assertEquals(0, s.all("u1").size)
    }

    @Test
    fun favoriteSortedAndLimited() {
        val s = FavoriteStore(FakeStore(), limit = 2)
        s.toggle("u1", FavoriteEntry(aid = "a", addedAt = 1))
        s.toggle("u1", FavoriteEntry(aid = "b", addedAt = 2))
        s.toggle("u1", FavoriteEntry(aid = "c", addedAt = 3))
        assertEquals(listOf("c", "b"), s.all("u1").map { it.aid })
    }
}
