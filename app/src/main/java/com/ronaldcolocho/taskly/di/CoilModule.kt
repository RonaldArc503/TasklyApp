package com.ronaldcolocho.taskly.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        client: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .diskCache(
                DiskCache.Builder()
                    .directory(File(context.filesDir, "media_cache/coil"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            )
            .memoryCache(
                MemoryCache.Builder(context).maxSizePercent(0.25).build()
            )
            .crossfade(false)
            .build()
    }
}
