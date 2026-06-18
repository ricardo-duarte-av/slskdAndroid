package com.slskdandroid.feature.users.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.UsersRepository
import com.slskdandroid.feature.users.api.USERS_USER_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val usersRepository: UsersRepository,
) : ViewModel() {

    private var query: String = savedStateHandle.get<String>(USERS_USER_ARG).orEmpty()
    private var lookupJob: Job? = null

    private val _uiState = MutableStateFlow<UsersUiState>(UsersUiState.Idle(query))
    val uiState: StateFlow<UsersUiState> = _uiState.asStateFlow()

    init {
        // When deep-linked with a username (users/{username}), look it up immediately.
        if (query.isNotBlank()) lookup(query)
    }

    fun onAction(action: UsersAction) {
        when (action) {
            is UsersAction.QueryChanged -> {
                query = action.query
                _uiState.value = UsersUiState.Idle(query)
            }

            UsersAction.Submit -> lookup(query)
            UsersAction.Retry -> lookup(currentUsername())

            UsersAction.Close -> {
                lookupJob?.cancel()
                _uiState.value = UsersUiState.Idle(query)
            }
        }
    }

    private fun lookup(raw: String) {
        val username = raw.trim()
        if (username.isEmpty() || _uiState.value is UsersUiState.Loading) return
        lookupJob?.cancel()
        _uiState.value = UsersUiState.Loading(username)
        lookupJob = viewModelScope.launch {
            runCatching { usersRepository.getUser(username) }
                .onSuccess { _uiState.value = UsersUiState.Loaded(it) }
                .onFailure {
                    _uiState.value = UsersUiState.Error(
                        username = username,
                        message = it.message ?: "Couldn't load $username. They may be offline.",
                    )
                }
        }
    }

    private fun currentUsername(): String = when (val state = _uiState.value) {
        is UsersUiState.Loading -> state.username
        is UsersUiState.Error -> state.username
        is UsersUiState.Loaded -> state.profile.username
        is UsersUiState.Idle -> query
    }
}
