package com.slskdandroid.core.data

import com.slskdandroid.core.model.ConnectionSettings
import com.slskdandroid.core.network.ConnectionTester

/**
 * Fake [ConnectionTester] that returns a canned [result] and records what it was asked to verify,
 * so tests can drive the success/failure branches without real networking.
 */
class FakeConnectionTester(
    var result: Result<Unit> = Result.success(Unit),
) : ConnectionTester {
    val verified = mutableListOf<ConnectionSettings>()

    override suspend fun verify(settings: ConnectionSettings): Result<Unit> {
        verified += settings
        return result
    }
}
