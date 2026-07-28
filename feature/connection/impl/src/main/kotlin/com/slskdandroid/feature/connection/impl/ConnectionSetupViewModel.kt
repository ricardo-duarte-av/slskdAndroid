package com.slskdandroid.feature.connection.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.ConnectionSettingsRepository
import com.slskdandroid.core.designsystem.component.UiText
import com.slskdandroid.core.designsystem.component.toUiText
import com.slskdandroid.core.model.ConnectionFailure
import com.slskdandroid.core.model.ConnectionFailureException
import com.slskdandroid.core.model.ConnectionSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionSetupViewModel @Inject constructor(
    private val connectionSettingsRepository: ConnectionSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionSetupUiState())
    val uiState: StateFlow<ConnectionSetupUiState> = _uiState.asStateFlow()

    // One-shot signal that the connection was verified and saved; the host navigates onward.
    private val _connectionEstablished = Channel<Unit>(Channel.BUFFERED)
    val connectionEstablished = _connectionEstablished.receiveAsFlow()

    fun onAction(action: ConnectionSetupAction) {
        when (action) {
            is ConnectionSetupAction.BaseUrlChanged ->
                _uiState.update { it.copy(baseUrl = action.value, errorMessage = null) }

            is ConnectionSetupAction.ApiKeyChanged ->
                _uiState.update { it.copy(apiKey = action.value, errorMessage = null) }

            ConnectionSetupAction.Submit -> submit()
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
        viewModelScope.launch {
            val settings = ConnectionSettings(baseUrl = state.baseUrl, apiKey = state.apiKey)
            connectionSettingsRepository.verifyAndSave(settings)
                .onSuccess {
                    _uiState.update { it.copy(isVerifying = false) }
                    _connectionEstablished.send(Unit)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = error.toUiMessage(),
                        )
                    }
                }
        }
    }
}

/**
 * Maps a verification failure to displayable text. Typed [ConnectionFailure]s become localized
 * resources; anything else falls back to its own message, then to a generic string.
 */
private fun Throwable.toUiMessage(): UiText = when (val failure = (this as? ConnectionFailureException)?.failure) {
    ConnectionFailure.InvalidUrl -> UiText.Res(R.string.connection_error_invalid_url)
    ConnectionFailure.AuthRejected -> UiText.Res(R.string.connection_error_auth)
    is ConnectionFailure.HttpError -> UiText.Res(R.string.connection_error_http, failure.code)
    ConnectionFailure.Unreachable -> UiText.Res(R.string.connection_error_unreachable)
    null -> toUiText(R.string.connection_error_fallback)
}
