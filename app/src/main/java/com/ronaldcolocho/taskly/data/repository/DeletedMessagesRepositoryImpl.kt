package com.ronaldcolocho.taskly.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ronaldcolocho.taskly.domain.repository.IDeletedMessagesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletedMessagesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IDeletedMessagesRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("deleted_messages_prefs", Context.MODE_PRIVATE)
    private val deletedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val writeMutex = Mutex()

    init {
        deletedIdsFlow.value = loadActiveIds()
    }

    override fun getDeletedMessageIds(): Flow<Set<String>> = deletedIdsFlow.asStateFlow()

    override suspend fun markAsDeletedForMe(messageId: String) {
        val active = withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val entries = loadEntries().toMutableMap()
                entries[messageId] = System.currentTimeMillis()
                persistActiveEntries(entries)
            }
        }
        deletedIdsFlow.value = active
    }

    private fun loadActiveIds(): Set<String> = persistActiveEntries(loadEntries())

    private fun loadEntries(): Map<String, Long> {
        val versioned = prefs.getStringSet(KEY_ENTRIES, null)
        if (versioned != null) {
            return versioned.mapNotNull { entry ->
                val separator = entry.indexOf(SEPARATOR)
                if (separator <= 0) null else entry.substring(separator + 1) to entry.substring(0, separator).toLongOrNull()
            }.mapNotNull { (id, timestamp) -> timestamp?.let { id to it } }.toMap()
        }

        // Existing installs used an unbounded plain set. Preserve visibility while
        // converting it once to expiring entries.
        val now = System.currentTimeMillis()
        return (prefs.getStringSet(LEGACY_KEY, emptySet()) ?: emptySet()).associateWith { now }
    }

    private fun persistActiveEntries(entries: Map<String, Long>): Set<String> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val active = entries.asSequence()
            .filter { (_, timestamp) -> timestamp >= cutoff }
            .sortedByDescending { (_, timestamp) -> timestamp }
            .take(MAX_ENTRIES)
            .toList()
        prefs.edit()
            .putStringSet(KEY_ENTRIES, active.map { (id, timestamp) -> "$timestamp$SEPARATOR$id" }.toSet())
            .remove(LEGACY_KEY)
            .apply()
        return active.mapTo(linkedSetOf()) { it.key }
    }

    private companion object {
        const val LEGACY_KEY = "deleted_ids"
        const val KEY_ENTRIES = "deleted_entries_v2"
        const val SEPARATOR = '|'
        const val MAX_ENTRIES = 2_000
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1_000
    }
}
