package com.ronaldcolocho.taskly.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ronaldcolocho.taskly.data.alarm.ReminderAlarmManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmManager: ReminderAlarmManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // The local agenda is deliberately authoritative at boot. Firestore can first
                // emit an empty cache while the device is offline; replacing the local agenda
                // with that value used to cancel every restored alarm until the app was opened.
                // The normal foreground sync reconciles cloud edits when it is available.
                alarmManager.rescheduleStoredAlarms()
            }
        }
    }
}
