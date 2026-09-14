package com.ronaldcolocho.taskly.ui.screen.tasks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.ui.state.TasksUiState
import com.ronaldcolocho.taskly.ui.theme.Amber400
import com.ronaldcolocho.taskly.ui.theme.Emerald500
import com.ronaldcolocho.taskly.ui.theme.Indigo600
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    initialTab: String? = null,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var newTaskTitle by remember { mutableStateOf("") }

    var selectedTaskForActions by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var editTaskTitleInput by remember { mutableStateOf("") }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(initialTab) {
        TaskStatus.entries.firstOrNull { it.name == initialTab }?.let(viewModel::setTab)
    }

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
                    // Contadores calculados eficientemente sin consultas adicionales
                    val todoCount = remember(state.allTasks) { state.allTasks.count { it.status == TaskStatus.TODO } }
                    val doingCount = remember(state.allTasks) { state.allTasks.count { it.status == TaskStatus.DOING } }
                    val doneCount = remember(state.allTasks) { state.allTasks.count { it.status == TaskStatus.DONE } }

                    // Identificación de tareas activas más antiguas
                    val activeTasks = remember(state.allTasks) {
                        state.allTasks.filter { it.status == TaskStatus.TODO || it.status == TaskStatus.DOING }
                    }
                    val oldestActiveTaskIds = remember(activeTasks) {
                        if (activeTasks.isEmpty()) emptySet()
                        else {
                            val countToHighlight = if (activeTasks.size >= 3) 2 else 1
                            activeTasks.sortedBy { it.createdAt }.take(countToHighlight).map { it.id }.toSet()
                        }
                    }

                    // Focus visual momentáneo y sutil que solo corre una vez al entrar
                    val focusAnimatable = remember { Animatable(0f) }
                    var hasAnimatedFocus by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        if (!hasAnimatedFocus && activeTasks.isNotEmpty()) {
                            delay(200)
                            focusAnimatable.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 400)
                            )
                            delay(2200)
                            focusAnimatable.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 800)
                            )
                            hasAnimatedFocus = true
                        }
                    }

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
                    
                    // Tabs con contadores integrados
                    TabRow(
                        selectedTabIndex = state.currentTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TaskTab(
                            title = "Por hacer",
                            count = todoCount,
                            status = TaskStatus.TODO,
                            isSelected = state.currentTab == TaskStatus.TODO,
                            onClick = { viewModel.setTab(TaskStatus.TODO) }
                        )
                        TaskTab(
                            title = "En progreso",
                            count = doingCount,
                            status = TaskStatus.DOING,
                            isSelected = state.currentTab == TaskStatus.DOING,
                            onClick = { viewModel.setTab(TaskStatus.DOING) }
                        )
                        TaskTab(
                            title = "Completas",
                            count = doneCount,
                            status = TaskStatus.DONE,
                            isSelected = state.currentTab == TaskStatus.DONE,
                            onClick = { viewModel.setTab(TaskStatus.DONE) }
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
                                                viewModel.moveTaskForward(task)
                                                false
                                            }
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                if (task.status != TaskStatus.TODO) {
                                                    viewModel.moveTaskBackward(task)
                                                }
                                                false
                                            }
                                            SwipeToDismissBoxValue.Settled -> false
                                        }
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = (task.status != TaskStatus.TODO),
                                    backgroundContent = {
                                        val direction = dismissState.dismissDirection
                                        val isForward = direction == SwipeToDismissBoxValue.StartToEnd
                                        val isBackward = direction == SwipeToDismissBoxValue.EndToStart

                                        val bgColor = when {
                                            isForward -> when (task.status) {
                                                TaskStatus.TODO -> Indigo600
                                                TaskStatus.DOING -> Emerald500
                                                TaskStatus.DONE -> Amber400
                                            }
                                            isBackward -> when (task.status) {
                                                TaskStatus.DOING -> Amber400
                                                TaskStatus.DONE -> Indigo600
                                                else -> Color.Transparent
                                            }
                                            else -> Color.Transparent
                                        }

                                        val icon = when {
                                            isForward -> when (task.status) {
                                                TaskStatus.TODO -> Icons.AutoMirrored.Filled.ArrowForward
                                                TaskStatus.DOING -> Icons.Default.Check
                                                TaskStatus.DONE -> Icons.Default.Refresh
                                            }
                                            isBackward -> when (task.status) {
                                                TaskStatus.DOING, TaskStatus.DONE -> Icons.AutoMirrored.Filled.ArrowBack
                                                else -> null
                                            }
                                            else -> null
                                        }

                                        val label = when {
                                            isForward -> when (task.status) {
                                                TaskStatus.TODO -> "En progreso"
                                                TaskStatus.DOING -> "Completar"
                                                TaskStatus.DONE -> "Reiniciar"
                                            }
                                            isBackward -> when (task.status) {
                                                TaskStatus.DOING -> "Por hacer"
                                                TaskStatus.DONE -> "En progreso"
                                                else -> ""
                                            }
                                            else -> ""
                                        }

                                        val alignment = if (isBackward) Alignment.CenterEnd else Alignment.CenterStart

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(bgColor)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = alignment
                                        ) {
                                            if (icon != null) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (isBackward) {
                                                        Text(
                                                            text = label,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Icon(icon, contentDescription = label, tint = Color.White)
                                                    } else {
                                                        Icon(icon, contentDescription = label, tint = Color.White)
                                                        Text(
                                                            text = label,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    content = {
                                        TaskCard(
                                            task = task,
                                            isOldestFocus = oldestActiveTaskIds.contains(task.id),
                                            focusAlpha = focusAnimatable.value,
                                            onLongClick = {
                                                selectedTaskForActions = task
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Modal de acciones para la tarea (Pulsación prolongada / Long Press)
        if (selectedTaskForActions != null) {
            val task = selectedTaskForActions!!
            val statusColor = when (task.status) {
                TaskStatus.TODO -> Amber400
                TaskStatus.DOING -> Indigo600
                TaskStatus.DONE -> Emerald500
            }
            val statusText = when (task.status) {
                TaskStatus.TODO -> "Por hacer"
                TaskStatus.DOING -> "En progreso"
                TaskStatus.DONE -> "Completada"
            }

            ModalBottomSheet(
                onDismissRequest = { selectedTaskForActions = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
                ) {
                    // Header con título de la tarea y badge de estado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // Opción: Editar
                    ListItem(
                        headlineContent = {
                            Text("Editar", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text("Modificar el título de la tarea")
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Indigo600.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar",
                                    tint = Indigo600,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val current = selectedTaskForActions
                                selectedTaskForActions = null
                                if (current != null) {
                                    taskToEdit = current
                                    editTaskTitleInput = current.title
                                }
                            }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Opción: Eliminar
                    ListItem(
                        headlineContent = {
                            Text(
                                "Eliminar",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = {
                            Text("Eliminar esta tarea de forma permanente")
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val current = selectedTaskForActions
                                selectedTaskForActions = null
                                if (current != null) {
                                    taskToDelete = current
                                }
                            }
                    )
                }
            }
        }

        // Diálogo para Editar Tarea
        if (taskToEdit != null) {
            val task = taskToEdit!!
            AlertDialog(
                onDismissRequest = { taskToEdit = null },
                icon = {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Indigo600)
                },
                title = {
                    Text("Editar tarea", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Modifica el título de la tarea:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editTaskTitleInput,
                            onValueChange = { editTaskTitleInput = it },
                            placeholder = { Text("Título de la tarea") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editTaskTitleInput.isNotBlank()) {
                                viewModel.editTask(task, editTaskTitleInput)
                                taskToEdit = null
                            }
                        },
                        enabled = editTaskTitleInput.isNotBlank()
                    ) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskToEdit = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo de Confirmación para Eliminar Tarea
        if (taskToDelete != null) {
            val task = taskToDelete!!
            AlertDialog(
                onDismissRequest = { taskToDelete = null },
                icon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                title = {
                    Text("¿Eliminar tarea?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text("¿Estás seguro de que deseas eliminar “${task.title}”? Esta acción no se puede deshacer.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val toDelete = taskToDelete
                            taskToDelete = null
                            if (toDelete != null) {
                                viewModel.deleteTask(toDelete) {
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
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun TaskTab(
    title: String,
    count: Int,
    status: TaskStatus,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (status) {
        TaskStatus.TODO -> Amber400
        TaskStatus.DOING -> Indigo600
        TaskStatus.DONE -> Emerald500
    }

    Tab(
        selected = isSelected,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = if (isSelected) statusColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) statusColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
