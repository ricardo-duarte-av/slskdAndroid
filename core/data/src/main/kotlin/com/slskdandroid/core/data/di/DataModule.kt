package com.slskdandroid.core.data.di

import com.slskdandroid.core.data.ConnectionSettingsRepository
import com.slskdandroid.core.data.DefaultConnectionSettingsRepository
import com.slskdandroid.core.data.AvatarRepository
import com.slskdandroid.core.data.BrowseRepository
import com.slskdandroid.core.data.ChatRepository
import com.slskdandroid.core.data.DefaultAvatarRepository
import com.slskdandroid.core.data.DefaultBrowseRepository
import com.slskdandroid.core.data.DefaultChatRepository
import com.slskdandroid.core.data.DefaultDownloadsRepository
import com.slskdandroid.core.data.DefaultRoomsRepository
import com.slskdandroid.core.data.DefaultSearchRepository
import com.slskdandroid.core.data.DefaultUploadsRepository
import com.slskdandroid.core.data.DefaultUsersRepository
import com.slskdandroid.core.data.DownloadsRepository
import com.slskdandroid.core.data.RoomsRepository
import com.slskdandroid.core.data.SearchRepository
import com.slskdandroid.core.data.UploadsRepository
import com.slskdandroid.core.data.UsersRepository
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
    fun bindsDownloadsRepository(impl: DefaultDownloadsRepository): DownloadsRepository

    @Binds
    @Singleton
    fun bindsUploadsRepository(impl: DefaultUploadsRepository): UploadsRepository

    @Binds
    @Singleton
    fun bindsBrowseRepository(impl: DefaultBrowseRepository): BrowseRepository

    @Binds
    @Singleton
    fun bindsUsersRepository(impl: DefaultUsersRepository): UsersRepository

    @Binds
    @Singleton
    fun bindsChatRepository(impl: DefaultChatRepository): ChatRepository

    @Binds
    @Singleton
    fun bindsAvatarRepository(impl: DefaultAvatarRepository): AvatarRepository

    @Binds
    @Singleton
    fun bindsRoomsRepository(impl: DefaultRoomsRepository): RoomsRepository

    @Binds
    @Singleton
    fun bindsConnectionSettingsRepository(
        impl: DefaultConnectionSettingsRepository,
    ): ConnectionSettingsRepository
}
