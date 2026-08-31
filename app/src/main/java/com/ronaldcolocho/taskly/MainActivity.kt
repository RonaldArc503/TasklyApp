package com.ronaldcolocho.taskly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ronaldcolocho.taskly.ui.navigation.AppNavigation
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TasklyTheme {
                AppNavigation()
            }
        }
    }
}
