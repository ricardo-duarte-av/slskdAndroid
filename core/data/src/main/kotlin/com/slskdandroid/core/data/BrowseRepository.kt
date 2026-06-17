package com.slskdandroid.core.data

import com.slskdandroid.core.model.BrowseDirectory
import kotlinx.coroutines.flow.Flow

/** Progress of a browse operation: a percentage while loading, then the result. */
sealed interface BrowseProgress {
    /** [percent] is null until the server reports a figure (404 while no browse is tracked). */
    data class Loading(val percent: Int?) : BrowseProgress
    data class Loaded(val directories: List<BrowseDirectory>) : BrowseProgress
}

/** Fetches a peer's shared files (its full browse listing). */
interface BrowseRepository {

    /**
     * Browses [username]'s entire share. Emits [BrowseProgress.Loading] updates (polled from the
     * server) while the share downloads, then a single [BrowseProgress.Loaded] with the directories
     * that contain files. Throws if the peer is offline / the browse fails.
     */
    fun browse(username: String): Flow<BrowseProgress>
}
