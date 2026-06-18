package com.slskdandroid.feature.rooms.impl

import com.slskdandroid.core.model.AvailableRoom
import com.slskdandroid.core.model.RoomMessage
import com.slskdandroid.core.model.RoomUser

/**
 * The Rooms tab's single state. A self-contained view machine: the joined-room [list] is always
 * present, an [open] room overlays it when one is opened, and the available-room [search] screen
 * overlays either when launched.
 */
data class RoomsUiState(
    val list: ListState = ListState.Loading,
    val open: OpenRoom? = null,
    val search: SearchState? = null,
)

sealed interface ListState {
    data object Loading : ListState
    data class Error(val message: String) : ListState
    data class Loaded(val rooms: List<String>) : ListState
}

/**
 * An open room. [users] is the member snapshot (null while loading) used both for the toggleable
 * member panel ([usersVisible]) and to look up each message sender's country flag.
 */
data class OpenRoom(
    val name: String,
    val messages: List<RoomMessage> = emptyList(),
    val loading: Boolean = true,
    val draft: String = "",
    val sending: Boolean = false,
    val usersVisible: Boolean = false,
    val users: List<RoomUser>? = null,
)

/** The available-room search screen. [joining] holds rooms whose join is in flight. */
data class SearchState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Loading,
    val joining: Set<String> = emptySet(),
)

sealed interface SearchPhase {
    data object Loading : SearchPhase
    data class Error(val message: String) : SearchPhase
    data class Loaded(val rooms: List<AvailableRoom>) : SearchPhase
}

sealed interface RoomsAction {
    data class OpenRoom(val name: String) : RoomsAction
    data object CloseRoom : RoomsAction
    data class DraftChanged(val text: String) : RoomsAction
    data object SendMessage : RoomsAction
    data object ToggleUsers : RoomsAction
    data class LeaveRoom(val name: String) : RoomsAction
    data object RetryList : RoomsAction

    data object OpenSearch : RoomsAction
    data object CloseSearch : RoomsAction
    data class SearchQueryChanged(val query: String) : RoomsAction
    data object RetrySearch : RoomsAction
    data class JoinRoom(val name: String) : RoomsAction
}
