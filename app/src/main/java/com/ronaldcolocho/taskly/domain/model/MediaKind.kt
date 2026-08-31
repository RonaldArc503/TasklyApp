package com.ronaldcolocho.taskly.domain.model

enum class MediaKind(val folder: String) {
    AUDIO("Audio"),
    IMAGE("Images"),
    VIDEO("Videos"),
    DOCUMENT("Documents"),
    OTHER("Other")
}
