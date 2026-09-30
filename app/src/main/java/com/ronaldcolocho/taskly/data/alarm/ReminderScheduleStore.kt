package com.ronaldcolocho.taskly.data.alarm

import android.content.Context
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder
import com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small device-local mirror of future timed reminders.  It is deliberately
 * independent from Firestore so boot recovery can happen without a network
 * connection or a restored Firebase session.
 */
@Singleton
class ReminderScheduleStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun read(): List<Reminder> = runCatching {
        val values = JSONArray(preferences.getString(KEY_REMINDERS, "[]"))
        buildList {
            for (index in 0 until values.length()) {
                val value = values.getJSONObject(index)
                add(
                    Reminder(
                        id = value.getString("id"),
                        title = value.optString("title"),
                        dueDate = value.getLong("dueDate"),
                        hasTime = value.optBoolean("hasTime"),
                        repeatType = runCatching {
                            ReminderRepeatType.valueOf(value.optString("repeatType"))
                        }.getOrDefault(ReminderRepeatType.NONE),
                        snoozedUntil = value.optLong("snoozedUntil")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun replace(reminders: List<Reminder>) {
        val values = JSONArray()
        reminders.filter { !it.isCompleted && it.hasTime && it.dueDate > 0L }.forEach { reminder ->
            values.put(reminder.toJson())
        }
        // The phone may be powered off immediately after the user saves a reminder.
        // Commit before returning so boot recovery never observes a half-written agenda.
        preferences.edit().putString(KEY_REMINDERS, values.toString()).commit()
    }

    @Synchronized
    fun upsert(reminder: Reminder) {
        val values = read().filterNot { it.id == reminder.id }.toMutableList()
        if (!reminder.isCompleted && reminder.hasTime && reminder.dueDate > 0L) values += reminder
        replace(values)
    }

    @Synchronized
    fun remove(id: String) = replace(read().filterNot { it.id == id })

    private fun Reminder.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("dueDate", dueDate)
        put("hasTime", hasTime)
        put("repeatType", repeatType.name)
        put("snoozedUntil", snoozedUntil)
    }

    private companion object {
        const val PREFERENCES = "reminder_schedule"
        const val KEY_REMINDERS = "future_timed_reminders"
    }
}
