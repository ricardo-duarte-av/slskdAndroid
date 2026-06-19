package com.slskdandroid.feature.users.impl

import com.slskdandroid.core.data.UsersRepository
import com.slskdandroid.core.model.UserPresence
import com.slskdandroid.core.model.UserProfile

/** Test double for [UsersRepository]; [behavior] drives the result and records lookups. */
class FakeUsersRepository : UsersRepository {

    var behavior: suspend (String) -> UserProfile = { profile(it) }
    val requested = mutableListOf<String>()

    override suspend fun getUser(username: String): UserProfile {
        requested += username
        return behavior(username)
    }
}

fun profile(username: String) = UserProfile(
    username = username,
    presence = UserPresence.Online,
    isPrivileged = false,
    hasFreeUploadSlot = true,
    uploadSlots = 1,
    queueLength = 0,
    ipAddress = null,
    port = null,
    description = null,
    pictureBase64 = null,
)
