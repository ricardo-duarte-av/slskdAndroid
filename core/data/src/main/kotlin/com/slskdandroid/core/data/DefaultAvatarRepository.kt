package com.slskdandroid.core.data

import android.content.Context
import android.util.Base64
import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.network.SlskdApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-tier avatar cache:
 *  - **Memory** (keyed by username) short-circuits the network for the rest of the process, so a
 *    user's picture is fetched at most once per session no matter how many times it's rendered.
 *  - **Disk** (under `cacheDir/avatars/`, keyed by a SHA-256 of the username + the base64 the info
 *    endpoint returned) persists the decoded bytes across sessions. The content-addressed key means
 *    a changed picture lands in a new file rather than serving a stale one.
 */
@Singleton
internal class DefaultAvatarRepository @Inject constructor(
    private val api: SlskdApi,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AvatarRepository {

    private val memory = ConcurrentHashMap<String, ByteArray>()
    private val knownEmpty = ConcurrentHashMap.newKeySet<String>()

    override suspend fun getAvatar(username: String): ByteArray? {
        memory[username]?.let { return it }
        if (username in knownEmpty) return null

        return withContext(ioDispatcher) {
            runCatching {
                val info = api.getUserInfo(username)
                val base64 = info.picture?.takeIf { info.hasPicture && it.isNotBlank() }
                if (base64 == null) {
                    knownEmpty.add(username)
                    return@runCatching null
                }
                val file = File(avatarDir(), cacheKey(username, base64))
                val bytes = if (file.exists()) {
                    file.readBytes()
                } else {
                    Base64.decode(base64, Base64.DEFAULT).also { file.writeBytes(it) }
                }
                memory[username] = bytes
                bytes
            }.getOrNull()
        }
    }

    private fun avatarDir(): File = File(context.cacheDir, "avatars").apply { mkdirs() }

    private fun cacheKey(username: String, base64: String): String {
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(username.toByteArray())
            update(base64.toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
