package com.jmnext.web

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageCacheTest {

    @Test
    fun putThenGet() {
        val dir = Files.createTempDirectory("imgcache").toFile()
        val c = ImageCache(dir)
        assertNull(c.get("a"))
        c.put("a", byteArrayOf(1, 2, 3))
        assertTrue(c.get("a")!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(1, c.size())
    }

    @Test
    fun keysAreSanitized() {
        val dir = Files.createTempDirectory("imgcache").toFile()
        val c = ImageCache(dir)
        c.put("../../etc/passwd", byteArrayOf(9))
        // 不应写到 dir 之外
        assertTrue(dir.listFiles()!!.all { it.isFile && it.parentFile == dir })
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun trimsToMaxFiles() {
        val dir = Files.createTempDirectory("imgcache").toFile()
        val c = ImageCache(dir, maxFiles = 3)
        repeat(6) { i ->
            c.put("k$i", byteArrayOf(i.toByte()))
            Thread.sleep(5) // 让 mtime 有区分度
        }
        assertEquals(3, c.size())
        assertNull(c.get("k0"), "最旧的应被淘汰")
        assertTrue(c.get("k5") != null, "最新的应保留")
    }
}
