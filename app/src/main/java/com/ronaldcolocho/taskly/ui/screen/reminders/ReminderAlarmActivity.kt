package com.ronaldcolocho.taskly.ui.screen.reminders

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.domain.model.reminder.reminderTimeLabel
import com.ronaldcolocho.taskly.ui.theme.Indigo600
import com.ronaldcolocho.taskly.ui.theme.Slate500
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import com.ronaldcolocho.taskly.data.alarm.ReminderAlarmManager
import com.ronaldcolocho.taskly.domain.repository.ReminderRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderAlarmActivity : ComponentActivity() {
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var alarmManager: ReminderAlarmManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Tienes un recordatorio"
        val dueDate = intent.getLongExtra("REMINDER_DUE_DATE", 0L)
        val hasTime = intent.getBooleanExtra("REMINDER_HAS_TIME", false)
        val reminderId = intent.getStringExtra("REMINDER_ID").orEmpty()

        setContent {
            TasklyTheme {
                ReminderAlarmScreen(
                    title = title,
                    dueLabel = reminderTimeLabel(dueDate, hasTime),
                    onOpenReminders = {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("taskly://reminders?reminderId=$reminderId")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                        finish()
                    },
                    onSnooze = { minutes -> snooze(reminderId, minutes) },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun snooze(id: String, minutes: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return finish()
        lifecycleScope.launch {
            val reminder = repository.getReminder(uid, id) ?: return@launch finish()
            val until = System.currentTimeMillis() + minutes * 60_000L
            repository.updateReminder(uid, id, mapOf("snoozedUntil" to until))
            alarmManager.scheduleAlarms(listOf(reminder.copy(snoozedUntil = until)))
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }
}

@Composable
private fun ReminderAlarmScreen(
    title: String,
    dueLabel: String,
    onOpenReminders: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF111827), Color(0xFF312E81), Color(0xFF0F172A))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 12.dp,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(Indigo600.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Indigo600,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Recordatorio",
                    color = Indigo600,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    color = Color(0xFF111827),
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = dueLabel,
                    color = Slate500,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 30, 60).forEach { minutes ->
                        OutlinedButton(onClick = { onSnooze(minutes) }) { Text("${minutes}m") }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenReminders,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Ver aviso", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
