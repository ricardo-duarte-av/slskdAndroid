package com.slskdandroid.core.data

/**
 * Supplies peer profile pictures (avatars), fetched from the same `users/{username}/info` endpoint
 * the Users/Info screen uses. Results are cached on-device so list scrolling and repeated lookups
 * don't re-hit the network or re-decode.
 */
interface AvatarRepository {

    /**
     * The raw image bytes of [username]'s avatar, or null if they have none (or it couldn't be
     * fetched). Served from an in-memory/disk cache when available.
     */
    suspend fun getAvatar(username: String): ByteArray?
}
