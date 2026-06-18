package com.slskdandroid.feature.users.impl

import com.slskdandroid.core.model.UserProfile

sealed interface UsersUiState {
    /** Nothing looked up yet — show the username prompt. [query] is the field's current text. */
    data class Idle(val query: String) : UsersUiState

    data class Loading(val username: String) : UsersUiState

    data class Error(val username: String, val message: String) : UsersUiState

    data class Loaded(val profile: UserProfile) : UsersUiState
}

sealed interface UsersAction {
    data class QueryChanged(val query: String) : UsersAction
    data object Submit : UsersAction

    /** Clear the loaded/loading user and return to the prompt. */
    data object Close : UsersAction

    /** Retry the current username after an error. */
    data object Retry : UsersAction
}
