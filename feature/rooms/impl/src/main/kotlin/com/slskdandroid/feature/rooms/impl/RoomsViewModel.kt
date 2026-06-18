package com.slskdandroid.feature.rooms.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.RoomsRepository
import com.slskdandroid.core.model.RoomMessage
import com.slskdandroid.core.model.RoomUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val roomsRepository: RoomsRepository,
) : ViewModel() {

    // Mutable UI inputs. Polled data hangs off these via flatMapLatest and the whole chain runs
    // through stateIn(WhileSubscribed), so nothing polls while the tab is off-screen.
    private val retryTrigger = MutableStateFlow(0)
    private val openRoom = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow("")
    private val sending = MutableStateFlow(false)
    private val usersVisible = MutableStateFlow(false)
    private val usersRefresh = MutableStateFlow(0)

    private val showSearch = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val searchRetry = MutableStateFlow(0)
    private val joining = MutableStateFlow<Set<String>>(emptySet())

    private val listState: Flow<ListState> = retryTrigger.flatMapLatest {
        roomsRepository.joinedRooms()
            .map<List<String>, ListState> { ListState.Loaded(it) }
            .onStart { emit(ListState.Loading) }
            .catch { e -> emit(ListState.Error(e.message ?: "Couldn't load rooms.")) }
    }

    private val messagesFlow: Flow<RoomMessages?> = openRoom.flatMapLatest { room ->
        if (room == null) {
            flowOf<RoomMessages?>(null)
        } else {
            roomsRepository.messages(room)
                .map { RoomMessages(room, it) }
                .onStart { emit(RoomMessages(room, null)) }
        }
    }

    private val usersFlow: Flow<List<RoomUser>?> =
        combine(openRoom, usersRefresh) { room, _ -> room }.flatMapLatest { room ->
            if (room == null) {
                flowOf<List<RoomUser>?>(null)
            } else {
                flow {
                    emit(null)
                    emit(runCatching { roomsRepository.users(room) }.getOrDefault(emptyList()))
                }
            }
        }

    private val openRoomState: Flow<OpenRoom?> =
        combine(messagesFlow, draft, sending, usersVisible, usersFlow) { msgs, draftText, isSending, visible, users ->
            msgs?.let {
                OpenRoom(
                    name = it.room,
                    messages = it.messages.orEmpty(),
                    loading = it.messages == null,
                    draft = draftText,
                    sending = isSending,
                    usersVisible = visible,
                    users = users,
                )
            }
        }

    private val searchState: Flow<SearchState?> =
        combine(availablePhaseFlow(), searchQuery, joining) { phase, query, joiningSet ->
            phase?.let { SearchState(query = query, phase = it, joining = joiningSet) }
        }

    val uiState: StateFlow<RoomsUiState> =
        combine(listState, openRoomState, searchState) { list, open, search ->
            RoomsUiState(list = list, open = open, search = search)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoomsUiState(),
        )

    private fun availablePhaseFlow(): Flow<SearchPhase?> =
        combine(showSearch, searchRetry) { show, _ -> show }.flatMapLatest { show ->
            if (!show) {
                flowOf<SearchPhase?>(null)
            } else {
                flow {
                    emit(SearchPhase.Loading)
                    emit(
                        runCatching { roomsRepository.availableRooms() }.fold(
                            onSuccess = { SearchPhase.Loaded(it) },
                            onFailure = { SearchPhase.Error(it.message ?: "Couldn't load rooms.") },
                        ),
                    )
                }
            }
        }

    fun onAction(action: RoomsAction) {
        when (action) {
            is RoomsAction.OpenRoom -> openRoomNamed(action.name)
            RoomsAction.CloseRoom -> {
                openRoom.value = null
                usersVisible.value = false
            }
            is RoomsAction.DraftChanged -> draft.value = action.text
            RoomsAction.SendMessage -> sendMessage()
            RoomsAction.ToggleUsers -> {
                val nowVisible = !usersVisible.value
                usersVisible.value = nowVisible
                if (nowVisible) usersRefresh.value++
            }
            is RoomsAction.LeaveRoom -> leaveRoom(action.name)
            RoomsAction.RetryList -> retryTrigger.value++

            RoomsAction.OpenSearch -> {
                searchQuery.value = ""
                showSearch.value = true
            }
            RoomsAction.CloseSearch -> showSearch.value = false
            is RoomsAction.SearchQueryChanged -> searchQuery.value = action.query
            RoomsAction.RetrySearch -> searchRetry.value++
            is RoomsAction.JoinRoom -> joinRoom(action.name)
        }
    }

    private fun openRoomNamed(name: String) {
        draft.value = ""
        sending.value = false
        usersVisible.value = false
        openRoom.value = name
    }

    private fun sendMessage() {
        val room = openRoom.value ?: return
        val text = draft.value.trim()
        if (text.isEmpty() || sending.value) return
        sending.value = true
        viewModelScope.launch {
            runCatching { roomsRepository.send(room, text) }
                .onSuccess { draft.value = "" }
            sending.value = false
        }
    }

    private fun leaveRoom(name: String) {
        viewModelScope.launch { runCatching { roomsRepository.leave(name) } }
        if (openRoom.value == name) {
            openRoom.value = null
            usersVisible.value = false
        }
    }

    private fun joinRoom(name: String) {
        if (name in joining.value) return
        joining.value = joining.value + name
        viewModelScope.launch {
            runCatching { roomsRepository.join(name) }
                .onSuccess {
                    joining.value = joining.value - name
                    showSearch.value = false
                    openRoomNamed(name)
                }
                .onFailure { joining.value = joining.value - name }
        }
    }

    /** The raw messages poll for an open room; null [messages] means the first poll is in flight. */
    private data class RoomMessages(val room: String, val messages: List<RoomMessage>?)
}
