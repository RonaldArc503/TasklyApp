package com.ronaldcolocho.taskly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import coil.Coil
import com.ronaldcolocho.taskly.di.MediaEntryPoint
import com.ronaldcolocho.taskly.ui.navigation.AppNavigation
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Coil se configura tras el primer frame (fuera del hilo crítico de arranque).
            LaunchedEffect(Unit) {
                val loader = withContext(Dispatchers.Default) {
                    EntryPointAccessors.fromApplication(applicationContext, MediaEntryPoint::class.java)
                        .imageLoader()
                }
                Coil.setImageLoader(loader)
            }
            TasklyTheme {
                AppNavigation()
            }
        }
    }
}
