package com.slskdandroid.core.network

import com.slskdandroid.core.model.ConnectionSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the currently active slskd [ConnectionSettings] for the network layer. It is kept
 * up to date by the data layer (which observes persisted settings) and read by
 * [SlskdAuthInterceptor] on every request. `null` means the app is not configured yet.
 */
@Singleton
class SlskdConnectionState @Inject constructor() {
    @Volatile
    var current: ConnectionSettings? = null
}
