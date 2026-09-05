package com.ronaldcolocho.taskly.data.remote.youtube

import com.google.gson.annotations.SerializedName

data class YouTubeSearchResponseDto(
    @SerializedName("items") val items: List<YouTubeVideoItemDto>?
)

data class YouTubeVideoItemDto(
    @SerializedName("id") val id: YouTubeVideoIdDto?,
    @SerializedName("snippet") val snippet: YouTubeSnippetDto?
)

data class YouTubeVideoIdDto(
    @SerializedName("videoId") val videoId: String?
)

data class YouTubeSnippetDto(
    @SerializedName("title") val title: String?,
    @SerializedName("channelTitle") val channelTitle: String?,
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnailsDto?
)

data class YouTubeThumbnailsDto(
    @SerializedName("medium") val medium: YouTubeThumbnailUrlDto?
)

data class YouTubeThumbnailUrlDto(
    @SerializedName("url") val url: String?
)
