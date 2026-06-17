package com.slskdandroid.core.network

import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for slskd SignalR hub connections. slskd pushes real-time updates (search
 * responses, transfer progress, logs) over SignalR hubs mounted under the `/hub/` path,
 * e.g. `/hub/search` and `/hub/transfers`. Callers build a connection, register `.on(...)`
 * handlers for the hub's server-to-client methods, then `start()`.
 *
 * Hub names, method names and payload shapes must be confirmed against the slskd source
 * (the `Hubs` classes) for the target version — see the project CLAUDE.md.
 */
@Singleton
class SlskdSignalRClient @Inject constructor(
    private val connectionState: SlskdConnectionState,
) {
    /** Builds (but does not start) a hub connection for the given hub path, e.g. "search". */
    fun hubConnection(hub: String): HubConnection {
        val settings = connectionState.current
            ?: error("slskd connection is not configured")
        val url = settings.baseUrl.trimEnd('/') + "/hub/" + hub.trimStart('/')
        val builder = HubConnectionBuilder.create(url)
        // slskd accepts the API key via the access-token provider / query string.
        return builder.build()
    }
}
