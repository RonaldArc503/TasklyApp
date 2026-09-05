package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.usecase.chat.GetConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetOlderConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetRecentConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.ObserveNetworkUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatListUiState {
    object Loading : ChatListUiState
    data class Success(
        val conversations: List<ChatConversation>,
        val offline: Boolean,
        val isLoadingMore: Boolean,
        val hasMore: Boolean
    ) : ChatListUiState
    object Empty : ChatListUiState
    object OfflineEmpty : ChatListUiState
    data class Error(val message: String) : ChatListUiState
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getRecentConversationsUseCase: GetRecentConversationsUseCase,
    private val getOlderConversationsUseCase: GetOlderConversationsUseCase,
    observeNetworkUseCase: ObserveNetworkUseCase
) : ViewModel() {

    val currentUserId: String = getCurrentUserIdUseCase() ?: ""

    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    private val olderConversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    private val isLoadingMore = MutableStateFlow(false)
    private val hasMore = MutableStateFlow(true)

    init {
        val uid = getCurrentUserIdUseCase()
        if (uid != null) {
            combine(
                getRecentConversationsUseCase(uid, PAGE_SIZE),
                observeNetworkUseCase(),
                olderConversations,
                isLoadingMore,
                hasMore
            ) { recent, isOnline, older, loadingMore, canLoadMore ->
                val conversations = (recent + older).associateBy { it.id }.values
                    .sortedWith(compareByDescending<ChatConversation> { it.participantIds.size == 1 }
                        .thenByDescending { it.lastMessageAt })
                val offline = !isOnline
                when {
                    conversations.isNotEmpty() -> ChatListUiState.Success(conversations, offline, loadingMore, canLoadMore)
                    offline -> ChatListUiState.OfflineEmpty
                    else -> ChatListUiState.Empty
                }
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .catch { e ->
                // Un error de red no debe reemplazar datos ya mostrados.
                if (_uiState.value !is ChatListUiState.Success &&
                    _uiState.value !is ChatListUiState.Empty &&
                    _uiState.value !is ChatListUiState.OfflineEmpty
                ) {
                    _uiState.value = ChatListUiState.Error(e.message ?: "Error")
                }
            }
            .onEach { _uiState.value = it }
            .launchIn(viewModelScope)
        } else {
            _uiState.value = ChatListUiState.Error("No session")
        }
    }

    fun loadMore() {
        val uid = getCurrentUserIdUseCase() ?: return
        if (isLoadingMore.value || !hasMore.value) return
        val current = (uiState.value as? ChatListUiState.Success)?.conversations.orEmpty()
        val oldest = current.minOfOrNull { it.lastMessageAt } ?: return
        if (oldest <= 0L) {
            hasMore.value = false
            return
        }
        viewModelScope.launch {
            isLoadingMore.value = true
            try {
                val page = getOlderConversationsUseCase(uid, oldest, PAGE_SIZE)
                olderConversations.value = (olderConversations.value + page).associateBy { it.id }.values.toList()
                hasMore.value = page.size == PAGE_SIZE.toInt()
            } finally {
                isLoadingMore.value = false
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50L
    }
}
