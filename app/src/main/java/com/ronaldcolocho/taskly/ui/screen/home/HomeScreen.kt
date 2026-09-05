package com.ronaldcolocho.taskly.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.ui.state.HomeUiState
import com.ronaldcolocho.taskly.ui.state.RecentItem

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            CircularProgressIndicator()
        }
        is HomeUiState.Error -> {
            Text(text = state.message, color = MaterialTheme.colorScheme.error)
        }
        is HomeUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Hola, ",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                item {
                    Text(
                        text = "Actividad Reciente",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                items(state.recentActivity) { recent ->
                    when (recent) {
                        is RecentItem.TaskItem -> {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    text = "Tarea: ",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        is RecentItem.ChatItem -> {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    text = "Chat:  - ",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tareas Pendientes ()",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(state.pendingTasks) { task ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = task.title,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
