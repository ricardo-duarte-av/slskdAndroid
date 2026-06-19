package com.slskdandroid.core.data

import com.slskdandroid.core.datastore.ConnectionSettingsDataSource
import com.slskdandroid.core.model.ConnectionSettings
import com.slskdandroid.core.network.SlskdConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [DefaultConnectionSettingsRepository.verifyAndSave] — the "validate the candidate
 * settings against the server, and only persist + activate them on success" rule that gates the
 * app's first-run setup.
 *
 * Uses a fake [ConnectionTester] (no real HTTP) but the *real* [ConnectionSettingsDataSource] over
 * an in-memory [FakePreferencesDataStore], so persistence is genuinely exercised. `backgroundScope`
 * (from `runTest`) stands in for the repository's `@ApplicationScope` and is auto-cancelled when
 * the test ends, so the repository's init-block collector doesn't leak.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultConnectionSettingsRepositoryTest {

    @Test
    fun `verifyAndSave on success normalizes, persists, and activates the settings`() = runTest {
        val dataSource = ConnectionSettingsDataSource(FakePreferencesDataStore())
        val tester = FakeConnectionTester(Result.success(Unit))
        val connectionState = SlskdConnectionState()
        val repository = DefaultConnectionSettingsRepository(dataSource, tester, connectionState, backgroundScope)

        val result = repository.verifyAndSave(
            ConnectionSettings(baseUrl = "  https://slsk.example.com/  ", apiKey = "abc"),
        )

        assertTrue(result.isSuccess)
        val expected = ConnectionSettings(baseUrl = "https://slsk.example.com", apiKey = "abc")
        // The URL was normalized (trimmed, trailing slash removed) before it was verified...
        assertEquals(expected, tester.verified.single())
        // ...then persisted and made active for the network layer.
        assertEquals(expected, dataSource.connectionSettings.first())
        assertEquals(expected, connectionState.current)
    }

    @Test
    fun `verifyAndSave on failure persists nothing and surfaces the error`() = runTest {
        val dataSource = ConnectionSettingsDataSource(FakePreferencesDataStore())
        val tester = FakeConnectionTester(Result.failure(IOException("Authentication failed")))
        val connectionState = SlskdConnectionState()
        val repository = DefaultConnectionSettingsRepository(dataSource, tester, connectionState, backgroundScope)

        val result = repository.verifyAndSave(ConnectionSettings("https://slsk.example.com", "bad"))

        assertTrue(result.isFailure)
        assertEquals("Authentication failed", result.exceptionOrNull()?.message)
        assertNull(dataSource.connectionSettings.first())
        assertNull(connectionState.current)
    }

    @Test
    fun `clear removes persisted settings and deactivates the connection`() = runTest {
        val dataSource = ConnectionSettingsDataSource(FakePreferencesDataStore())
        dataSource.save(ConnectionSettings("https://slsk.example.com", "abc"))
        val connectionState = SlskdConnectionState().apply {
            current = ConnectionSettings("https://slsk.example.com", "abc")
        }
        val repository = DefaultConnectionSettingsRepository(
            dataSource, FakeConnectionTester(), connectionState, backgroundScope,
        )

        repository.clear()

        assertNull(dataSource.connectionSettings.first())
        assertNull(connectionState.current)
    }
}
