package com.ronaldcolocho.taskly.domain.model

sealed interface MediaDownloadState {
    data object NotDownloaded : MediaDownloadState
    data class Downloading(val progress: Float) : MediaDownloadState
    data object Downloaded : MediaDownloadState
    data class Error(val message: String) : MediaDownloadState
}
