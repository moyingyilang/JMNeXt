package com.jmnext.data.sync

import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.HistoryEntry
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 端到端同步测试：本地 WebDAV 服务 + 多台“设备”。
 *
 * 覆盖的是同步真正会出问题的地方，而不是 DTO 字段：
 * 1. 设备 A 上传后，空设备 B 能拉下来（首次同步不丢数据）；
 * 2. 两台设备各加一条，同步后并集（不互相覆盖）；
 * 3. **本机取消收藏不会传播到另一台**（刻意的保守策略，见 SyncMerge 注释）；
 * 4. 远端文件不存在时能自己建目录并首次上传（真实网盘常见 409/404 场景）；
 * 5. 屏蔽名单跨设备取并集。
 */
class SyncIntegrationTest {

    private lateinit var dav: LocalWebDavServer

    @BeforeTest
    fun setUp() {
        dav = LocalWebDavServer().start()
    }

    @AfterTest
    fun tearDown() {
        dav.stop()
    }

    @Test
    fun firstSyncFromEmptyDevicePullsRemote(): Unit = runBlocking {
        val a = TestDevice("A", dav.url)
        a.history.record("ns", HistoryEntry(aid = "100", title = "作品甲", updatedAt = 1_000))
        a.favorites.add("ns", FavoriteEntry(aid = "100", title = "作品甲", addedAt = 1_000))
        a.blocks.addTag("ntr")
        a.service().syncNow().getOrThrow()

        val b = TestDevice("B", dav.url)
        b.service().syncNow().getOrThrow()

        assertEquals(listOf("100"), b.history.all("ns").map { it.aid })
        assertEquals(listOf("100"), b.favorites.all("ns").map { it.aid })
        assertTrue(b.blocks.snapshot().tags.contains("ntr"))
    }

    @Test
    fun bothDevicesMergeWithoutOverwrite(): Unit = runBlocking {
        val a = TestDevice("A", dav.url)
        val b = TestDevice("B", dav.url)

        a.history.record("ns", HistoryEntry(aid = "1", title = "A 的历史", updatedAt = 100))
        a.service().syncNow().getOrThrow()

        b.history.record("ns", HistoryEntry(aid = "2", title = "B 的历史", updatedAt = 200))
        b.service().syncNow().getOrThrow()
        a.service().syncNow().getOrThrow()

        assertEquals(setOf("1", "2"), a.history.all("ns").map { it.aid }.toSet())
        assertEquals(setOf("1", "2"), b.history.all("ns").map { it.aid }.toSet())
    }

    @Test

    fun createsNestedDirectoriesOnFirstUpload(): Unit = runBlocking {
        val a = TestDevice("A", dav.url)
        a.settings.save(
            a.settings.load().copy(path = "/deep/nested/dir/sync.json"),
        )
        a.history.record("ns", HistoryEntry(aid = "7", updatedAt = 1))
        val report = a.service().syncNow().getOrThrow()
        assertTrue(!report.remoteExisted, "首次上传时远端文件应不存在")
        assertEquals(1, report.historyCount)
    }

    @Test
    fun blockedMergeAcrossDevices(): Unit = runBlocking {
        val a = TestDevice("A", dav.url)
        a.blocks.addTag("guro")
        a.service().syncNow().getOrThrow()

        val b = TestDevice("B", dav.url)
        b.blocks.addWord("广告")
        b.service().syncNow().getOrThrow()

        a.service().syncNow().getOrThrow()

        assertTrue(a.blocks.snapshot().tags.contains("guro"))
        assertTrue(a.blocks.snapshot().words.contains("广告"), "A 应拿到 B 加的关键词")
    }

    @Test
    fun wrongPasswordFailsLoudly(): Unit = runBlocking {
        val bad = TestDevice("X", dav.url, pass = "wrong")
        bad.history.record("ns", HistoryEntry(aid = "1", updatedAt = 1))
        val r = bad.service().syncNow()
        assertTrue(r.isFailure, "认证失败必须报错，而不是静默成功")
        assertNotNull(r.exceptionOrNull())
    }
}

/** 墓碑相关：删除必须跨设备生效，且不能把“没同步过”误判成删除。 */
class SyncDeletionTest {

    private lateinit var dav: LocalWebDavServer

    @BeforeTest
    fun setUp() { dav = LocalWebDavServer().start() }

    @AfterTest
    fun tearDown() { dav.stop() }

    @Test
    fun removalPropagatesToOtherDevice(): Unit = runBlocking {
        val a = TestDevice("A", dav.url)
        a.favorites.add("ns", FavoriteEntry(aid = "42", title = "收藏", addedAt = 10))
        a.history.record("ns", HistoryEntry(aid = "42", updatedAt = 10))
        a.service().syncNow().getOrThrow()

        val b = TestDevice("B", dav.url)
        b.service().syncNow().getOrThrow()
        assertEquals(1, b.favorites.all("ns").size)

        // B 本机取消收藏并同步 → A 同步后也应删除
        b.favorites.removeAid("ns", "42")
        b.service().syncNow().getOrThrow()
        assertEquals(0, b.favorites.all("ns").size, "本机删除后不应被远端复活")

        a.service().syncNow().getOrThrow()
        assertEquals(0, a.favorites.all("ns").size, "删除应跨设备生效")
    }

    @Test
    fun reAddAfterDeleteRevives(): Unit = runBlocking {
        val a = TestDevice("A", dav.url)
        a.favorites.add("ns", FavoriteEntry(aid = "7", title = "x", addedAt = 10))
        a.service().syncNow().getOrThrow()

        a.favorites.removeAid("ns", "7")
        a.service().syncNow().getOrThrow()
        assertEquals(0, a.favorites.all("ns").size)

        // 删除之后再添加：时间更新，墓碑应失效
        Thread.sleep(5)
        a.favorites.add("ns", FavoriteEntry(aid = "7", title = "x", addedAt = System.currentTimeMillis()))
        a.service().syncNow().getOrThrow()
        assertEquals(1, a.favorites.all("ns").size, "删除后重新添加应当保留")
    }

    @Test
    fun firstSyncDoesNotTreatMissingAsDeletion(): Unit = runBlocking {
        // 远端已有数据，本机是空的且**没有任何基线**
        val a = TestDevice("A", dav.url)
        a.history.record("ns", HistoryEntry(aid = "1", updatedAt = 100))
        a.service().syncNow().getOrThrow()

        val fresh = TestDevice("Fresh", dav.url)
        fresh.service().syncNow().getOrThrow()
        assertEquals(1, fresh.history.all("ns").size, "空设备首次同步不能把远端数据当删除")
    }
}
