package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.UserPresence
import com.slskdandroid.core.model.UserProfile
import com.slskdandroid.core.network.SlskdApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class DefaultUsersRepository @Inject constructor(
    private val api: SlskdApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UsersRepository {

    override suspend fun getUser(username: String): UserProfile = withContext(ioDispatcher) {
        coroutineScope {
            // status/endpoint can 404 (peer offline) independently of info — tolerate their failure.
            val statusDeferred = async { runCatching { api.getUserStatus(username) }.getOrNull() }
            val endpointDeferred = async { runCatching { api.getUserEndpoint(username) }.getOrNull() }

            val info = api.getUserInfo(username)
            val status = statusDeferred.await()
            val endpoint = endpointDeferred.await()

            UserProfile(
                username = username,
                presence = status?.presence.toPresence(),
                isPrivileged = status?.isPrivileged == true,
                hasFreeUploadSlot = info.hasFreeUploadSlot,
                uploadSlots = info.uploadSlots,
                queueLength = info.queueLength,
                ipAddress = endpoint?.address,
                port = endpoint?.port,
                description = info.description?.takeIf { it.isNotBlank() },
                pictureBase64 = info.picture?.takeIf { info.hasPicture && it.isNotBlank() },
            )
        }
    }
}

private fun String?.toPresence(): UserPresence = when (this?.lowercase()) {
    "online" -> UserPresence.Online
    "away" -> UserPresence.Away
    "offline" -> UserPresence.Offline
    else -> UserPresence.Unknown
}
