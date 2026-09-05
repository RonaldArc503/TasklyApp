package com.ronaldcolocho.taskly

import android.app.Application
import coil.Coil
import com.ronaldcolocho.taskly.di.MediaEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors

@HiltAndroidApp
class TasklyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Instalar el ImageLoader antes de que se componga la primera pantalla,
        // evitando cachés divididas / re-descargas al crear el singleton por defecto.
        val loader = EntryPointAccessors.fromApplication(this, MediaEntryPoint::class.java)
            .imageLoader()
        Coil.setImageLoader(loader)
    }
}