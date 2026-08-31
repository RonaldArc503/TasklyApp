package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.usecase.chat.GetConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed interface ChatListUiState {
    object Loading : ChatListUiState
    data class Success(val conversations: List<ChatConversation>) : ChatListUiState
    data class Error(val message: String) : ChatListUiState
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getConversationsUseCase: GetConversationsUseCase
) : ViewModel() {

    val currentUserId: String = getCurrentUserIdUseCase() ?: ""

    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        val uid = getCurrentUserIdUseCase()
        if (uid != null) {
            getConversationsUseCase(uid)
                .map { ChatListUiState.Success(it) as ChatListUiState }
                .catch { emit(ChatListUiState.Error(it.message ?: "Error")) }
                .onEach { _uiState.value = it }
                .launchIn(viewModelScope)
        } else {
            _uiState.value = ChatListUiState.Error("No session")
        }
    }
}

