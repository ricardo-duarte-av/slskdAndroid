package com.slskdandroid.core.data

import com.slskdandroid.core.model.UserProfile

/** Fetches a peer's aggregated profile (info + status + address). */
interface UsersRepository {

    /**
     * Loads [username]'s profile. The peer's `info` is required (throws if it can't be fetched —
     * e.g. the peer is offline); presence and address are best-effort and fall back to
     * unknown/null when their lookups fail.
     */
    suspend fun getUser(username: String): UserProfile
}
