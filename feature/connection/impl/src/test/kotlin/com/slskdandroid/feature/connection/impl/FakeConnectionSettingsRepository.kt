package com.slskdandroid.feature.connection.impl

import com.slskdandroid.core.data.ConnectionSettingsRepository
import com.slskdandroid.core.model.ConnectionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Test double for [ConnectionSettingsRepository]. [verifyResult] drives the success/failure branch
 * and [verifiedSettings] records what the ViewModel submitted.
 */
class FakeConnectionSettingsRepository : ConnectionSettingsRepository {

    override val connectionSettings: Flow<ConnectionSettings?> = flowOf(null)

    var verifyResult: Result<Unit> = Result.success(Unit)
    val verifiedSettings = mutableListOf<ConnectionSettings>()

    override suspend fun verifyAndSave(settings: ConnectionSettings): Result<Unit> {
        verifiedSettings += settings
        return verifyResult
    }

    override suspend fun clear() = error("unused by ConnectionSetupViewModel")
}
