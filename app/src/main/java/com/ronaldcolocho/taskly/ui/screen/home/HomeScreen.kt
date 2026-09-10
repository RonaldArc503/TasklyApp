package com.ronaldcolocho.taskly.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder
import com.ronaldcolocho.taskly.domain.model.reminder.urgencyLabel
import com.ronaldcolocho.taskly.ui.state.HomeUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// ROOT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToTasks: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToReminders: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is HomeUiState.Loading -> HomeLoadingState()
        is HomeUiState.Error  -> HomeErrorState(message = state.message)
        is HomeUiState.Success -> HomeContent(
            state = state,
            onNavigateToTasks = onNavigateToTasks,
            onNavigateToChat = onNavigateToChat,
            onNavigateToReminders = onNavigateToReminders
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOADING
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN CONTENT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onNavigateToTasks: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToReminders: () -> Unit
) {
    val firstName = state.user?.displayName?.split(" ")?.firstOrNull().orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {

        // 1 ── HEADER
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -16 }
            ) {
                HomeHeader(
                    firstName = firstName,
                    pendingTasksCount = state.pendingTasksCount,
                    unreadConversationsCount = state.unreadConversationsCount,
                    upcomingRemindersCount = state.upcomingReminders.size
                )
            }
        }

        // 2 ── TAREAS PENDIENTES
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300, delayMillis = 60)) + slideInVertically(tween(300, delayMillis = 60)) { 24 }
            ) {
                HomeSectionHeader(
                    title = "Pendientes",
                    count = state.pendingTasksCount,
                    actionLabel = if (state.pendingTasksCount > 0) "Ver todas" else null,
                    onAction = onNavigateToTasks
                )
            }
        }

        if (state.recentPendingTasks.isEmpty()) {
            item {
                HomeEmptyHint(
                    icon = Icons.Default.CheckCircleOutline,
                    text = "Todo al día · No tienes tareas pendientes"
                )
            }
        } else {
            itemsIndexed(
                items = state.recentPendingTasks,
                key = { _, task -> "task_${task.id}" }
            ) { index, task ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(220, delayMillis = 80 + index * 40)) +
                            slideInVertically(tween(220, delayMillis = 80 + index * 40)) { 20 }
                ) {
                    HomeTaskItem(task = task, onClick = onNavigateToTasks)
                }
            }
        }

        // 3 ── CHATS RECIENTES
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300, delayMillis = 120)) + slideInVertically(tween(300, delayMillis = 120)) { 24 }
            ) {
                HomeSectionHeader(
                    title = "Mensajes recientes",
                    count = null,
                    actionLabel = if (state.recentConversations.isNotEmpty()) "Ver todos" else null,
                    onAction = { /* no direct chat-list nav from here; kept intentionally blank */ }
                )
            }
        }

        if (state.recentConversations.isEmpty()) {
            item {
                HomeEmptyHint(
                    icon = Icons.Default.ChatBubbleOutline,
                    text = "Sin conversaciones recientes"
                )
            }
        } else {
            itemsIndexed(
                items = state.recentConversations,
                key = { _, chat -> "chat_${chat.id}" }
            ) { index, chat ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(220, delayMillis = 140 + index * 40)) +
                            slideInVertically(tween(220, delayMillis = 140 + index * 40)) { 20 }
                ) {
                    HomeConversationItem(
                        chat = chat,
                        onClick = { onNavigateToChat(chat.id) }
                    )
                }
                if (index < state.recentConversations.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }

        // 4 ── PRÓXIMOS RECORDATORIOS
        if (state.upcomingReminders.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300, delayMillis = 180)) + slideInVertically(tween(300, delayMillis = 180)) { 24 }
                ) {
                    HomeSectionHeader(
                        title = "Próximos recordatorios",
                        count = null,
                        actionLabel = "Ver todos",
                        onAction = onNavigateToReminders
                    )
                }
            }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(280, delayMillis = 200))
                ) {
                    HomeRemindersCard(
                        reminders = state.upcomingReminders,
                        onClick = onNavigateToReminders
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(
    firstName: String,
    pendingTasksCount: Int,
    unreadConversationsCount: Int,
    upcomingRemindersCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Saludo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = if (firstName.isNotBlank()) "Hola, $firstName 👋" else "Hola 👋",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "¿Qué tienes pendiente hoy?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Avatar placeholder circular
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Chips de resumen rápido
        if (pendingTasksCount > 0 || unreadConversationsCount > 0 || upcomingRemindersCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (pendingTasksCount > 0) {
                    SummaryChip(
                        icon = Icons.Default.CheckBox,
                        label = "$pendingTasksCount pendiente${if (pendingTasksCount > 1) "s" else ""}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (unreadConversationsCount > 0) {
                    SummaryChip(
                        icon = Icons.Default.ChatBubble,
                        label = "$unreadConversationsCount sin leer",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (upcomingRemindersCount > 0) {
                    SummaryChip(
                        icon = Icons.Default.Notifications,
                        label = "$upcomingRemindersCount aviso${if (upcomingRemindersCount > 1) "s" else ""}",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeSectionHeader(
    title: String,
    count: Int?,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (count != null && count > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (actionLabel != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TASK ITEM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeTaskItem(task: Task, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "task_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    pressed = true
                    onClick()
                }
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status dot
            val dotColor = when (task.status) {
                TaskStatus.TODO  -> MaterialTheme.colorScheme.error
                TaskStatus.DOING -> MaterialTheme.colorScheme.primary
                TaskStatus.DONE  -> MaterialTheme.colorScheme.secondary
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val statusLabel = when (task.status) {
                    TaskStatus.TODO  -> "Por hacer"
                    TaskStatus.DOING -> "En progreso"
                    TaskStatus.DONE  -> "Completa"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONVERSATION ITEM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeConversationItem(chat: ChatConversation, onClick: () -> Unit) {
    val unread = chat.unreadCount > 0
    val displayName = chat.name
        ?: chat.members.values.firstOrNull()?.displayName
        ?: "Chat"

    val timeStr = remember(chat.lastMessageAt) {
        if (chat.lastMessageAt > 0) {
            val cal = Calendar.getInstance()
            val msgCal = Calendar.getInstance().also { it.timeInMillis = chat.lastMessageAt }
            val isSameDay = cal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
            if (isSameDay)
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.lastMessageAt))
            else
                SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(chat.lastMessageAt))
        } else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (unread) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (chat.isGroup) Icons.Default.Group else Icons.Default.Person,
                contentDescription = null,
                tint = if (unread) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unread) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = chat.lastMessage.ifBlank { "Nuevo chat" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REMINDERS (agrupados en una sola card compacta)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeRemindersCard(reminders: List<Reminder>, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            reminders.forEachIndexed { index, reminder ->
                HomeReminderRow(reminder = reminder, onClick = onClick)
                if (index < reminders.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeReminderRow(reminder: Reminder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icono de campana pequeño
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Usa la función de presentación ya existente en el modelo
            val label = urgencyLabel(reminder.dueDate, reminder.hasTime)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EMPTY HINT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeEmptyHint(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
