package com.ronaldcolocho.taskly.domain.util

fun attachmentMediaUrl(url: String, trans: String): String {
    return url.replace(
        Regex("/(image|video|raw)/upload/"),
        "/$1/upload/$trans/"
    ).let { if (it == url) url else it }
}
