package com.slskdandroid.core.data

import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.model.BrowseDirectory
import com.slskdandroid.core.model.SearchResultFile
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.NetworkDirectory
import com.slskdandroid.core.network.model.NetworkFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import kotlin.math.roundToInt

internal class DefaultBrowseRepository @Inject constructor(
    private val api: SlskdApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BrowseRepository {

    override fun browse(username: String): Flow<BrowseProgress> = channelFlow {
        send(BrowseProgress.Loading(percent = null))

        // The browse GET blocks until the whole share arrives; meanwhile poll the status endpoint
        // (a bare percent, 404 before it starts / after it ends) to surface progress.
        val browse = async { api.getBrowse(username) }
        while (browse.isActive) {
            runCatching { api.getBrowseStatus(username) }.getOrNull()?.let { percent ->
                trySend(BrowseProgress.Loading(percent.coerceIn(0.0, 100.0).roundToInt()))
            }
            delay(POLL_INTERVAL_MS)
        }

        val response = browse.await()
        val directories =
            response.directories.toBrowseDirectories(locked = false) +
                response.lockedDirectories.toBrowseDirectories(locked = true)
        send(BrowseProgress.Loaded(directories))
        close()

        awaitClose()
    }.flowOn(ioDispatcher)
}

private fun List<NetworkDirectory>.toBrowseDirectories(locked: Boolean): List<BrowseDirectory> =
    filter { it.files.isNotEmpty() }.map { dir ->
        val separator = if (dir.name.contains('\\')) "\\" else "/"
        BrowseDirectory(
            directory = dir.name,
            files = dir.files.map { it.toResultFile(dir.name, separator, locked) },
        )
    }

private fun NetworkFile.toResultFile(directory: String, separator: String, locked: Boolean) =
    SearchResultFile(
        filename = "$directory$separator$filename",
        sizeBytes = size,
        bitRate = bitRate,
        lengthSeconds = length,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        isVariableBitRate = isVariableBitRate,
        extension = extension,
        isLocked = isLocked || locked,
    )

private const val POLL_INTERVAL_MS = 400L
