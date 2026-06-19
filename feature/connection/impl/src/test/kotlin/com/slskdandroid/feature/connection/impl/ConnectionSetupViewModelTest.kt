package com.slskdandroid.feature.connection.impl

import app.cash.turbine.test
import com.slskdandroid.core.model.ConnectionSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [ConnectionSetupViewModel]: field edits, submit gating, and the
 * success/failure outcomes of verifying the connection. Uses a hand-written
 * [FakeConnectionSettingsRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionSetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeConnectionSettingsRepository()

    @Test
    fun `editing a field updates state and clears a previous error`() = runTest {
        repository.verifyResult = Result.failure(IOException("boom"))
        val viewModel = ConnectionSetupViewModel(repository)
        viewModel.onAction(ConnectionSetupAction.BaseUrlChanged("http://host"))
        viewModel.onAction(ConnectionSetupAction.ApiKeyChanged("key"))
        viewModel.onAction(ConnectionSetupAction.Submit)
        assertEquals("boom", viewModel.uiState.value.errorMessage)

        viewModel.onAction(ConnectionSetupAction.BaseUrlChanged("http://other"))

        val state = viewModel.uiState.value
        assertEquals("http://other", state.baseUrl)
        assertNull(state.errorMessage)
    }

    @Test
    fun `submit does nothing when fields are blank`() = runTest {
        val viewModel = ConnectionSetupViewModel(repository)

        viewModel.onAction(ConnectionSetupAction.Submit)

        assertTrue(repository.verifiedSettings.isEmpty())
        assertFalse(viewModel.uiState.value.isVerifying)
    }

    @Test
    fun `successful submit verifies the entered settings and signals the connection is established`() = runTest {
        val viewModel = ConnectionSetupViewModel(repository)
        viewModel.onAction(ConnectionSetupAction.BaseUrlChanged("http://host"))
        viewModel.onAction(ConnectionSetupAction.ApiKeyChanged("key"))

        viewModel.connectionEstablished.test {
            viewModel.onAction(ConnectionSetupAction.Submit)
            awaitItem() // the one-shot "established" signal
        }

        assertEquals(
            ConnectionSettings(baseUrl = "http://host", apiKey = "key"),
            repository.verifiedSettings.single(),
        )
        val state = viewModel.uiState.value
        assertFalse(state.isVerifying)
        assertNull(state.errorMessage)
    }

    @Test
    fun `failed submit surfaces the error message and stops verifying`() = runTest {
        repository.verifyResult = Result.failure(IOException("Authentication failed"))
        val viewModel = ConnectionSetupViewModel(repository)
        viewModel.onAction(ConnectionSetupAction.BaseUrlChanged("http://host"))
        viewModel.onAction(ConnectionSetupAction.ApiKeyChanged("key"))

        viewModel.onAction(ConnectionSetupAction.Submit)

        val state = viewModel.uiState.value
        assertEquals("Authentication failed", state.errorMessage)
        assertFalse(state.isVerifying)
    }
}
