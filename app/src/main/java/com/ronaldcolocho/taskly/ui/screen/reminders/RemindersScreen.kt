package com.ronaldcolocho.taskly.ui.screen.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ronaldcolocho.taskly.domain.model.reminder.*
import com.ronaldcolocho.taskly.ui.theme.*
import com.ronaldcolocho.taskly.util.FileUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordatorios", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val state = uiState) {
            is RemindersUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RemindersUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ReminderHeaderStats(state.items)
                    }
                    
                    item {
                        AddReminderForm(
                            onAdd = { title, date, hasTime ->
                                viewModel.addReminder(title, date, hasTime)
                            },
                            error = state.error,
                            onClearError = { viewModel.clearError() }
                        )
                    }

                    if (state.items.isEmpty()) {
                        item {
                            EmptyRemindersState()
                        }
                    } else {
                        items(state.items, key = { it.id }) { reminder ->
                            ReminderCard(
                                item = reminder,
                                onUpdateDate = { newDate -> viewModel.updateDueDate(reminder.id, newDate) },
                                onRestart = { viewModel.updateDueDate(reminder.id, nextMonthDueDate(reminder.dueDate)) },
                                onUploadReceipt = { file, kind -> viewModel.uploadReceipt(reminder.id, file, kind) },
                                onRemoveReceipt = { r -> viewModel.removeReceipt(reminder.id, r) },
                                onLongClick = { editingReminder = reminder },
                                onDelete = { viewModel.deleteReminder(reminder.id) }
                            )
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }

    editingReminder?.let { reminder ->
        ReminderEditorDialog(
            reminder = reminder,
            onDismiss = { editingReminder = null },
            onSave = { title, dueDate, hasTime ->
                viewModel.updateReminder(reminder.id, title, dueDate, hasTime)
                editingReminder = null
            }
        )
    }
}

@Composable
fun ReminderHeaderStats(items: List<Reminder>) {
    val overdue = items.count { it.urgency == Urgency.OVERDUE }
    val red = items.count { it.urgency == Urgency.RED }
    val orange = items.count { it.urgency == Urgency.ORANGE }

    Column {
        Text("Lleva el control de tus pagos y sube los comprobantes.", color = Slate500, fontSize = 14.sp)
        if (overdue > 0 || red > 0 || orange > 0) {
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (overdue > 0) BadgeChip("$overdue vencidos", Color(0xFFFEE2E2), Color(0xFFB91C1C))
                if (red > 0) BadgeChip("$red por vencer", Color(0xFFFEE2E2), Color(0xFFB91C1C))
                if (orange > 0) BadgeChip("$orange próximos", Color(0xFFFFEDD5), Color(0xFFC2410C))
            }
        }
    }
}

