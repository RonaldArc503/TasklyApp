package com.ronaldcolocho.taskly.di

import com.ronaldcolocho.taskly.data.repository.AuthRepositoryImpl
import com.ronaldcolocho.taskly.data.repository.ChatRepositoryImpl
import com.ronaldcolocho.taskly.data.repository.PresenceRepositoryImpl
import com.ronaldcolocho.taskly.data.repository.ProfileRepositoryImpl
import com.ronaldcolocho.taskly.data.repository.SavedRepositoryImpl
import com.ronaldcolocho.taskly.data.repository.TaskRepositoryImpl
import com.ronaldcolocho.taskly.domain.repository.IAuthRepository
import com.ronaldcolocho.taskly.domain.repository.IChatRepository
import com.ronaldcolocho.taskly.domain.repository.IPresenceRepository
import com.ronaldcolocho.taskly.domain.repository.IProfileRepository
import com.ronaldcolocho.taskly.domain.repository.ISavedRepository
import com.ronaldcolocho.taskly.domain.repository.ITaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCloudinaryRepository(
        cloudinaryRepositoryImpl: com.ronaldcolocho.taskly.data.repository.CloudinaryRepositoryImpl
    ): com.ronaldcolocho.taskly.domain.repository.ICloudinaryRepository


    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        taskRepositoryImpl: TaskRepositoryImpl
    ): ITaskRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): IChatRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): IProfileRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): IAuthRepository

    @Binds
    @Singleton
    abstract fun bindSavedRepository(
        savedRepositoryImpl: SavedRepositoryImpl
    ): ISavedRepository

    @Binds
    @Singleton
    abstract fun bindPresenceRepository(
        presenceRepositoryImpl: PresenceRepositoryImpl
    ): IPresenceRepository
}

