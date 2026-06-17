package com.slskdandroid.core.network

import com.slskdandroid.core.network.model.NetworkFile
import com.slskdandroid.core.network.model.NetworkSearchResponse

/**
 * Events emitted by slskd's search SignalR hub (`SearchHubMethods`). Only the events the
 * client reacts to are modelled: incremental responses and search-state updates.
 */
sealed interface SearchHubEvent {
    /** A peer's response (`RESPONSE`) to the search identified by [searchId]. */
    data class Response(val searchId: String, val response: NetworkSearchResponse) : SearchHubEvent

    /** A search-state update (`UPDATE`); [isComplete] flips true when slskd finishes. */
    data class Update(val searchId: String, val isComplete: Boolean) : SearchHubEvent
}

/**
 * Gson-deserialized wire types for the hub. The SignalR Java client uses Gson (not
 * kotlinx.serialization) and instantiates these via Unsafe, so fields are nullable and the
 * field names must match slskd's camelCase JSON. Keep these separate from the Retrofit DTOs.
 */
internal class HubResponseEnvelope {
    val searchId: String? = null
    val response: HubResponse? = null
}

internal class HubResponse {
    val username: String? = null
    val hasFreeUploadSlot: Boolean = false
    val uploadSpeed: Long = 0
    val queueLength: Long = 0
    val files: List<HubFile>? = null
}

internal class HubFile {
    val filename: String? = null
    val size: Long = 0
    val bitRate: Int? = null
    val length: Int? = null
}

internal class HubSearchSummary {
    val id: String? = null
    val isComplete: Boolean = false
}

internal fun HubResponse.toNetworkResponse(): NetworkSearchResponse = NetworkSearchResponse(
    username = username.orEmpty(),
    hasFreeUploadSlot = hasFreeUploadSlot,
    uploadSpeed = uploadSpeed,
    queueLength = queueLength,
    files = files.orEmpty().map { file ->
        NetworkFile(
            filename = file.filename.orEmpty(),
            size = file.size,
            bitRate = file.bitRate,
            length = file.length,
        )
    },
)
