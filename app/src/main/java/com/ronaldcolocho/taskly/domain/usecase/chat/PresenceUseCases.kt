package com.ronaldcolocho.taskly.domain.usecase.chat

import com.ronaldcolocho.taskly.domain.model.Presence
import com.ronaldcolocho.taskly.domain.repository.IPresenceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SubscribePresenceUseCase @Inject constructor(
    private val repository: IPresenceRepository
) {
    operator fun invoke(uid: String): Flow<Presence?> = repository.subscribePresence(uid)
}

class SubscribePresencesUseCase @Inject constructor(
    private val repository: IPresenceRepository
) {
    operator fun invoke(uids: Set<String>): Flow<Map<String, Presence>> =
        repository.subscribePresences(uids)
}

class SetOnlineUseCase @Inject constructor(
    private val repository: IPresenceRepository
) {
    operator fun invoke(uid: String) = repository.setOnline(uid)
}

class SetOfflineUseCase @Inject constructor(
    private val repository: IPresenceRepository
) {
    operator fun invoke(uid: String) = repository.setOffline(uid)
}
