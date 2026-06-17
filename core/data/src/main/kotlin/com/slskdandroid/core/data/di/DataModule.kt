package com.slskdandroid.core.data.di

import com.slskdandroid.core.data.ConnectionSettingsRepository
import com.slskdandroid.core.data.DefaultConnectionSettingsRepository
import com.slskdandroid.core.data.DefaultSearchRepository
import com.slskdandroid.core.data.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    @Binds
    @Singleton
    fun bindsSearchRepository(impl: DefaultSearchRepository): SearchRepository

    @Binds
    @Singleton
    fun bindsConnectionSettingsRepository(
        impl: DefaultConnectionSettingsRepository,
    ): ConnectionSettingsRepository
}
