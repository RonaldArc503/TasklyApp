package com.ronaldcolocho.taskly.audio

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ronaldcolocho.taskly.domain.model.OnlineMusicSource

internal fun AudioTrack.toPlaybackMediaItem(uri: Uri = Uri.parse(url)): MediaItem = MediaItem.Builder()
    .setMediaId(id).setUri(uri).setCustomCacheKey(OnlineMusicSource.cacheKey(url))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(name).setArtist(artist)
        .setArtworkUri(thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
        .setExtras(Bundle().apply {
            putString("messageId", msgId); putString("playlistId", playlistId); putString("originalUrl", url)
            putInt("durationSeconds", durationSeconds)
            putString("sourceChannel", sourceChannel)
            putBoolean("relatedAutoplay", relatedAutoplay && playlistId == null)
        }).build()).build()

internal fun MediaItem.toAudioTrack(known: AudioTrack? = null): AudioTrack {
    val metadata = mediaMetadata
    val extras = metadata.extras
    val ownMetadata = extras?.containsKey("originalUrl") == true
    return AudioTrack(mediaId,
        extras?.getString("originalUrl") ?: known?.url ?: localConfiguration?.uri?.toString().orEmpty(),
        metadata.title?.toString() ?: known?.name.orEmpty(), extras?.getString("messageId") ?: known?.msgId.orEmpty(),
        extras?.getInt("durationSeconds", known?.durationSeconds ?: 0) ?: known?.durationSeconds ?: 0,
        if (ownMetadata) metadata.artworkUri?.toString().orEmpty() else metadata.artworkUri?.toString() ?: known?.thumbnailUrl.orEmpty(),
        if (ownMetadata) extras?.getString("playlistId") else known?.playlistId,
        metadata.artist?.toString() ?: known?.artist.orEmpty(),
        extras?.getBoolean("relatedAutoplay", known?.relatedAutoplay ?: false) ?: known?.relatedAutoplay ?: false,
        extras?.getString("sourceChannel") ?: known?.sourceChannel.orEmpty())
}
