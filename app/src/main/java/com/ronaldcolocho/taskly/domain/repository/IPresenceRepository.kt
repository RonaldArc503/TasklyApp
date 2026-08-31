package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.Presence
import kotlinx.coroutines.flow.Flow

interface IPresenceRepository {
    fun subscribePresence(uid: String): Flow<Presence?>
    fun setOnline(uid: String)
    fun setOffline(uid: String)
}
