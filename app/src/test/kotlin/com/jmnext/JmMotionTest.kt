package com.jmnext

import androidx.compose.animation.core.LinearEasing
import com.jmnext.ui.theme.JmMotionSpec
import com.jmnext.ui.theme.MotionSpec
import com.jmnext.ui.theme.reducedMotion
import com.jmnext.ui.theme.toJmMotionTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动效 token 的三条硬约束，**对四套风格分别验证**（见 `docs/MOTION-EXPRESSIVE.md`）。
 *
 * 为什么按风格跑：token 是由风格的 fast/base/slow 派生的，某一套风格参数被改成
 * 离谱值（比如 base=800）时，只有逐套验证才能抓到；这也是"风格可以不同、但节奏必须都合理"的守门。
 */
class JmMotionTest {

    /** 与 `ThemeStyle` 里四套风格一致的 fast/base/slow（新增风格时这里要跟着加）。 */
    private val styles = listOf(
        "material" to MotionSpec(100, 200, 300, springy = false),
        "hyperos" to MotionSpec(150, 240, 360, springy = true),
        "miuix" to MotionSpec(100, 200, 300, springy = false),
        "plasma" to MotionSpec(120, 220, 320, springy = false),
    )

    @Test
    fun durationsStayInPerceptualWindowPerStyle() {
        for ((styleName, m) in styles) {
            for (s in m.toJmMotionTable().all) {
                if (s.name == "loading") continue   // 唯一允许超长的（循环骨架）
                val where = "[$styleName/${s.name}]"
                assertTrue("$where ${s.durationMillis}ms 低于 90ms（会像跳变）", s.durationMillis >= 90)
                assertTrue("$where ${s.durationMillis}ms 高于 600ms（会显迟钝）", s.durationMillis <= 600)
            }
        }
    }

    @Test
    fun exitIsNeverSlowerThanEnterPerStyle() {
        for ((styleName, m) in styles) {
            val table = m.toJmMotionTable()
            for (s in table.all) {
                val paired = s.pair ?: continue
                val enter = table.all.firstOrNull { it.name == paired }
                assertTrue("[$styleName] ${s.name} 的配对 $paired 不存在", enter != null)
                assertTrue(
                    "[$styleName] ${s.name}(${s.durationMillis}ms) 比进入 $paired(${enter!!.durationMillis}ms) 还慢",
                    s.durationMillis <= enter.durationMillis,
                )
            }
        }
    }

    @Test
    fun linearIsOnlyForLoadingPerStyle() {
        for ((styleName, m) in styles) {
            for (s in m.toJmMotionTable().all) {
                if (s.easing === LinearEasing) {
                    assertEquals("[$styleName] 只有加载态允许线性，${s.name} 也用了", "loading", s.name)
                }
            }
        }
    }

    @Test
    fun namesAreUniquePerStyle() {
        for ((styleName, m) in styles) {
            val all = m.toJmMotionTable().all
            assertEquals("[$styleName] token 名称重复", all.size, all.map { it.name }.toSet().size)
        }
    }

    @Test
    fun styleIdentityIsPreserved() {
        // 不同风格派生出的 navForward 时长不应相同，否则风格被抹平
        val a = styles[0].second.toJmMotionTable().navForward.durationMillis
        val b = styles[1].second.toJmMotionTable().navForward.durationMillis
        assertTrue("material 与 hyperos 的转场时长被抹平了（$a vs $b）", a != b)
    }

    @Test
    fun reduceMotionCollapsesToShortFade() {
        val spec: JmMotionSpec = styles[0].second.toJmMotionTable().navForward
        val short = spec.reducedMotion()
        assertEquals(120, short.durationMillis)
        assertTrue("退化后仍应保留进/出方向语义", short.isEnter == spec.isEnter)
    }
}

/** 退化路径的补充断言：开启"减少动画"后，**任何** token 都必须是极短淡入。 */
class JmMotionReduceTest {

    private val spec = MotionSpec(150, 240, 360, springy = true)

    @Test
    fun everyTokenCollapsesWhenReduceMotionIsOn() {
        for (s in spec.toJmMotionTable(reduceMotion = true).all) {
            assertEquals("[${s.name}] 退化后应为 120ms", 120, s.durationMillis)
        }
    }

    @Test
    fun reduceMotionKeepsDirectionSemantics() {
        val table = spec.toJmMotionTable(reduceMotion = true)
        assertTrue(table.listEnter.isEnter)
        assertTrue(!table.listExit.isEnter)
    }
}
