package com.slskdandroid.core.data

import com.slskdandroid.core.datastore.MessageWatermarkDataSource
import com.slskdandroid.core.model.MessageWatermarks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real [MessageWatermarkDataSource] over an in-memory DataStore — the persistence is
 * the whole point of the class, since each background poll may run in a fresh process.
 */
class DefaultMessageWatermarkStoreTest {

    private val store = DefaultMessageWatermarkStore(MessageWatermarkDataSource(FakePreferencesDataStore()))

    @Test
    fun `starts un-baselined and empty`() = runTest {
        val loaded = store.load()

        assertFalse(loaded.baselined)
        assertEquals(0L, loaded.baselineFloorMs)
        assertTrue(loaded.directMessages.isEmpty())
        assertTrue(loaded.rooms.isEmpty())
    }

    @Test
    fun `round-trips the baseline and per-conversation watermarks`() = runTest {
        store.save(
            MessageWatermarks(
                baselined = true,
                baselineFloorMs = 1_700L,
                directMessages = mapOf("peer" to 1_800L, "other peer" to 1_900L),
                rooms = mapOf("nicotine" to 2_000L),
            ),
        )

        val loaded = store.load()

        assertTrue(loaded.baselined)
        assertEquals(1_700L, loaded.baselineFloorMs)
        assertEquals(mapOf("peer" to 1_800L, "other peer" to 1_900L), loaded.directMessages)
        assertEquals(mapOf("nicotine" to 2_000L), loaded.rooms)
    }

    @Test
    fun `saving drops conversations that are no longer present`() = runTest {
        store.save(MessageWatermarks(baselined = true, directMessages = mapOf("gone" to 1L, "stays" to 2L)))

        store.save(MessageWatermarks(baselined = true, directMessages = mapOf("stays" to 3L)))

        assertEquals(mapOf("stays" to 3L), store.load().directMessages)
    }
}
