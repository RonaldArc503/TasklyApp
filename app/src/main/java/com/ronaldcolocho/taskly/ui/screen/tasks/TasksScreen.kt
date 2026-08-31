package com.ronaldcolocho.taskly.ui.screen.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.ui.state.TasksUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var newTaskTitle by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            is TasksUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TasksUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is TasksUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Progress Bar
                    val animatedProgress by animateFloatAsState(targetValue = state.progress, label = "progress")
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    // Tabs
                    TabRow(
                        selectedTabIndex = state.currentTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = state.currentTab == TaskStatus.TODO,
                            onClick = { viewModel.setTab(TaskStatus.TODO) },
                            text = { Text("Por hacer") }
                        )
                        Tab(
                            selected = state.currentTab == TaskStatus.DOING,
                            onClick = { viewModel.setTab(TaskStatus.DOING) },
                            text = { Text("En progreso") }
                        )
                        Tab(
                            selected = state.currentTab == TaskStatus.DONE,
                            onClick = { viewModel.setTab(TaskStatus.DONE) },
                            text = { Text("Completada") }
                        )
                    }

                    // Add Task input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            placeholder = { Text("¿Qué necesitas hacer?") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    viewModel.addTask(newTaskTitle) {
                                        newTaskTitle = ""
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task")
                        }
                    }

                    if (state.currentTab == TaskStatus.DONE && state.displayedTasks.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearDoneTasks() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Limpiar tareas completadas", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Task List
                    if (state.displayedTasks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No hay tareas en esta sección", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.displayedTasks, key = { it.id }) { task ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        when (dismissValue) {
                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                viewModel.moveTaskToNextStatus(task)
                                                false // Return false so it snaps back, UI updates based on state change
                                            }
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                viewModel.deleteTask(task) {
                                                    coroutineScope.launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = "Tarea eliminada",
                                                            actionLabel = "Deshacer",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            viewModel.restoreTask()
                                                        }
                                                    }
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        val color = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                            else -> Color.Transparent
                                        }
                                        val alignment = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                            else -> Alignment.Center
                                        }
                                        val icon = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                                            SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                                            else -> null
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(color)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = alignment
                                        ) {
                                            icon?.let {
                                                Icon(it, contentDescription = null, tint = Color.White)
                                            }
                                        }
                                    },
                                    content = {
                                        TaskCard(task = task)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
