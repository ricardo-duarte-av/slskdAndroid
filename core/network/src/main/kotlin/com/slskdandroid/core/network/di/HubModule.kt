package com.slskdandroid.core.network.di

import com.slskdandroid.core.network.SearchHub
import com.slskdandroid.core.network.SlskdSearchHub
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the SignalR-backed [SlskdSearchHub] as the [SearchHub] consumers inject. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class HubModule {

    @Binds
    abstract fun bindSearchHub(impl: SlskdSearchHub): SearchHub
}
