package com.slskdandroid.notifications

import com.slskdandroid.core.model.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationWorkSchedulerTest {

    @Test
    fun `interval converts seconds to whole minutes`() {
        assertEquals(20L, NotificationWorkScheduler.workIntervalMinutes(1_200))
        assertEquals(60L, NotificationWorkScheduler.workIntervalMinutes(3_600))
    }

    @Test
    fun `interval never drops below WorkManager's periodic floor`() {
        // Values persisted by older versions can be far below the floor.
        assertEquals(15L, NotificationWorkScheduler.workIntervalMinutes(30))
        assertEquals(15L, NotificationWorkScheduler.workIntervalMinutes(300))
        assertEquals(15L, NotificationWorkScheduler.workIntervalMinutes(0))
    }

    @Test
    fun `the settings floor is exactly the WorkManager floor`() {
        assertEquals(15L, NotificationWorkScheduler.workIntervalMinutes(NotificationSettings.MIN_INTERVAL_SECONDS))
        assertEquals(15L * 60, NotificationSettings.MIN_INTERVAL_SECONDS.toLong())
    }
}
