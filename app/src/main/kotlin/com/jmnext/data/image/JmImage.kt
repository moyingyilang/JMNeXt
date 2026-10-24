package com.jmnext.data.image

import android.graphics.Bitmap
import com.jmnext.data.crypto.JmCrypto

/**
 * 漫画图片的切片还原（Android 侧薄壳）。
 *
 * 服务端会把部分漫画的整页纵向切成若干条并**上下错位重排**，客户端拿到的是乱序版本。
 * 这里等价移植 `JMComic_SRC` 中 `utils/Function.js` 的 `onImageLoaded`：
 * 逐条把源图上的条带搬到目标图的对应位置。
 *
 * 几何关系（与源码逐字对应，变量名保持一致以便日后对账）：
 *
 *   num       = md5(aid + page) 推出的份数，见 [JmCrypto.sliceCount]
 *   remainder = h % num
 *   第 i 条：源区间 y = h - floor(h/num)*(i+1) - remainder
 *            目标起点 py = floor(h/num)*i
 *   其中 i == 0 时把 remainder 补进条高（而非目标位置）——
 *   这样所有条带拼起来正好铺满整页，不重不漏。
 *
 * **为什么要按条带流式搬，而不是"整页取出来再整页写回去"**（2.2.1）：
 * 一页 1131×1600 的 ARGB 是 7.2MB。旧写法同时存在三份这么大的缓冲
 *（源图取出的像素数组、还原后的数组、目标位图），叠加预取后很容易 OOM
 *（用户实测到 `java.lang.OutOfMemoryError`）。现在只保留**目标位图 + 一条带**的缓冲，
 * 峰值从约 21.6MB 降到约 7.8MB。几何仍完全来自跨平台的 [ImageUnscramble.bands]，
 * 因此与桌面端同一份算法、不会分叉。
 */
object JmImage {

    /**
     * @param src 从服务端下载到的乱序图
     * @param page 页码字符串，必须是接口原样给出的值（参与 md5 计算）
     * @return 还原后的新图；不需要还原或参数不合法时**原样返回 [src]**
     */
    fun unscramble(src: Bitmap, aid: Int, page: String): Bitmap {
        val w = src.width
        val h = src.height
        val bands = ImageUnscramble.bands(w, h, JmCrypto.sliceCount(aid, page))
        if (bands.isEmpty()) return src

        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maxRows = bands.maxOf { it.height }
        // 一条带的缓冲就够：逐条搬完即可，不需要整页数组
        val rowBuf = IntArray(w * maxRows)
        for (b in bands) {
            src.getPixels(rowBuf, 0, w, 0, b.srcY, w, b.height)
            dst.setPixels(rowBuf, 0, w, 0, b.dstY, w, b.height)
        }
        return dst
    }
}
