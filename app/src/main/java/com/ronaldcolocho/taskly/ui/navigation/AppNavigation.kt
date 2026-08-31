package com.ronaldcolocho.taskly.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.ui.screen.auth.AuthScreen
import com.ronaldcolocho.taskly.ui.screen.auth.AuthViewModel
import com.ronaldcolocho.taskly.ui.screen.chat.AudioPlayerBar
import com.ronaldcolocho.taskly.ui.screen.chat.ChatListScreen
import com.ronaldcolocho.taskly.ui.screen.chat.ChatScreen
import com.ronaldcolocho.taskly.ui.screen.chat.rememberAudioPlayerController
import com.ronaldcolocho.taskly.ui.screen.chat.rememberMediaDownloadManager
import com.ronaldcolocho.taskly.ui.screen.home.HomeScreen
import com.ronaldcolocho.taskly.ui.screen.home.NetworkViewModel
import com.ronaldcolocho.taskly.ui.screen.home.PresenceViewModel
import com.ronaldcolocho.taskly.ui.screen.profile.ProfileScreen
import com.ronaldcolocho.taskly.ui.screen.tasks.TasksScreen
import com.ronaldcolocho.taskly.ui.state.AuthState

sealed class Route(val route: String) {
    object Auth : Route("auth")
    object Home : Route("home")
    object Tasks : Route("tasks")
    object ChatList : Route("chat_list")
    object ChatDetail : Route("chat/{convId}") {
        fun createRoute(convId: String) = "chat/$convId"
    }
    object Reminders : Route("reminders")
    object Profile : Route("profile")
}

@Composable
fun AppNavigation() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    when (authState) {
        AuthState.Uninitialized -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthState.LoggedOut -> {
            AuthScreen()
        }
        AuthState.LoggedIn -> {
            MainScaffold()
        }
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val audioController = rememberAudioPlayerController()
    val mediaManager = rememberMediaDownloadManager()
    val presenceViewModel: PresenceViewModel = hiltViewModel()
    val networkViewModel: NetworkViewModel = hiltViewModel()
    val isOnline by networkViewModel.isOnline.collectAsState()

    LaunchedEffect(Unit) {
        audioController.setTrackResolver { track -> mediaManager.fileFor(track.id, MediaKind.AUDIO) }
        audioController.setEnsureTrack { track ->
            mediaManager.awaitLocalFile(track.id, track.url, MediaKind.AUDIO)
        }
    }

    DisposableEffect(Unit) {
        presenceViewModel.start()
        onDispose { presenceViewModel.stop() }
    }

    val isBottomBarVisible = currentDestination?.route in listOf(
        Route.Home.route, Route.Tasks.route, Route.ChatList.route, Route.Reminders.route, Route.Profile.route
    )

    Scaffold(
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar {
                    val items = listOf(
                        Triple(Route.Home, "Inicio", Icons.Filled.Home),
                        Triple(Route.Tasks, "Tareas", Icons.Filled.List),
                        Triple(Route.ChatList, "Chat", Icons.Filled.ChatBubble),
                        Triple(Route.Reminders, "Avisos", Icons.Filled.Notifications),
                        Triple(Route.Profile, "Perfil", Icons.Filled.Person)
                    )
                    items.forEach { (routeObj, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(imageVector = icon as ImageVector, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentDestination?.hierarchy?.any { it.route == routeObj.route } == true,
                            onClick = {
                                navController.navigate(routeObj.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            if (!isOnline) {
                OfflineBanner()
            }
            AudioPlayerBar(controller = audioController)
            NavHost(
                navController = navController,
                startDestination = Route.Home.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(Route.Home.route) { HomeScreen() }
                composable(Route.Tasks.route) { TasksScreen() }
                composable(Route.ChatList.route) { ChatListScreen(onNavigateToChat = { convId -> navController.navigate(Route.ChatDetail.createRoute(convId)) }) }
                composable(
                    route = Route.ChatDetail.route,
                    arguments = listOf(navArgument("convId") { type = NavType.StringType })
                ) {
                    ChatScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Route.Reminders.route) { /* RemindersScreen() */ }
                composable(Route.Profile.route) { ProfileScreen(onLogout = { }) }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
    ) {
        Text(
            text = "Sin conexión. Los cambios se sincronizarán cuando vuelvas a estar en línea.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}
