package com.slskdandroid.feature.users.impl

import androidx.lifecycle.SavedStateHandle
import com.slskdandroid.feature.users.api.USERS_USER_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeUsersRepository()

    @Test
    fun `submitting a username loads the profile`() = runTest {
        val viewModel = UsersViewModel(SavedStateHandle(), repository)

        viewModel.onAction(UsersAction.QueryChanged("alice"))
        viewModel.onAction(UsersAction.Submit)

        val state = viewModel.uiState.value
        assertTrue(state is UsersUiState.Loaded)
        assertEquals("alice", (state as UsersUiState.Loaded).profile.username)
        assertEquals(listOf("alice"), repository.requested)
    }

    @Test
    fun `a blank submit does not look anyone up`() = runTest {
        val viewModel = UsersViewModel(SavedStateHandle(), repository)

        viewModel.onAction(UsersAction.Submit)

        assertTrue(viewModel.uiState.value is UsersUiState.Idle)
        assertTrue(repository.requested.isEmpty())
    }

    @Test
    fun `a failing lookup surfaces an error`() = runTest {
        repository.behavior = { throw IOException("offline") }
        val viewModel = UsersViewModel(SavedStateHandle(), repository)

        viewModel.onAction(UsersAction.QueryChanged("ghost"))
        viewModel.onAction(UsersAction.Submit)

        val state = viewModel.uiState.value
        assertTrue(state is UsersUiState.Error)
        assertEquals("offline", (state as UsersUiState.Error).message)
    }

    @Test
    fun `a deep-link username is looked up immediately`() = runTest {
        val viewModel = UsersViewModel(SavedStateHandle(mapOf(USERS_USER_ARG to "alice")), repository)

        assertEquals(listOf("alice"), repository.requested)
        assertTrue(viewModel.uiState.value is UsersUiState.Loaded)
    }
}
