package com.ronaldcolocho.taskly.ui.screen.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ReminderPermissionsCard(onRefresh: () -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    val refresh by rememberUpdatedState(onRefresh)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                revision++
                refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val rows = remember(revision) {
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val battery = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val channel = notifications.getNotificationChannel("reminders_alarm_channel")
        listOf(
            Triple("Notificaciones", if (notifications.areNotificationsEnabled()) "Habilitado" else "Deshabilitado", Settings.ACTION_APP_NOTIFICATION_SETTINGS),
            Triple("Canal de recordatorios", when {
                channel == null -> "Sin crear: se crea al llegar el primer aviso"
                channel.importance >= NotificationManager.IMPORTANCE_HIGH -> "Habilitado: prioridad alta"
                else -> "Bloqueado o con prioridad baja"
            }, Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS),
            Triple("Alarmas exactas", if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) "Habilitado" else "Deshabilitado: no se pueden programar alarmas", if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM else Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
            Triple("Pantalla completa", if (Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent()) "Habilitado" else "Deshabilitado", if (Build.VERSION.SDK_INT >= 34) Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT else Settings.ACTION_APP_NOTIFICATION_SETTINGS),
            Triple("Mostrar sobre otras apps", if (Settings.canDrawOverlays(context)) "Habilitado" else "Deshabilitado", Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Triple("Batería (recomendado)", if (battery.isIgnoringBatteryOptimizations(context.packageName)) "Sin optimización" else "Optimización activa", Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diagnóstico de recordatorios", style = MaterialTheme.typography.titleMedium)
            Text("Estado actual del sistema. Android controla estos permisos y puede revocarlos tras una actualizaciÃ³n o cambio del sistema.", style = MaterialTheme.typography.bodySmall)
            rows.forEach { (title, status, action) ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(title)
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        val intent = Intent(action).apply {
                            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS || action == Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(Settings.EXTRA_CHANNEL_ID, "reminders_alarm_channel")
                            } else if (action != Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            Toast.makeText(context, "No se pudo abrir este ajuste. Busca Taskly en los ajustes de Android.", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Ajustar") }
                }
            }
            Text("Con fecha y hora futuras se programa la alarma; los avisos sin hora no activan la pantalla. Si tu teléfono tiene permisos de inicio automático o ventanas en segundo plano, revísalos también en la información de Taskly.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { revision++; refresh() }) { Text("Volver a comprobar") }
        }
    }
}
