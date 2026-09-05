package com.ronaldcolocho.taskly.domain.model

data class AppSettings(
    val darkTheme: Boolean = true,
    val automaticDownloadsEnabled: Boolean = false
)
