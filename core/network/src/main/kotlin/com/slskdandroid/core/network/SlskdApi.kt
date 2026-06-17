package com.slskdandroid.core.network

import com.slskdandroid.core.network.model.NetworkSearch
import com.slskdandroid.core.network.model.NetworkSearchResponse
import com.slskdandroid.core.network.model.NetworkUserDownloads
import com.slskdandroid.core.network.model.QueueDownloadRequest
import com.slskdandroid.core.network.model.StartSearchRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for the slskd REST API (`/api/v0`). Only a slice of the Searches
 * controller is wired up so far; extend with Transfers, Browse, Rooms, Server, etc.
 * as features land. Confirm paths/payloads against the slskd Swagger spec.
 */
interface SlskdApi {

    @POST("api/v0/searches")
    suspend fun startSearch(@Body request: StartSearchRequest): NetworkSearch

    @GET("api/v0/searches/{id}")
    suspend fun getSearch(@Path("id") id: String): NetworkSearch

    @GET("api/v0/searches/{id}/responses")
    suspend fun getSearchResponses(@Path("id") id: String): List<NetworkSearchResponse>

    @DELETE("api/v0/searches/{id}")
    suspend fun deleteSearch(@Path("id") id: String)

    /** All downloads, grouped by user then directory. slskd has no transfers push hub — poll this. */
    @GET("api/v0/transfers/downloads")
    suspend fun getDownloads(): List<NetworkUserDownloads>

    /** (Re-)enqueues downloads of [files] from [username]; used to retry failed/cancelled transfers. */
    @POST("api/v0/transfers/downloads/{username}")
    suspend fun enqueueDownloads(
        @Path("username") username: String,
        @Body files: List<QueueDownloadRequest>,
    )

    /**
     * Cancels the download [id] from [username]. With [remove] = true it is also dropped from the
     * server's list (used to clear a finished/failed entry); otherwise it is merely cancelled.
     */
    @DELETE("api/v0/transfers/downloads/{username}/{id}")
    suspend fun cancelDownload(
        @Path("username") username: String,
        @Path("id") id: String,
        @Query("remove") remove: Boolean,
    )
}
