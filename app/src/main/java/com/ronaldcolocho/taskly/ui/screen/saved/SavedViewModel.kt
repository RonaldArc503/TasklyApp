package com.ronaldcolocho.taskly.ui.screen.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import com.ronaldcolocho.taskly.domain.usecase.saved.DeleteSavedItemUseCase
import com.ronaldcolocho.taskly.domain.usecase.saved.GetSavedItemsUseCase
import com.ronaldcolocho.taskly.domain.usecase.saved.GetSavedCountsUseCase
import com.ronaldcolocho.taskly.domain.usecase.saved.PinSavedItemUseCase
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
        val isRefreshing: Boolean = false
    ) : SavedUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val getSavedItemsUseCase: GetSavedItemsUseCase,
    private val getSavedCountsUseCase: GetSavedCountsUseCase,
    private val deleteSavedItemUseCase: DeleteSavedItemUseCase,
    private val pinSavedItemUseCase: PinSavedItemUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel() {

    private val _filter = MutableStateFlow("all")
    
    private val currentUserId = getCurrentUserIdUseCase()
    private val counts = MutableStateFlow(com.ronaldcolocho.taskly.domain.repository.SavedCounts(0, 0))

    val uiState: StateFlow<SavedUiState> = if (currentUserId == null) {
        MutableStateFlow(SavedUiState.Success(emptyList(), 0, true, "all")).asStateFlow()
    } else {
        combine(
            _filter.flatMapLatest { filter -> getSavedItemsUseCase(currentUserId, filter).onStart { emit(emptyList()) } },
            _filter,
            counts
        ) { visibleItems, currentFilter, summary ->
            SavedUiState.Success(
                items = visibleItems,
                pinnedCount = summary.pinned,
                isEmptyTotal = summary.total == 0,
                filter = currentFilter,
                isRefreshing = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SavedUiState.Success(emptyList(), 0, true, "all", isRefreshing = true)
        )
    }

    init { refreshCounts() }

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    fun deleteItem(id: String) {
        currentUserId?.let { uid ->
            viewModelScope.launch {
                deleteSavedItemUseCase(uid, id).onSuccess { refreshCounts() }
            }
        }
    }

    fun pinItem(id: String, pinned: Boolean) {
        currentUserId?.let { uid ->
            viewModelScope.launch {
                pinSavedItemUseCase(uid, id, pinned).onSuccess { refreshCounts() }
            }
        }
    }

    private fun refreshCounts() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            runCatching { getSavedCountsUseCase(uid) }.onSuccess { counts.value = it }
        }
    }
}
