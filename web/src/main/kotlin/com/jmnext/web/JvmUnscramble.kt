package com.jmnext.web

import com.jmnext.data.image.ImageUnscramble
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * 服务端反切片（JVM）。
 *
 * 为什么不复用 app 的那份：`app/data/image/JmImage.kt` 走 Android `Bitmap`，`shared` 里没有图像库，
 * 桌面与 Web 侧要用 `java.awt` / `ImageIO`。**几何算法只有一份** —— 仍取自
 * `ImageUnscramble.bands`（纯函数，跨平台），这里只做"按条带搬运像素"。
 *
 * 与 2.1.9 的 OOM 修复保持同一取舍：逐条带 `getRGB`/`setRGB`，不整页二次分配。
 */
object JvmUnscramble {

    /** 识别格式；识别不出返回 null（调用方据此决定要不要透传原始字节）。 */
    fun formatOf(bytes: ByteArray): String? = when {
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpeg"
        bytes.size > 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> "webp"
        else -> null
    }

    /**
     * 还原一张切片图。
     *
     * @param sliceCount 切片数（来自 `JmCrypto.sliceCount(aid, page)`）；`<= 1` 时原样返回
     * @return 还原后的 PNG 字节；解码失败或无需还原时返回原字节
     */
    fun unscramble(source: ByteArray, sliceCount: Int): ByteArray {
        if (sliceCount <= 1) return source
        val src = runCatching { ImageIO.read(ByteArrayInputStream(source)) }.getOrNull() ?: return source
        if (src.width <= 0 || src.height <= 0) return source
        val bands = ImageUnscramble.bands(src.width, src.height, sliceCount)
        if (bands.isEmpty()) return source

        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        for (b in bands) {
            // 逐条带搬运：一次取一行宽度的像素，避免整页数组
            val row = IntArray(src.width * b.height)
            src.getRGB(0, b.srcY, src.width, b.height, row, 0, src.width)
            out.setRGB(0, b.dstY, src.width, b.height, row, 0, src.width)
        }
        val bos = ByteArrayOutputStream(source.size)
        ImageIO.write(out, "png", bos)
        return bos.toByteArray()
    }
}
