package com.ronaldcolocho.taskly.ui.screen.converter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.YouTubeVideo
import com.ronaldcolocho.taskly.domain.usecase.youtube.SearchYouTubeVideosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConverterUiState(
    val query: String = "",
    val manualUrl: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchError: String? = null,
    val manualError: String? = null,
    val results: List<YouTubeVideo> = emptyList(),
    val iframeUrl: String = "",
    val selectedTitle: String = "",
    val selectedMediaLabel: String = "",
    val isManualUrlInputExpanded: Boolean = false
)

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val searchYouTubeVideosUseCase: SearchYouTubeVideosUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        if (newQuery.trim().isEmpty()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    hasSearched = false,
                    searchError = null,
                    isSearching = false
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = false) }
            delay(800)
            executeSearch(newQuery)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        val currentQuery = _uiState.value.query
        if (currentQuery.trim().isEmpty()) return
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = false) }
            executeSearch(currentQuery)
        }
    }

    private suspend fun executeSearch(query: String) {
        val result = searchYouTubeVideosUseCase(query)
        result.onSuccess { videos ->
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    results = videos,
                    searchError = null
                )
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    results = emptyList(),
                    searchError = error.message
                )
            }
        }
    }

    fun onManualUrlChange(newUrl: String) {
        _uiState.update { it.copy(manualUrl = newUrl) }
    }

    fun onSharedUrlReceived(url: String) {
        _uiState.update {
            it.copy(
                manualUrl = url,
                manualError = null,
                iframeUrl = "",
                selectedTitle = "",
                selectedMediaLabel = "",
                isManualUrlInputExpanded = true
            )
        }
    }

    fun setManualUrlInputExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isManualUrlInputExpanded = expanded) }
    }

    fun submitManualUrl() {
        val videoId = extractYouTubeVideoId(_uiState.value.manualUrl)
        if (videoId == null) {
            _uiState.update { it.copy(manualError = "Ingresa un enlace o ID de YouTube valido.") }
            return
        }
        _uiState.update {
            it.copy(
                manualError = null,
                selectedTitle = "",
                selectedMediaLabel = "Audio MP3 / Video MP4",
                iframeUrl = veviozUrl(videoId)
            )
        }
    }

    fun pickVideo(video: YouTubeVideo) {
        _uiState.update {
            it.copy(
                manualError = null,
                selectedTitle = video.title,
                manualUrl = video.url,
                selectedMediaLabel = "Audio MP3 / Video MP4",
                iframeUrl = veviozUrl(video.id)
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                iframeUrl = "",
                manualUrl = "",
                selectedTitle = "",
                selectedMediaLabel = "",
                manualError = null
            )
        }
    }

    private fun extractYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) return trimmed

        return runCatching {
            val uri = Uri.parse(trimmed)
            val host = uri.host.orEmpty()
            val pathSegments = uri.pathSegments
            val videoId = when {
                host.contains("youtu.be") -> uri.lastPathSegment
                host.contains("youtube.com") || host.contains("youtube-nocookie.com") -> {
                    uri.getQueryParameter("v")
                        ?: pathSegments.indexOf("shorts")
                            .takeIf { it >= 0 }
                            ?.let { pathSegments.getOrNull(it + 1) }
                        ?: pathSegments.indexOf("embed")
                            .takeIf { it >= 0 }
                            ?.let { pathSegments.getOrNull(it + 1) }
                }
                else -> null
            }
            videoId?.takeIf { Regex("^[a-zA-Z0-9_-]{11}$").matches(it) }
        }.getOrNull()
    }

    private fun veviozUrl(videoId: String): String = "https://api.vevioz.com/$videoId"
}
