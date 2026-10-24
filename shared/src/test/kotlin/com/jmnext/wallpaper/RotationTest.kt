package com.jmnext.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轮换决策单测。
 *
 * 这些用例锁定的是用户实际遇到过的两种症状：
 * 1. 连点两次「换一张」看到同一张 → [avoidsRepeatingCurrentImage]；
 * 2. 池子走完后不补货、于是永远只有那几张 → [marksWrapWhenCycleEnds]。
 */
class RotationTest {

    private val pool = listOf("a", "b", "c")

    @Test
    fun advancesThroughPoolInOrder() {
        assertEquals(0, rotationStep(0, pool, null).index)
        assertEquals(1, rotationStep(1, pool, null).index)
        assertEquals(2, rotationStep(2, pool, null).index)
    }

    @Test
    fun avoidsRepeatingCurrentImage() {
        // 游标指到 a，但当前正显示 a → 应后移到 b
        assertEquals(1, rotationStep(0, pool, "a").index)
        // 单张池子没法避免（也不该崩）
        assertEquals(0, rotationStep(0, listOf("only"), "only").index)
    }

    @Test
    fun marksWrapWhenCycleEnds() {
        assertFalse("取第 1 张不应触发补货", rotationStep(0, pool, null).wrap)
        assertTrue("取到最后一张时应触发补货", rotationStep(2, pool, null).wrap)
        assertEquals(0, rotationStep(2, pool, null).nextCursor)
    }

    @Test
    fun survivesLargeAndNegativeCursor() {
        assertEquals(1, rotationStep(1_000_003, pool, null).index)   // 1000003 % 3 == 1
        assertEquals(2, rotationStep(-1, pool, null).index)          // 负数不应崩，按模回正
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPoolIsRejected() {
        rotationStep(0, emptyList(), null)
    }
}
