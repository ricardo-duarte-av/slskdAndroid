package com.slskdandroid.core.network

import com.microsoft.signalr.Action1
import com.slskdandroid.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams slskd's search-hub events. Behind an interface so repositories that consume it can be
 * unit-tested with a fake instead of a live SignalR connection.
 */
interface SearchHub {
    fun events(): Flow<SearchHubEvent>
}

/**
 * Real [SearchHub]. Each collection opens its own hub connection, starts it, and emits every
 * `RESPONSE`/`UPDATE` the server broadcasts (across all searches — callers filter by search id).
 * The connection is started before the flow begins emitting; cancelling the collection
 * unsubscribes the handlers and stops the connection.
 */
@Singleton
class SlskdSearchHub @Inject constructor(
    private val signalRClient: SlskdSignalRClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchHub {
    override fun events(): Flow<SearchHubEvent> = callbackFlow {
        val connection = signalRClient.hubConnection("search")

        val responseSub = connection.on(
            "RESPONSE",
            Action1<HubResponseEnvelope> { envelope ->
                val searchId = envelope.searchId
                val response = envelope.response
                if (searchId != null && response != null) {
                    trySend(SearchHubEvent.Response(searchId, response.toNetworkResponse()))
                }
            },
            HubResponseEnvelope::class.java,
        )

        val updateSub = connection.on(
            "UPDATE",
            Action1<HubSearchSummary> { summary ->
                summary.id?.let { trySend(SearchHubEvent.Update(it, summary.isComplete)) }
            },
            HubSearchSummary::class.java,
        )

        // Blocks until the connection (and SignalR negotiate/handshake) is established.
        connection.start().blockingAwait()

        awaitClose {
            responseSub.unsubscribe()
            updateSub.unsubscribe()
            runCatching { connection.stop().blockingAwait() }
        }
    }.flowOn(ioDispatcher)
}
