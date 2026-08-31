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

    fun start() {
        val uid = getCurrentUserIdUseCase() ?: return
        setOnlineUseCase(uid)
        job?.cancel()
        job = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                setOnlineUseCase(uid)
            }
        }
    }

    fun stop() {
        job?.cancel()
        val uid = getCurrentUserIdUseCase()
        if (uid != null) setOfflineUseCase(uid)
    }
}
