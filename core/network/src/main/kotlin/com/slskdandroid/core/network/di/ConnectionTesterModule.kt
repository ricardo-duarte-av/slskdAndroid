package com.slskdandroid.core.network.di

import com.slskdandroid.core.network.ConnectionTester
import com.slskdandroid.core.network.SlskdConnectionTester
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the OkHttp-backed [SlskdConnectionTester] as the [ConnectionTester] consumers inject. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConnectionTesterModule {

    @Binds
    abstract fun bindConnectionTester(impl: SlskdConnectionTester): ConnectionTester
}
