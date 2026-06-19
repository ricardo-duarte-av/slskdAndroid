package com.slskdandroid.core.data

import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.DirectoryContentsRequest
import com.slskdandroid.core.network.model.NetworkAvailableRoom
import com.slskdandroid.core.network.model.NetworkBrowseResponse
import com.slskdandroid.core.network.model.NetworkConversation
import com.slskdandroid.core.network.model.NetworkDirectory
import com.slskdandroid.core.network.model.NetworkPrivateMessage
import com.slskdandroid.core.network.model.NetworkRoomMessage
import com.slskdandroid.core.network.model.NetworkRoomUser
import com.slskdandroid.core.network.model.NetworkSearch
import com.slskdandroid.core.network.model.NetworkSearchResponse
import com.slskdandroid.core.network.model.NetworkUserDownloads
import com.slskdandroid.core.network.model.NetworkUserEndpoint
import com.slskdandroid.core.network.model.NetworkUserInfo
import com.slskdandroid.core.network.model.NetworkUserStatus
import com.slskdandroid.core.network.model.QueueDownloadRequest
import com.slskdandroid.core.network.model.StartSearchRequest

/**
 * A hand-written test double for [SlskdApi]. Every endpoint fails by default; a test overrides
 * only the handful of calls the code under test actually makes (so an unexpected call is a loud,
 * obvious failure rather than a silent default). Shared by all `core:data` repository tests.
 */
open class FakeSlskdApi : SlskdApi {

    private fun nope(method: String): Nothing =
        error("FakeSlskdApi.$method was called but not stubbed for this test")

    override suspend fun getSearches(): List<NetworkSearch> = nope("getSearches")
    override suspend fun startSearch(request: StartSearchRequest): NetworkSearch = nope("startSearch")
    override suspend fun getSearch(id: String): NetworkSearch = nope("getSearch")
    override suspend fun cancelSearch(id: String) = nope("cancelSearch")
    override suspend fun getSearchResponses(id: String): List<NetworkSearchResponse> = nope("getSearchResponses")
    override suspend fun deleteSearch(id: String) = nope("deleteSearch")
    override suspend fun getDirectoryContents(
        username: String,
        request: DirectoryContentsRequest,
    ): List<NetworkDirectory> = nope("getDirectoryContents")

    override suspend fun getUserInfo(username: String): NetworkUserInfo = nope("getUserInfo")
    override suspend fun getUserStatus(username: String): NetworkUserStatus = nope("getUserStatus")
    override suspend fun getUserEndpoint(username: String): NetworkUserEndpoint = nope("getUserEndpoint")
    override suspend fun getBrowse(username: String): NetworkBrowseResponse = nope("getBrowse")
    override suspend fun getBrowseStatus(username: String): Double = nope("getBrowseStatus")

    override suspend fun getDownloads(): List<NetworkUserDownloads> = nope("getDownloads")
    override suspend fun enqueueDownloads(username: String, files: List<QueueDownloadRequest>) = nope("enqueueDownloads")
    override suspend fun cancelDownload(username: String, id: String, remove: Boolean) = nope("cancelDownload")
    override suspend fun getUploads(): List<NetworkUserDownloads> = nope("getUploads")
    override suspend fun cancelUpload(username: String, id: String, remove: Boolean) = nope("cancelUpload")

    override suspend fun getConversations(
        includeInactive: Boolean,
        unAcknowledgedOnly: Boolean,
    ): List<NetworkConversation> = nope("getConversations")

    override suspend fun getMessages(username: String): List<NetworkPrivateMessage> = nope("getMessages")
    override suspend fun sendMessage(username: String, message: String) = nope("sendMessage")
    override suspend fun acknowledgeConversation(username: String) = nope("acknowledgeConversation")

    override suspend fun getJoinedRooms(): List<String> = nope("getJoinedRooms")
    override suspend fun getAvailableRooms(): List<NetworkAvailableRoom> = nope("getAvailableRooms")
    override suspend fun getRoomMessages(room: String): List<NetworkRoomMessage> = nope("getRoomMessages")
    override suspend fun getRoomUsers(room: String): List<NetworkRoomUser> = nope("getRoomUsers")
    override suspend fun joinRoom(roomName: String) = nope("joinRoom")
    override suspend fun leaveRoom(room: String) = nope("leaveRoom")
    override suspend fun sendRoomMessage(room: String, message: String) = nope("sendRoomMessage")
}
