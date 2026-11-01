package com.ronaldcolocho.taskly.audio

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ronaldcolocho.taskly.data.music.OnlineMusicRepository
import javax.inject.Inject
import android.app.PendingIntent
import android.content.Intent
import com.ronaldcolocho.taskly.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*

@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    @Inject lateinit var onlineMusic: OnlineMusicRepository
    private var mediaSession: MediaSession? = null
    private var streamCache: SimpleCache? = null
    private val radioScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val cache = SimpleCache(java.io.File(cacheDir, "music_streams"),
            LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024), StandaloneDatabaseProvider(this))
        streamCache = cache
        val upstream = DefaultHttpDataSource.Factory().setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
        val resolving = DataSource.Factory { MusicStreamDataSource(upstream.createDataSource(), onlineMusic) }
        val cached = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, cached)))
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handle audio focus automatically
            )
            .build()

        player.addListener(RelatedPlaybackQueue(player, radioScope, load = { seed ->
            withContext(Dispatchers.IO) {
                onlineMusic.related(seed.url).map { track ->
                    AudioTrack(track.id, track.url, track.name, track.sourceMessageId, track.durationSeconds,
                        track.thumbnailUrl, artist = track.artist, relatedAutoplay = true)
                }
            }
        }))
        
        val openCurrentAudio = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_CURRENT_AUDIO
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openCurrentAudio)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        radioScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        streamCache?.release()
        streamCache = null
        super.onDestroy()
    }
}
