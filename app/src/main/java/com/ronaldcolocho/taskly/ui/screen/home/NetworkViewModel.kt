package com.ronaldcolocho.taskly.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.usecase.chat.ObserveNetworkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(
    observeNetworkUseCase: ObserveNetworkUseCase
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = observeNetworkUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
}
