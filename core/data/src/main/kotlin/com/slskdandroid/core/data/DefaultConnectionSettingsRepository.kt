package com.slskdandroid.core.data

import com.slskdandroid.core.common.ApplicationScope
import com.slskdandroid.core.datastore.ConnectionSettingsDataSource
import com.slskdandroid.core.model.ConnectionSettings
import com.slskdandroid.core.network.SlskdConnectionState
import com.slskdandroid.core.network.SlskdConnectionTester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultConnectionSettingsRepository @Inject constructor(
    private val dataSource: ConnectionSettingsDataSource,
    private val connectionTester: SlskdConnectionTester,
    private val connectionState: SlskdConnectionState,
    @ApplicationScope private val appScope: CoroutineScope,
) : ConnectionSettingsRepository {

    init {
        // Keep the network layer's active settings in sync with whatever is persisted, so a
        // relaunch with stored settings is immediately able to make authenticated calls.
        dataSource.connectionSettings
            .onEach { settings -> connectionState.current = settings }
            .launchIn(appScope)
    }

    override val connectionSettings: Flow<ConnectionSettings?> =
        dataSource.connectionSettings.onEach { connectionState.current = it }

    override suspend fun verifyAndSave(settings: ConnectionSettings): Result<Unit> {
        val normalized = settings.copy(baseUrl = settings.baseUrl.trim().trimEnd('/'))
        return connectionTester.verify(normalized).onSuccess {
            dataSource.save(normalized)
            connectionState.current = normalized
        }
    }

    override suspend fun clear() {
        dataSource.clear()
        connectionState.current = null
    }
}
