package com.slskdandroid.core.network

import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for slskd SignalR hub connections. slskd pushes real-time updates (search
 * responses, transfer progress, logs) over SignalR hubs mounted under the `/hub/` path,
 * e.g. `/hub/search` and `/hub/transfers`. Callers build a connection, register `.on(...)`
 * handlers for the hub's server-to-client methods, then `start()`.
 *
 * The connection carries the configured API key as the `X-API-Key` header, which slskd's
 * hubs accept (they authorize under `AuthPolicy.Any`).
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
        return HubConnectionBuilder.create(url)
            .withHeader("X-API-Key", settings.apiKey)
            // Long polling instead of WebSockets: it's plain HTTP, so it works through reverse
            // proxies that don't forward WS upgrades, and carries the X-API-Key header on every
            // request just like the REST API. SignalR's Java client does not auto-fall-back from
            // a failed WebSocket transport, which surfaces as "error starting the WebSocket
            // transport" behind such proxies.
            .withTransport(TransportEnum.LONG_POLLING)
            .build()
    }
}
