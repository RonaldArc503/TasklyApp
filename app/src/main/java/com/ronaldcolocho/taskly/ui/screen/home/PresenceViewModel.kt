package com.ronaldcolocho.taskly.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.usecase.chat.SetOfflineUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SetOnlineUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresenceViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val setOnlineUseCase: SetOnlineUseCase,
    private val setOfflineUseCase: SetOfflineUseCase
) : ViewModel() {

    private var job: Job? = null
    private var activeUserId: String? = null

    fun start() {
        val uid = getCurrentUserIdUseCase() ?: return
        if (activeUserId == uid && job?.isActive == true) return

        job?.cancel()
        activeUserId = uid
        setOnlineUseCase(uid)
        job = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                setOnlineUseCase(uid)
            }
        }
    }

    fun stop() {
        val uid = activeUserId ?: return
        job?.cancel()
        job = null
        activeUserId = null
        setOfflineUseCase(uid)
    }

    override fun onCleared() {
        stop()
    }
}
