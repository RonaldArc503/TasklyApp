package com.ronaldcolocho.taskly

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ronaldcolocho.taskly.di.MediaEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors

@HiltAndroidApp
class TasklyApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return EntryPointAccessors.fromApplication(this, MediaEntryPoint::class.java)
            .imageLoader()
    }
}