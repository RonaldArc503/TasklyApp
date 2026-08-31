package com.ronaldcolocho.taskly.domain.model

data class Presence(
    val online: Boolean,
    val lastSeen: Long
) {
    companion object {
        const val ONLINE_WINDOW_MS = 90_000L
    }

    fun isOnlineNow(now: Long = System.currentTimeMillis()): Boolean =
        online && now - lastSeen <= ONLINE_WINDOW_MS
}
