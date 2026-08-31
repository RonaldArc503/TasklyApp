package com.ronaldcolocho.taskly.di

import com.ronaldcolocho.taskly.audio.AudioPlayerController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioPlayerControllerEntryPoint {
    fun audioPlayerController(): AudioPlayerController
}
