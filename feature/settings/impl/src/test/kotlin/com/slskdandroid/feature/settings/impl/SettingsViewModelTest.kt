package com.slskdandroid.feature.settings.impl

import app.cash.turbine.test
import com.slskdandroid.core.model.NotificationSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()

    private fun viewModel() = SettingsViewModel(repository)

    @Test
    fun `exposes the persisted settings`() = runTest {
        repository.state.value = NotificationSettings(enabled = true, checkIntervalSeconds = 120)
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = awaitItem().takeIf { it.notificationsEnabled } ?: awaitItem()
            assertTrue(state.notificationsEnabled)
            assertEquals(120, state.checkIntervalSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling notifications writes through to the repository`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.SetNotificationsEnabled(true))
        assertTrue(repository.state.value.enabled)
        viewModel.onAction(SettingsAction.SetNotificationsEnabled(false))
        assertFalse(repository.state.value.enabled)
    }

    @Test
    fun `interval below the minimum is clamped`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.SetCheckIntervalSeconds(1))
        assertEquals(NotificationSettings.MIN_INTERVAL_SECONDS, repository.state.value.checkIntervalSeconds)
    }
}
