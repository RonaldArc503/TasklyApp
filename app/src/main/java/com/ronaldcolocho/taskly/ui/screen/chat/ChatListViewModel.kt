package com.ronaldcolocho.taskly.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.ChatConversation
import com.ronaldcolocho.taskly.domain.model.Presence
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.usecase.chat.FindOrCreateDirectConversationUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.EnsureConversationSearchIndexesUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetArchivedConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetOlderConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetPinnedConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.GetRecentConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.ObserveNetworkUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SearchConversationsUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SetConversationArchivedUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SetConversationPinnedUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SubscribeConversationStatesUseCase
import com.ronaldcolocho.taskly.domain.usecase.chat.SubscribePresencesUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.GetProfileUseCase
import com.ronaldcolocho.taskly.domain.usecase.profile.SearchProfilesUseCase
import com.ronaldcolocho.taskly.domain.util.ChatSearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatListUiState {
    object Loading : ChatListUiState
    data class Success(
        val conversations: List<ChatConversation>,
        val offline: Boolean,
        val isLoadingMore: Boolean,
        val hasMore: Boolean,
        val presenceByUserId: Map<String, Presence> = emptyMap(),
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val showingArchived: Boolean = false,
        val archivedCount: Int = 0
    ) : ChatListUiState
    object Empty : ChatListUiState
    object OfflineEmpty : ChatListUiState
    data class Error(val message: String) : ChatListUiState
}

