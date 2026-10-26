package com.jmnext.data.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoSyncTest {

    @Test
    fun needsEnabledAndUsable() {
        assertFalse(AutoSync.shouldRun(enabled = false, usable = true, lastAt = 0, now = 1_000_000))
        assertFalse(AutoSync.shouldRun(enabled = true, usable = false, lastAt = 0, now = 1_000_000))
        assertTrue(AutoSync.shouldRun(enabled = true, usable = true, lastAt = 0, now = 1_000_000))
    }

    @Test
    fun throttlesWithinInterval() {
        val now = 10_000_000L
        assertFalse(AutoSync.shouldRun(true, true, lastAt = now - 60_000, now = now))
        assertTrue(AutoSync.shouldRun(true, true, lastAt = now - AutoSync.MIN_INTERVAL_MS - 1, now = now))
    }
}
