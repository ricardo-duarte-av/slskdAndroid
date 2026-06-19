package com.slskdandroid.core.data

import com.slskdandroid.core.network.SearchHub
import com.slskdandroid.core.network.SearchHubEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A fake [SearchHub] the test drives by hand: [emit] pushes a hub event to whoever is collecting
 * [events]. The buffer means [emit] never suspends or drops, even if emitted between awaited items.
 */
class FakeSearchHub : SearchHub {
    private val events = MutableSharedFlow<SearchHubEvent>(extraBufferCapacity = 64)

    override fun events(): Flow<SearchHubEvent> = events

    suspend fun emit(event: SearchHubEvent) = events.emit(event)
}
