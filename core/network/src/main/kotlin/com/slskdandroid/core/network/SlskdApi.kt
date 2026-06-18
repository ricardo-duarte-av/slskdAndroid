package com.slskdandroid.core.network

import com.slskdandroid.core.network.model.DirectoryContentsRequest
import com.slskdandroid.core.network.model.NetworkBrowseResponse
import com.slskdandroid.core.network.model.NetworkAvailableRoom
import com.slskdandroid.core.network.model.NetworkConversation
import com.slskdandroid.core.network.model.NetworkPrivateMessage
import com.slskdandroid.core.network.model.NetworkRoomMessage
import com.slskdandroid.core.network.model.NetworkRoomUser
import com.slskdandroid.core.network.model.NetworkDirectory
import com.slskdandroid.core.network.model.NetworkSearch
import com.slskdandroid.core.network.model.NetworkSearchResponse
import com.slskdandroid.core.network.model.NetworkUserDownloads
import com.slskdandroid.core.network.model.NetworkUserEndpoint
import com.slskdandroid.core.network.model.NetworkUserInfo
import com.slskdandroid.core.network.model.NetworkUserStatus
import com.slskdandroid.core.network.model.QueueDownloadRequest
import com.slskdandroid.core.network.model.StartSearchRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for the slskd REST API (`/api/v0`). Only a slice of the Searches
 * controller is wired up so far; extend with Transfers, Browse, Rooms, Server, etc.
 * as features land. Confirm paths/payloads against the slskd Swagger spec.
 */
interface SlskdApi {

    @GET("api/v0/searches")
    suspend fun getSearches(): List<NetworkSearch>

    @POST("api/v0/searches")
    suspend fun startSearch(@Body request: StartSearchRequest): NetworkSearch

    @GET("api/v0/searches/{id}")
    suspend fun getSearch(@Path("id") id: String): NetworkSearch

    /** Cancels (stops) an in-progress search without deleting it. */
    @PUT("api/v0/searches/{id}")
    suspend fun cancelSearch(@Path("id") id: String)

    @GET("api/v0/searches/{id}/responses")
    suspend fun getSearchResponses(@Path("id") id: String): List<NetworkSearchResponse>

    @DELETE("api/v0/searches/{id}")
    suspend fun deleteSearch(@Path("id") id: String)

    /** Requests a peer's listing of a directory (used to expand a folder from a search result). */
    @POST("api/v0/users/{username}/directory")
    suspend fun getDirectoryContents(
        @Path("username") username: String,
        @Body request: DirectoryContentsRequest,
    ): List<NetworkDirectory>

    /** A peer's self-reported profile (description, picture, upload slots, queue length). 404 if offline. */
    @GET("api/v0/users/{username}/info")
    suspend fun getUserInfo(@Path("username") username: String): NetworkUserInfo

    /** A peer's online presence and privileged flag. 404 if offline. */
    @GET("api/v0/users/{username}/status")
    suspend fun getUserStatus(@Path("username") username: String): NetworkUserStatus

    /** A peer's network address (`{ address, port }`). 404 if offline. */
    @GET("api/v0/users/{username}/endpoint")
    suspend fun getUserEndpoint(@Path("username") username: String): NetworkUserEndpoint

    /** Browses a peer's entire share. Blocks until the share is fetched (can be slow/large). */
    @GET("api/v0/users/{username}/browse")
    suspend fun getBrowse(@Path("username") username: String): NetworkBrowseResponse

    /** Percent (0–100) of the in-progress browse for [username]; 404 once none is running. */
    @GET("api/v0/users/{username}/browse/status")
    suspend fun getBrowseStatus(@Path("username") username: String): Double

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

    /** All uploads, grouped by user then directory. Same shape as downloads; poll it (no push hub). */
    @GET("api/v0/transfers/uploads")
    suspend fun getUploads(): List<NetworkUserDownloads>

    /**
     * Cancels the upload [id] to [username]. With [remove] = true it is also dropped from the
     * server's list. There is no upload (re-)initiation endpoint — uploads are peer-driven.
     */
    @DELETE("api/v0/transfers/uploads/{username}/{id}")
    suspend fun cancelUpload(
        @Path("username") username: String,
        @Path("id") id: String,
        @Query("remove") remove: Boolean,
    )

    /**
     * All private-message conversations. slskd has no messaging push hub, so poll this. With
     * [includeInactive] = true closed/idle threads are returned too (so the list stays complete).
     */
    @GET("api/v0/conversations")
    suspend fun getConversations(
        @Query("includeInactive") includeInactive: Boolean,
        @Query("unAcknowledgedOnly") unAcknowledgedOnly: Boolean,
    ): List<NetworkConversation>

    /** The messages of one conversation. 404 if no conversation with [username] exists yet. */
    @GET("api/v0/conversations/{username}/messages")
    suspend fun getMessages(@Path("username") username: String): List<NetworkPrivateMessage>

    /**
     * Sends a private message to [username]. The body is the bare message text (serialized as a
     * JSON string, which slskd's `[FromBody] string` binder accepts). 201 on success.
     */
    @POST("api/v0/conversations/{username}")
    suspend fun sendMessage(@Path("username") username: String, @Body message: String)

    /** Acknowledges (marks read) all messages from [username]. */
    @PUT("api/v0/conversations/{username}")
    suspend fun acknowledgeConversation(@Path("username") username: String)

    /** The names of the rooms this user has currently joined. */
    @GET("api/v0/rooms/joined")
    suspend fun getJoinedRooms(): List<String>

    /** All rooms available on the network (the room-search list). */
    @GET("api/v0/rooms/available")
    suspend fun getAvailableRooms(): List<NetworkAvailableRoom>

    /** Recent chat messages in a joined room. slskd has no rooms push hub, so poll this. */
    @GET("api/v0/rooms/joined/{room}/messages")
    suspend fun getRoomMessages(@Path("room") room: String): List<NetworkRoomMessage>

    /** The current members of a joined room (with per-user country/status). */
    @GET("api/v0/rooms/joined/{room}/users")
    suspend fun getRoomUsers(@Path("room") room: String): List<NetworkRoomUser>

    /** Joins the room named by the bare JSON-string body. */
    @POST("api/v0/rooms/joined")
    suspend fun joinRoom(@Body roomName: String)

    /** Leaves a joined room. */
    @DELETE("api/v0/rooms/joined/{room}")
    suspend fun leaveRoom(@Path("room") room: String)

    /** Sends a chat message (bare JSON-string body) to a joined room. */
    @POST("api/v0/rooms/joined/{room}/messages")
    suspend fun sendRoomMessage(@Path("room") room: String, @Body message: String)
}
