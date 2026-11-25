package com.ronaldcolocho.taskly.domain.util

import android.net.Uri

fun videoThumbnailUrl(url: String): String? {
    val uri = Uri.parse(url)
    val path = uri.encodedPath ?: return null
    if (uri.host != "res.cloudinary.com" || "/video/upload/" !in path) return null

    val framePath = path.substringBeforeLast('/') + "/" +
        path.substringAfterLast('/').substringBeforeLast('.') + ".jpg"
    return attachmentMediaUrl(uri.buildUpon().encodedPath(framePath).build().toString(), "w_600,c_limit,q_auto")
}

fun attachmentMediaUrl(url: String, trans: String): String {
    return url.replace(
        Regex("/(image|video|raw)/upload/"),
        "/$1/upload/$trans/"
    ).let { if (it == url) url else it }
}
