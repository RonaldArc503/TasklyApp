package com.ronaldcolocho.taskly.ui.screen.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.domain.model.Task
import com.ronaldcolocho.taskly.domain.model.TaskStatus
import com.ronaldcolocho.taskly.ui.theme.Amber400
import com.ronaldcolocho.taskly.ui.theme.Emerald500
import com.ronaldcolocho.taskly.ui.theme.Indigo600

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: Task,
    isOldestFocus: Boolean = false,
    focusAlpha: Float = 0f,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
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

    val isFocused = isOldestFocus && focusAlpha > 0.01f

    // Borde sutil ámbar durante el focus
    val focusBorderColor = if (isDark) {
        Color(0xFFF59E0B).copy(alpha = (focusAlpha * 0.9f).coerceIn(0f, 1f))
    } else {
        Color(0xFFD97706).copy(alpha = (focusAlpha * 0.85f).coerceIn(0f, 1f))
    }

    // Tinte suave de fondo durante el focus
    val cardBg = if (isFocused) {
        if (isDark) {
            Color(0xFF1E293B)
        } else {
            Color(0xFFFFFBEB).copy(alpha = (focusAlpha * 0.8f).coerceIn(0f, 1f))
        }
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) BorderStroke(1.5.dp, focusBorderColor) else null,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 3.dp else 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left color border
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(statusColor)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (isFocused) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDark) Color(0xFF78350F).copy(alpha = (focusAlpha * 0.8f).coerceIn(0f, 1f))
                                    else Color(0xFFFEF3C7).copy(alpha = (focusAlpha * 0.9f).coerceIn(0f, 1f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = (if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309)).copy(alpha = focusAlpha.coerceIn(0f, 1f)),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Más antigua",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = (if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309)).copy(alpha = focusAlpha.coerceIn(0f, 1f))
                                )
                            }
                        }
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
