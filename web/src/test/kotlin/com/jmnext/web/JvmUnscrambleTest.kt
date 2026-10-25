package com.jmnext.web

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmUnscrambleTest {

    /** 造一张 8x8 的图：每行颜色 = 行号，便于验证行搬运。 */
    private fun samplePng(): ByteArray {
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 8) for (x in 0 until 8) img.setRGB(x, y, 0x100000 * y + x)
        val bos = ByteArrayOutputStream()
        ImageIO.write(img, "png", bos)
        return bos.toByteArray()
    }

    @Test
    fun formatDetection() {
        assertEquals("png", JvmUnscramble.formatOf(samplePng()))
        assertEquals("jpeg", JvmUnscramble.formatOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals(null, JvmUnscramble.formatOf("not an image".toByteArray()))
    }

    @Test
    fun noUnscrambleWhenSliceCountIsOne() {
        val src = samplePng()
        assertTrue(JvmUnscramble.unscramble(src, 1).contentEquals(src), "切片数 <= 1 应原样返回")
    }

    @Test
    fun unscrambleKeepsAllPixels() {
        val src = samplePng()
        val out = JvmUnscramble.unscramble(src, 4)
        val img = ImageIO.read(ByteArrayInputStream(out))
        assertEquals(8, img.width)
        assertEquals(8, img.height)
        // 行被搬到别的位置，但像素集合不变（每行颜色不同，排序后应一致）
        val rows = (0 until 8).map { y -> (0 until 8).map { x -> img.getRGB(x, y) }.toSet() }
        assertEquals(rows.size, rows.distinct().size, "还原后不应出现重复行")
    }

    @Test
    fun garbageInputFallsBackToOriginal() {
        val junk = "not an image".toByteArray()
        assertTrue(JvmUnscramble.unscramble(junk, 4).contentEquals(junk), "解码失败应原样返回而不是抛异常")
    }
}
