package com.jmnext

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动效审计：禁止在 UI 代码里**随手写死**时长或弹簧参数。
 *
 * 为什么需要这条：项目里曾散落 tween(200)、tween(300)、spring(0.45f, 900f) 这类字面量，
 * 导致同一个动作在不同页面时长不同 —— 用户能感到"哪里不对"却说不出。token 化之后，
 * 这类写法必须只出现在**允许的地方**（token 定义、物理弹簧、风格开关），否则测试红。
 *
 * 白名单里每条都要写明理由；新增条目必须同样给出理由，不能"因为报错就加进来"。
 */
class MotionAuditTest {

    /** 允许出现字面量的文件（相对 app/src/main/kotlin/）。 */
    private val allowed = mapOf(
        "com/jmnext/ui/theme/JmMotion.kt" to "token 定义本身",
        "com/jmnext/ui/theme/ReduceMotion.kt" to "退化时的 120ms 常量",
        "com/jmnext/ui/JmNavHost.kt" to "reduce-motion 退化与 Tab spring（物理/降级分支）",
        "com/jmnext/ui/components/FloatingBottomBar.kt" to "拖拽回弹的物理弹簧",
        "com/jmnext/ui/components/Glass.kt" to "springy 风格的材质弹簧",
    )

    private val hardcoded = Regex("""(tween|spring)[^(]*\(\s*(durationMillis\s*=\s*)?[0-9]""")

    @Test
    fun noAdHocDurationsOutsideTokenLayer() {
        val root = File("src/main/kotlin")
        assertTrue("找不到源码目录：${root.absolutePath}", root.isDirectory)
        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (allowed.containsKey(rel)) return@forEach
            f.readLines().forEachIndexed { i, line ->
                val t = line.trimStart()
                val isComment = t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                if (!isComment && hardcoded.containsMatchIn(line)) {
                    offenders += rel + ":" + (i + 1) + "  " + line.trim()
                }
            }
        }
        assertTrue(
            "以下位置写死了动效参数，应改用 JmMotion token（或登记白名单并写明理由）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
