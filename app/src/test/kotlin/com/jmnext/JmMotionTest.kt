package com.jmnext

import androidx.compose.animation.core.LinearEasing
import com.jmnext.ui.theme.JmMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动效 token 的三条硬约束（见 `docs/MOTION-EXPRESSIVE.md`）。
 *
 * 这些断言的作用不是"测试有没有写错"，而是**防止以后随手加一个 800ms 的慢动画**把整体节奏带偏：
 * 参数一旦越界，测试立刻红，而不是等用户觉得"越用越迟钝"。
 */
class JmMotionTest {

    private val all = JmMotion.all

    @Test
    fun durationsStayInPerceptualWindow() {
        for (s in all) {
            assertTrue("${s.name} 时长 ${s.durationMillis}ms 低于 90ms（会显得像跳变）", s.durationMillis >= 90)
            assertTrue("${s.name} 时长 ${s.durationMillis}ms 高于 600ms（会显得迟钝）", s.durationMillis <= 600 || s.name == "loading")
        }
    }

    @Test
    fun exitIsNeverSlowerThanEnter() {
        for (s in all) {
            val paired = s.pair ?: continue
            val enter = all.firstOrNull { it.name == paired }
            assertTrue("${s.name} 的配对 $paired 不存在", enter != null)
            assertTrue(
                "${s.name}(${s.durationMillis}ms) 比进入 $paired(${enter!!.durationMillis}ms) 还慢",
                s.durationMillis <= enter.durationMillis,
            )
        }
    }

    @Test
    fun linearIsOnlyForLoading() {
        for (s in all) {
            if (s.easing === LinearEasing) {
                assertEquals("只有加载态允许线性曲线，但 ${s.name} 也用了", "loading", s.name)
            }
        }
    }

    @Test
    fun namesAreUnique() {
        assertEquals("token 名称重复：$all", all.size, all.map { it.name }.toSet().size)
    }

    @Test
    fun reduceMotionCollapsesToShortFade() {
        val short = JmMotion.reduceMotion(JmMotion.navForward)
        assertEquals(120, short.durationMillis)
        assertTrue("退化后仍应保留进出方向语义", short.isEnter == JmMotion.navForward.isEnter)
    }
}