data class NewConversationUiState(
    val query: String = "",
    val results: List<UserProfile> = emptyList(),
    val isSearching: Boolean = false,
    val isOpening: Boolean = false,
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getRecentConversationsUseCase: GetRecentConversationsUseCase,
    private val getOlderConversationsUseCase: GetOlderConversationsUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val searchProfilesUseCase: SearchProfilesUseCase,
    private val findOrCreateDirectConversationUseCase: FindOrCreateDirectConversationUseCase,
    private val subscribePresencesUseCase: SubscribePresencesUseCase,
    private val subscribeConversationStatesUseCase: SubscribeConversationStatesUseCase,
    private val getPinnedConversationsUseCase: GetPinnedConversationsUseCase,
    private val getArchivedConversationsUseCase: GetArchivedConversationsUseCase,
    private val searchConversationsUseCase: SearchConversationsUseCase,
    private val ensureConversationSearchIndexesUseCase: EnsureConversationSearchIndexesUseCase,
    private val setConversationPinnedUseCase: SetConversationPinnedUseCase,
    private val setConversationArchivedUseCase: SetConversationArchivedUseCase,
    observeNetworkUseCase: ObserveNetworkUseCase
) : ViewModel() {

    val currentUserId: String = getCurrentUserIdUseCase() ?: ""

    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    private val olderConversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    private val isLoadingMore = MutableStateFlow(false)
    private val hasMore = MutableStateFlow(true)
    private val recentConversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    private val conversationStates = MutableStateFlow<Map<String, com.ronaldcolocho.taskly.domain.model.ConversationUserState>>(emptyMap())
    private val pinnedConversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    private val archivedConversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    private val archivedHasMore = MutableStateFlow(true)
    private val isOffline = MutableStateFlow(false)
    private val listSearchQuery = MutableStateFlow("")
    private val remoteSearchResults = MutableStateFlow<List<ChatConversation>>(emptyList())
    private val isSearchingConversations = MutableStateFlow(false)
    private val showingArchived = MutableStateFlow(false)
    private val visiblePresenceUserIds = MutableStateFlow<Set<String>>(emptySet())
    private val presenceByUserId = MutableStateFlow<Map<String, Presence>>(emptyMap())
    private val currentProfile = MutableStateFlow<UserProfile?>(null)
    private val _newConversation = MutableStateFlow(NewConversationUiState())
    val newConversation: StateFlow<NewConversationUiState> = _newConversation.asStateFlow()
    private val _openConversation = MutableStateFlow<String?>(null)
    val openConversation: StateFlow<String?> = _openConversation.asStateFlow()
    private var profileSearchJob: Job? = null
    private var conversationSearchJob: Job? = null
    private val indexedConversationIds = mutableSetOf<String>()

    init {
        val uid = getCurrentUserIdUseCase()
        if (uid != null) {
            getProfileUseCase(uid)
                .onEach { currentProfile.value = it }
                .catch { }
                .launchIn(viewModelScope)

            visiblePresenceUserIds
                .flatMapLatest(subscribePresencesUseCase::invoke)
                .catch { emit(emptyMap()) }
                .onEach { values ->
                    presenceByUserId.value = values
                    val current = _uiState.value
                    if (current is ChatListUiState.Success) {
                        _uiState.value = current.copy(presenceByUserId = values)
                    }
                }
                .launchIn(viewModelScope)

            getRecentConversationsUseCase(uid, PAGE_SIZE)
                .onEach { recentConversations.value = it }
                .catch { error -> if (_uiState.value !is ChatListUiState.Success) _uiState.value = ChatListUiState.Error(error.message ?: "Error") }
                .launchIn(viewModelScope)

            observeNetworkUseCase()
                .onEach { isOffline.value = !it }
                .launchIn(viewModelScope)

            combine(recentConversations, olderConversations) { recent, older ->
                (recent + older).map(ChatConversation::id).toSet()
            }
                .flatMapLatest { ids -> subscribeConversationStatesUseCase(uid, ids) }
                .catch { emit(emptyMap()) }
                .onEach { conversationStates.value = it }
                .launchIn(viewModelScope)

            combine(recentConversations, olderConversations) { recent, older ->
                (recent + older).associateBy(ChatConversation::id).values.toList()
            }.onEach { conversations ->
                val pending = conversations.filter { it.id !in indexedConversationIds }
                if (pending.isNotEmpty()) {
                    ensureConversationSearchIndexesUseCase(uid, pending).onSuccess {
                        indexedConversationIds += pending.map(ChatConversation::id)
                    }
                }
            }.catch { }.launchIn(viewModelScope)

            getPinnedConversationsUseCase(uid, MAX_PINNED_CONVERSATIONS)
                .catch { emit(emptyList()) }
                .onEach { pinnedConversations.value = it }
                .launchIn(viewModelScope)

            val dataInputs = combine(
                recentConversations,
                olderConversations,
                conversationStates,
                pinnedConversations
            ) { recent, older, states, pinned ->
                val loaded = (recent + older).associateBy(ChatConversation::id).values.map { conversation ->
                    val userState = states[conversation.id]
                    conversation.copy(
                        isPinned = userState?.pinned == true,
                        pinnedAt = userState?.pinnedAt ?: 0L,
                        isArchived = userState?.archived == true,
                        archivedAt = userState?.archivedAt ?: 0L
                    )
                }
                Pair(loaded, pinned)
            }
            val presentationInputs = combine(
                listSearchQuery,
                remoteSearchResults,
                isSearchingConversations,
                showingArchived,
                archivedConversations
            ) { query, remote, searching, archived, archivedItems ->
                PresentationInputs(query, remote, searching, archived, archivedItems)
            }
            val operationInputs = combine(isOffline, isLoadingMore, hasMore, archivedHasMore, showingArchived) {
                    offline, loading, mainMore, archiveMore, archived ->
                Triple(offline, loading, if (archived) archiveMore else mainMore)
            }

            combine(dataInputs, presentationInputs, operationInputs) { data, presentation, operations ->
                val (loaded, pinned) = data
                val (offline, loadingMore, canLoadMore) = operations
                val source = if (presentation.showingArchived) {
                    presentation.archivedItems
                } else {
                    (loaded + pinned).associateBy(ChatConversation::id).values.filterNot(ChatConversation::isArchived)
                }
                val normalizedQuery = ChatSearchNormalizer.normalize(presentation.query)
                val localMatches = if (normalizedQuery.isBlank()) source else source.filter { conversation ->
                    conversationSearchValues(conversation).any { ChatSearchNormalizer.matches(it, normalizedQuery) }
                }
                val conversations = if (normalizedQuery.isBlank() || presentation.showingArchived) {
                    localMatches
                } else {
                    (localMatches + presentation.remoteResults).associateBy(ChatConversation::id).values
                        .filterNot(ChatConversation::isArchived)
                }.sortedWith(
                    compareByDescending<ChatConversation> { !presentation.showingArchived && it.isPinned }
                        .thenByDescending { if (presentation.showingArchived) it.archivedAt else it.pinnedAt }
                        .thenByDescending(ChatConversation::lastMessageAt)
                )
                when {
                    conversations.isNotEmpty() -> ChatListUiState.Success(
                        conversations,
                        offline,
                        loadingMore,
                        canLoadMore,
                        presenceByUserId.value,
                        presentation.query,
                        presentation.searching,
                        presentation.showingArchived,
                        archivedConversations.value.size
                    )
                    presentation.query.isNotBlank() || presentation.showingArchived -> ChatListUiState.Success(
                        emptyList(), offline, loadingMore, canLoadMore, presenceByUserId.value,
                        presentation.query, presentation.searching, presentation.showingArchived,
                        archivedConversations.value.size
                    )
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

    fun onConversationSearchQueryChange(query: String) {
        listSearchQuery.value = query
        conversationSearchJob?.cancel()
        val normalized = ChatSearchNormalizer.normalize(query)
        if (normalized.length < 2 || showingArchived.value) {
            remoteSearchResults.value = emptyList()
            isSearchingConversations.value = false
            return
        }
        conversationSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            isSearchingConversations.value = true
            remoteSearchResults.value = runCatching {
                searchConversationsUseCase(currentUserId, normalized, SEARCH_RESULT_LIMIT)
            }.getOrDefault(emptyList())
            isSearchingConversations.value = false
        }
    }

    fun showArchived(show: Boolean) {
        showingArchived.value = show
        listSearchQuery.value = ""
        remoteSearchResults.value = emptyList()
        if (show && archivedConversations.value.isEmpty()) loadMoreArchived(reset = true)
    }

    fun togglePinned(conversation: ChatConversation) {
        viewModelScope.launch {
            setConversationPinnedUseCase(currentUserId, conversation.id, !conversation.isPinned)
        }
    }

    fun toggleArchived(conversation: ChatConversation) {
        viewModelScope.launch {
            val archive = !conversation.isArchived
            setConversationArchivedUseCase(currentUserId, conversation.id, archive).onSuccess {
                if (!archive) archivedConversations.value = archivedConversations.value.filterNot { it.id == conversation.id }
            }
        }
    }

    fun loadMoreArchived(reset: Boolean = false) {
        if (isLoadingMore.value || (!reset && !archivedHasMore.value)) return
        viewModelScope.launch {
            isLoadingMore.value = true
            try {
                val current = if (reset) emptyList() else archivedConversations.value
                val page = getArchivedConversationsUseCase(
                    currentUserId,
                    current.lastOrNull()?.archivedAt,
                    PAGE_SIZE
                )
                archivedConversations.value = (current + page).associateBy(ChatConversation::id).values
                    .sortedByDescending(ChatConversation::archivedAt)
                archivedHasMore.value = page.size == PAGE_SIZE.toInt()
            } catch (_: Exception) {
                // Una consulta remota puede fallar temporalmente (por ejemplo, mientras
                // Firestore termina de crear un índice). Conservamos lo ya mostrado y
                // evitamos que la coroutine de ViewModel cierre la aplicación.
                archivedHasMore.value = false
            } finally {
                isLoadingMore.value = false
            }
        }
    }

    fun setVisiblePresenceUserIds(userIds: Set<String>) {
        visiblePresenceUserIds.value = userIds.filterTo(linkedSetOf()) {
            it.isNotBlank() && it != currentUserId
        }
    }

    fun onNewConversationQueryChange(query: String) {
        _newConversation.value = _newConversation.value.copy(query = query, error = null)
        profileSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _newConversation.value = NewConversationUiState(query = query)
            return
        }
        profileSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _newConversation.value = _newConversation.value.copy(isSearching = true)
            searchProfilesUseCase(trimmed)
                .onSuccess { profiles ->
                    _newConversation.value = _newConversation.value.copy(
                        results = profiles.filterNot { it.uid == currentUserId },
                        isSearching = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    _newConversation.value = _newConversation.value.copy(
                        results = emptyList(),
                        isSearching = false,
                        error = error.message ?: "No se pudo buscar usuarios."
                    )
                }
        }
    }

    fun openDirectConversation(profile: UserProfile) {
        val me = currentProfile.value ?: run {
            _newConversation.value = _newConversation.value.copy(error = "Tu perfil aun no esta disponible.")
            return
        }
        if (_newConversation.value.isOpening) return
        viewModelScope.launch {
            _newConversation.value = _newConversation.value.copy(isOpening = true, error = null)
            findOrCreateDirectConversationUseCase(me, profile)
                .onSuccess { conversationId ->
                    _newConversation.value = NewConversationUiState()
                    _openConversation.value = conversationId
                }
                .onFailure { error ->
                    _newConversation.value = _newConversation.value.copy(
                        isOpening = false,
                        error = error.message ?: "No se pudo abrir la conversacion."
                    )
                }
        }
    }

    fun clearNewConversation() {
        profileSearchJob?.cancel()
        _newConversation.value = NewConversationUiState()
    }

    fun consumeOpenConversation(conversationId: String) {
        if (_openConversation.value == conversationId) _openConversation.value = null
    }

    fun loadMore() {
        if (showingArchived.value) {
            loadMoreArchived()
            return
        }
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
        const val SEARCH_DEBOUNCE_MS = 350L
        const val SEARCH_RESULT_LIMIT = 50L
        const val MAX_PINNED_CONVERSATIONS = 50L
    }
}

private data class PresentationInputs(
    val query: String,
    val remoteResults: List<ChatConversation>,
    val searching: Boolean,
    val showingArchived: Boolean,
    val archivedItems: List<ChatConversation>
)

private fun conversationSearchValues(conversation: ChatConversation): List<String> = buildList {
    conversation.name?.let(::add)
    conversation.members.values.forEach { member ->
        add(member.displayName)
        add(member.phone)
    }
}
