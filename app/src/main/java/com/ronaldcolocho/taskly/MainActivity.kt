package com.ronaldcolocho.taskly

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronaldcolocho.taskly.ui.navigation.AppNavigation
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import com.ronaldcolocho.taskly.ui.screen.settings.SettingsViewModel
import com.ronaldcolocho.taskly.util.SharedUrlParser
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val sharedUrlViewModel: SharedUrlViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedUrl(intent)
        enableEdgeToEdge()
        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val sharedUrl by sharedUrlViewModel.sharedUrl.collectAsStateWithLifecycle()
            TasklyTheme(darkTheme = settings.darkTheme) {
                StartupPermissionRequester()
                AppNavigation(
                    sharedUrl = sharedUrl,
                    onSharedUrlHandled = sharedUrlViewModel::markHandled
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedUrl(intent)
    }

    private fun receiveSharedUrl(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/plain") != true) return
        SharedUrlParser.extract(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))?.let(
            sharedUrlViewModel::receive
        )
    }
}

@Composable
private fun StartupPermissionRequester() {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("startup_permissions", Context.MODE_PRIVATE)
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val fullScreenIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        requestOverlayPermissionIfNeeded(context, overlayLauncher)
    }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        requestFullScreenIntentOrOverlay(context, fullScreenIntentLauncher, overlayLauncher)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestExactAlarmThenFullScreenIntent(
            context,
            exactAlarmLauncher,
            fullScreenIntentLauncher,
            overlayLauncher
        )
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("requested", false)
        ) {
            preferences.edit().putBoolean("requested", true).apply()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestExactAlarmThenFullScreenIntent(
                context,
                exactAlarmLauncher,
                fullScreenIntentLauncher,
                overlayLauncher
            )
        }
    }
}

private fun requestExactAlarmThenFullScreenIntent(
    context: Context,
    exactAlarmLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    fullScreenIntentLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    overlayLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        requestFullScreenIntentOrOverlay(context, fullScreenIntentLauncher, overlayLauncher)
        return
    }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (!alarmManager.canScheduleExactAlarms()) {
        exactAlarmLauncher.launch(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    } else {
        requestFullScreenIntentOrOverlay(context, fullScreenIntentLauncher, overlayLauncher)
    }
}

private fun requestFullScreenIntentOrOverlay(
    context: Context,
    fullScreenIntentLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    overlayLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notifications.canUseFullScreenIntent()) {
            fullScreenIntentLauncher.launch(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
            return
        }
    }
    requestOverlayPermissionIfNeeded(context, overlayLauncher)
}

private fun requestOverlayPermissionIfNeeded(
    context: Context,
    overlayLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        overlayLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
}
