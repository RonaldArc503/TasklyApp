package com.ronaldcolocho.taskly.ui.screen.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.saved.DeleteSavedItemUseCase
import com.ronaldcolocho.taskly.domain.usecase.saved.GetSavedItemsUseCase
import com.ronaldcolocho.taskly.domain.usecase.saved.GetSavedCountsUseCase
import com.ronaldcolocho.taskly.domain.usecase.saved.PinSavedItemUseCase
import com.ronaldcolocho.taskly.domain.util.SavedSearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SavedUiState {
    object Loading : SavedUiState
    data class Success(
        val items: List<SavedItem>,
        val pinnedCount: Int,
        val isEmptyTotal: Boolean,
        val filter: String,
        val query: String,
        val sort: SavedSort,
        val isRefreshing: Boolean = false
    ) : SavedUiState
}

enum class SavedSort(val label: String) {
    RECENT("Guardados recientes"),
    OLDEST("Guardados antiguos"),
    PINNED_FIRST("Fijados primero")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val getSavedItemsUseCase: GetSavedItemsUseCase,
    private val deleteSavedItemUseCase: DeleteSavedItemUseCase,
    private val pinSavedItemUseCase: PinSavedItemUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel() {

    private val _filter = MutableStateFlow("all")
    private val _query = MutableStateFlow("")
    private val _sort = MutableStateFlow(SavedSort.PINNED_FIRST)
    private val currentUserId = getCurrentUserIdUseCase()
    val uiState: StateFlow<SavedUiState> = if (currentUserId == null) {
        MutableStateFlow(SavedUiState.Success(emptyList(), 0, true, "all", "", SavedSort.PINNED_FIRST)).asStateFlow()
    } else {
        combine(
            getSavedItemsUseCase(currentUserId).onStart { emit(emptyList()) },
            _filter,
            _query,
            _sort
        ) { allItems, currentFilter, currentQuery, currentSort ->
            val filtered = allItems.asSequence()
                .filter { item ->
                    when (currentFilter) {
                        "messages" -> item.kind == "message"
                        "links" -> item.kind == "link"
                        "pinned" -> item.pinned
                        else -> true
                    }
                }
                .filter { item ->
                    currentQuery.isBlank() || savedSearchValues(item).any {
                        SavedSearchNormalizer.matches(it, currentQuery)
                    }
                }
                .toList()
            SavedUiState.Success(
                items = sortSavedItems(filtered, currentSort),
                pinnedCount = allItems.count(SavedItem::pinned),
                isEmptyTotal = allItems.isEmpty(),
                filter = currentFilter,
                query = currentQuery,
                sort = currentSort,
                isRefreshing = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SavedUiState.Success(emptyList(), 0, true, "all", "", SavedSort.PINNED_FIRST, isRefreshing = true)
        )
    }

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    fun setQuery(query: String) { _query.value = query }

    fun setSort(sort: SavedSort) { _sort.value = sort }

    fun deleteItem(id: String) {
        currentUserId?.let { uid ->
            viewModelScope.launch {
                deleteSavedItemUseCase(uid, id)
            }
        }
    }

    fun pinItem(id: String, pinned: Boolean) {
        currentUserId?.let { uid ->
            viewModelScope.launch {
                pinSavedItemUseCase(uid, id, pinned)
            }
        }
    }

}

private fun savedSearchValues(item: SavedItem): List<String> = buildList {
    add(item.text)
    addAll(item.links)
    add(item.convName)
    add(item.senderName)
    item.attachments.forEach { add(it.name) }
}

private fun sortSavedItems(items: List<SavedItem>, sort: SavedSort): List<SavedItem> = when (sort) {
    SavedSort.RECENT -> items.sortedByDescending(SavedItem::savedAt)
    SavedSort.OLDEST -> items.sortedBy(SavedItem::savedAt)
    SavedSort.PINNED_FIRST -> items.sortedWith(
        compareByDescending<SavedItem> { it.pinned }
            .thenByDescending { if (it.pinned) it.pinnedAt else it.savedAt }
            .thenByDescending(SavedItem::savedAt)
    )
}
