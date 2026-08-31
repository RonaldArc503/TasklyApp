package com.ronaldcolocho.taskly.di

import coil.ImageLoader
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaEntryPoint {
    fun audioPlayerController(): AudioPlayerController
    fun mediaDownloadManager(): MediaDownloadManager
    fun imageLoader(): ImageLoader
}
