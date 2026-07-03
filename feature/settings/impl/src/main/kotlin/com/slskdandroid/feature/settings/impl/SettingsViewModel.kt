package com.slskdandroid.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slskdandroid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.notificationSettings
        .map { SettingsUiState(it.enabled, it.checkIntervalSeconds) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetNotificationsEnabled -> viewModelScope.launch {
                settingsRepository.setNotificationsEnabled(action.enabled)
            }

            is SettingsAction.SetCheckIntervalSeconds -> viewModelScope.launch {
                settingsRepository.setCheckIntervalSeconds(action.seconds)
            }
        }
    }
}
