package com.ronaldcolocho.taskly.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import android.os.Bundle
import com.ronaldcolocho.taskly.domain.model.OnlineMusicSource
import kotlinx.coroutines.*

/** Owned by the playback service so radio continues with the player screen closed. */
internal class RelatedPlaybackQueue(
    private val player: Player,
    private val scope: CoroutineScope,
    private val load: suspend (AudioTrack) -> List<AudioTrack>,
    private val mediaItem: (AudioTrack) -> MediaItem = { it.toPlaybackMediaItem() }
) : Player.Listener {
    private var generation = 0
    private var job: Job? = null
    private val attempted = linkedSetOf<String>()

    private fun status(loading: Boolean, error: String? = null) {
        val previous = player.playlistMetadata
        if ((previous.extras?.getBoolean("tasklyRadioLoading") ?: false) == loading &&
            previous.extras?.getString("tasklyRadioError") == error) return
        val extras = Bundle(previous.extras ?: Bundle()).apply {
            putBoolean("tasklyRadioLoading", loading); putString("tasklyRadioError", error)
        }
        // Media3 ignores extras values in metadata equality; a meaningful subtitle publishes the change.
        player.setPlaylistMetadata(previous.buildUpon()
            .setSubtitle(if (loading) "Buscando canciones similares…" else error).setExtras(extras).build())
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
            generation++
            attempted.clear()
            job?.cancel()
            status(false)
        }
    }

    override fun onEvents(player: Player, events: Player.Events) = update()

    private fun eligible(track: AudioTrack?) = track != null && track.relatedAutoplay &&
        track.playlistId == null && OnlineMusicSource.youtubeId(track.url) != null

    private fun queue() = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }

    private fun update() {
        if (!scope.isActive) return
        if (!eligible(player.currentMediaItem?.toAudioTrack())) {
            job?.cancel()
            status(false)
            return
        }
        if (!player.playWhenReady || player.repeatMode == Player.REPEAT_MODE_ONE) {
            job?.cancel()
            status(false, player.playlistMetadata.extras?.getString("tasklyRadioError"))
            return
        }
        // A cancelled blocking extraction must finish before another network lookup starts.
        if (job?.isCompleted == false || player.mediaItemCount - player.currentMediaItemIndex > 2) return
        val snapshot = queue()
        val seed = snapshot.lastOrNull()?.toAudioTrack() ?: return
        if (!eligible(seed) || !attempted.add(seed.id)) return
        if (attempted.size > 200) attempted.remove(attempted.first())
        val requestGeneration = generation
        val request = scope.launch(start = CoroutineStart.LAZY) {
            var error: String? = null
            try {
                status(true)
                val recommendations = withTimeout(15_000) { load(seed) }
                if (requestGeneration != generation || !eligible(player.currentMediaItem?.toAudioTrack()) ||
                    queue().map { it.mediaId } != snapshot.map { it.mediaId }) return@launch
                val existing = snapshot.map { it.mediaId }.toSet()
                val next = recommendations.filter { it.id !in existing && it.playlistId == null && OnlineMusicSource.youtubeId(it.url) != null }
                    .distinctBy { it.id }.take(5).map { it.copy(relatedAutoplay = true) }
                if (next.isNotEmpty()) {
                    val ended = player.playbackState == Player.STATE_ENDED && player.playWhenReady
                    val nextIndex = player.mediaItemCount
                    player.addMediaItems(next.map(mediaItem))
                    // Resume only if the song ended while its recommendations were still loading.
                    if (ended) { player.seekToDefaultPosition(nextIndex); player.prepare(); player.play() }
                    if (player.currentMediaItemIndex > 50) player.removeMediaItems(0, player.currentMediaItemIndex - 20)
                } else error = "No hay canciones similares disponibles para continuar."
            } catch (_: TimeoutCancellationException) {
                error = "La consulta de canciones similares tardó demasiado."
            } catch (cancelled: CancellationException) {
                if (requestGeneration == generation) attempted.remove(seed.id)
                throw cancelled
            } catch (failure: Exception) {
                val httpStatus = Regex("HTTP (\\d{3})").find(failure.message.orEmpty())?.groupValues?.get(1).orEmpty()
                android.util.Log.w("MusicRadio", "Related lookup failed: ${failure.javaClass.simpleName}; HTTP=$httpStatus")
                error = "No se pudieron cargar canciones similares."
            } finally {
                if (requestGeneration == generation) status(false, error)
                job = null
            }
        }
        job = request
        request.invokeOnCompletion { scope.launch { update() } }
        request.start()
    }
}