@Composable
fun BadgeChip(text: String, bgColor: Color, textColor: Color) {
    Box(modifier = Modifier.clip(CircleShape).background(bgColor).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderForm(
    onAdd: (String, Long, Boolean) -> Unit,
    error: String?,
    onClearError: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(0L) }
    var hasTime by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableStateOf(9) }
    var selectedMinute by remember { mutableStateOf(0) }
    
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth, selectedHour, selectedMinute, 0)
            cal.set(Calendar.MILLISECOND, 0)
            dueDate = cal.timeInMillis
            onClearError()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            selectedHour = hourOfDay
            selectedMinute = minute
            hasTime = true
            val cal = Calendar.getInstance()
            if (dueDate > 0L) {
                cal.timeInMillis = dueDate
            }
            cal.set(Calendar.HOUR_OF_DAY, selectedHour)
            cal.set(Calendar.MINUTE, selectedMinute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            dueDate = cal.timeInMillis
            onClearError()
        },
        selectedHour,
        selectedMinute,
        false
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Texto a recordar") },
            placeholder = { Text("Ej. Recibo Impuesto Pagado") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        Text("La fecha es obligatoria. La hora y minuto son opcionales.", color = Slate500, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { datePickerDialog.show() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (dueDate > 0) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dueDate) else "Seleccionar fecha")
            }
            TextButton(
                onClick = {
                    val today = Calendar.getInstance()
                    today.set(Calendar.HOUR_OF_DAY, selectedHour)
                    today.set(Calendar.MINUTE, selectedMinute)
                    today.set(Calendar.SECOND, 0)
                    today.set(Calendar.MILLISECOND, 0)
                    dueDate = today.timeInMillis
                    onClearError()
                }
            ) {
                Text("Hoy")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { timePickerDialog.show() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (hasTime) SimpleDateFormat("h:mm a", Locale.getDefault()).format(dueDate) else "Agregar hora")
            }

            if (hasTime) {
                TextButton(
                    onClick = {
                        hasTime = false
                        selectedHour = 9
                        selectedMinute = 0
                        if (dueDate > 0L) {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = dueDate
                            cal.set(Calendar.HOUR_OF_DAY, selectedHour)
                            cal.set(Calendar.MINUTE, selectedMinute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            dueDate = cal.timeInMillis
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Quitar hora", fontSize = 12.sp)
                }
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                onAdd(title, dueDate, hasTime)
                if (title.isBlank() || dueDate <= 0L) return@Button
                title = ""
                dueDate = 0L
                hasTime = false
                selectedHour = 9
                selectedMinute = 0
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
        ) {
            Text("Agregar recordatorio", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReminderEditorDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onSave: (String, Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val initialCalendar = remember(reminder.id) {
        Calendar.getInstance().apply {
            if (reminder.dueDate > 0L) timeInMillis = reminder.dueDate
        }
    }
    var title by remember(reminder.id) { mutableStateOf(reminder.title) }
    var dueDate by remember(reminder.id) { mutableStateOf(reminder.dueDate) }
    var hasTime by remember(reminder.id) { mutableStateOf(reminder.hasTime) }
    var selectedHour by remember(reminder.id) { mutableStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember(reminder.id) { mutableStateOf(initialCalendar.get(Calendar.MINUTE)) }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, selectedHour, selectedMinute, 0)
                set(Calendar.MILLISECOND, 0)
            }
            dueDate = calendar.timeInMillis
        },
        initialCalendar.get(Calendar.YEAR),
        initialCalendar.get(Calendar.MONTH),
        initialCalendar.get(Calendar.DAY_OF_MONTH)
    )
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            selectedHour = hourOfDay
            selectedMinute = minute
            hasTime = true
            val calendar = Calendar.getInstance().apply {
                if (dueDate > 0L) timeInMillis = dueDate
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            dueDate = calendar.timeInMillis
        },
        selectedHour,
        selectedMinute,
        false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar recordatorio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Texto a recordar") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(reminderTimeLabel(dueDate, false))
                    }
                    TextButton(
                        onClick = {
                            val today = Calendar.getInstance()
                            today.set(Calendar.HOUR_OF_DAY, selectedHour)
                            today.set(Calendar.MINUTE, selectedMinute)
                            today.set(Calendar.SECOND, 0)
                            today.set(Calendar.MILLISECOND, 0)
                            dueDate = today.timeInMillis
                        }
                    ) {
                        Text("Hoy")
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { timePickerDialog.show() }) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (hasTime) SimpleDateFormat("h:mm a", Locale.getDefault()).format(dueDate) else "Agregar hora")
                    }
                    if (hasTime) {
                        TextButton(onClick = { hasTime = false }) {
                            Text("Quitar hora")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, dueDate, hasTime) },
                enabled = title.isNotBlank() && dueDate > 0L
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun EmptyRemindersState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = Indigo600, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No tienes recordatorios", fontWeight = FontWeight.Bold, color = Slate700)
        Text("Crea el primero arriba.", fontSize = 12.sp, color = Slate500)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderCard(
    item: Reminder,
    onUpdateDate: (Long) -> Unit,
    onRestart: () -> Unit,
    onUploadReceipt: (File, String) -> Unit,
    onRemoveReceipt: (Receipt) -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val urgencyText = remember(item.id, item.dueDate, item.hasTime) {
        urgencyLabel(item.dueDate, item.hasTime)
    }
    val dueDateText = remember(item.id, item.dueDate, item.hasTime) {
        reminderTimeLabel(item.dueDate, item.hasTime)
    }

    val (borderColor, bgColor, chipBg, chipText, dotColor) = when (item.urgency) {
        Urgency.OVERDUE -> listOf(Color(0xFFF87171), if(isDark) Color(0x1AEF4444) else Color(0xFFFEF2F2), Color(0xFFDC2626), Color.White, Color(0xFFEF4444))
        Urgency.RED -> listOf(Color(0xFFF87171), if(isDark) Color(0x1AEF4444) else Color(0xFFFEF2F2), Color(0xFFEF4444), Color.White, Color(0xFFEF4444))
        Urgency.ORANGE -> listOf(Color(0xFFFB923C), if(isDark) Color(0x1AF97316) else Color(0xFFFFF7ED), Color(0xFFF97316), Color.White, Color(0xFFF97316))
        Urgency.GREEN -> listOf(Color(0xFF34D399), if(isDark) Color(0x1A10B981) else Color(0xFFECFDF5), Color(0xFF059669), Color.White, Color(0xFF10B981))
        Urgency.NONE -> listOf(Slate200, if(isDark) Slate800 else Color.White, Slate500, Color.White, Slate400)
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            if (item.dueDate > 0L) {
                cal.timeInMillis = item.dueDate
            }
            val hour = if (item.hasTime) cal.get(Calendar.HOUR_OF_DAY) else 9
            val minute = if (item.hasTime) cal.get(Calendar.MINUTE) else 0
            cal.set(year, month, dayOfMonth, hour, minute, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onUpdateDate(cal.timeInMillis)
        },
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH),
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    )

    val coroutineScope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val file = FileUtil.getFileFromUri(context, uri)
                if (file != null) {
                    val kind = if (file.name.lowercase().endsWith(".pdf")) "pdf" else "image"
                    onUploadReceipt(file, kind)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Box(modifier = Modifier.padding(top = 6.dp, end = 12.dp).size(10.dp).clip(CircleShape).background(dotColor))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            BadgeChip(urgencyText, chipBg, chipText)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(dueDateText, fontSize = 11.sp)
                }
                
                OutlinedButton(
                    onClick = onRestart,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reiniciar", fontSize = 11.sp)
                }
                
                OutlinedButton(
                    onClick = { launcher.launch("*/*") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Comprobante", fontSize = 11.sp)
                }
            }

            if (item.receipts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.receipts.forEach { r ->
                        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Slate100)) {
                            if (r.kind == "image") {
                                AsyncImage(
                                    model = r.url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                }
                            }
                            IconButton(
                                onClick = { onRemoveReceipt(r) },
                                modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
        
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Slate400)
        }
    }
}
