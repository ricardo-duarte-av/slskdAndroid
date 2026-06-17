package com.slskdandroid.feature.connection.impl

/** Form + submission state for the connection-setup screen. */
data class ConnectionSetupUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val isVerifying: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && !isVerifying
}

sealed interface ConnectionSetupAction {
    data class BaseUrlChanged(val value: String) : ConnectionSetupAction
    data class ApiKeyChanged(val value: String) : ConnectionSetupAction
    data object Submit : ConnectionSetupAction
}
