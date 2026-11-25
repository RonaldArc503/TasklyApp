package com.ronaldcolocho.taskly.ui.screen.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.ui.theme.*
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ChatVideoDialog(
    item: LightboxItem,
    mediaManager: MediaDownloadManager,
    actions: MediaViewerActions = MediaViewerActions(),
    onClose: () -> Unit
) {
    val attachment = item.attachment
    var fullscreen by remember { mutableStateOf(false) }
    MediaViewerDialog(onDismiss = { if (fullscreen) fullscreen = false else onClose() }) {
        val context = LocalContext.current
        val player = remember(context, attachment) {
            ExoPlayer.Builder(context)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setSeekBackIncrementMs(10_000L)
                .setSeekForwardIncrementMs(10_000L)
                .build()
        }
        var playbackFailed by remember(player) { mutableStateOf(false) }

        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackFailed = true
                }
            }
            val cached = mediaManager.fileFor(attachment.publicId, MediaKind.VIDEO)
            val source = cached?.let(Uri::fromFile) ?: Uri.parse(attachment.url)
            player.addListener(listener)
            player.setMediaItem(MediaItem.fromUri(source))
            player.playWhenReady = true
            player.prepare()
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

        if (!fullscreen) MediaViewerHeader(item, actions, onClose)
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        useController = false
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            if (playbackFailed) {
                Column(
                    modifier = Modifier.align(Alignment.Center).background(Color.Black).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No se pudo reproducir el video.", color = Color.White)
                    Button(onClick = {
                        playbackFailed = false
                        player.prepare()
                        player.play()
                    }) {
                        Text("Reintentar")
                    }
                }
            }
            IconButton(
                onClick = { fullscreen = !fullscreen },
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    .background(Color.Black.copy(alpha = .7f), CircleShape)
            ) {
                Icon(
                    if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    if (fullscreen) "Salir de pantalla completa" else "Pantalla completa",
                    tint = Color.White
                )
            }
        }
        if (!fullscreen && item.caption.isNotBlank()) {
            Text(item.caption, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                color = ChatText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        VideoControls(player)
        if (!fullscreen) MediaViewerFooter(item, actions)
    }
}

@Composable
private fun VideoControls(player: Player) {
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var seeking by remember(player) { mutableStateOf<Float?>(null) }
    var playing by remember(player) { mutableStateOf(player.playWhenReady) }
    var seekable by remember(player) { mutableStateOf(false) }
    var muted by remember(player) { mutableStateOf(player.volume == 0f) }
    var speed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    var showSpeeds by remember { mutableStateOf(false) }
    val speeds = listOf(.5f, 1f, 1.25f, 1.5f, 2f)
    fun speedLabel(value: Float) = "${value.toString().removeSuffix(".0")}×"

    DisposableEffect(player) {
        fun updateControls() {
            duration = player.duration.coerceAtLeast(0L)
            position = player.currentPosition.coerceAtLeast(0L)
            playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
            seekable = player.isCurrentMediaItemSeekable && duration > 0L
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = updateControls()
        }
        player.addListener(listener)
        updateControls()
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            position = player.currentPosition.coerceAtLeast(0L)
            delay(180)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        val displayedPosition = seeking ?: position.toFloat()
        Slider(
            value = displayedPosition.coerceIn(0f, duration.toFloat()),
            onValueChange = { seeking = it },
            onValueChangeFinished = {
                seeking?.let {
                    player.seekTo(it.toLong())
                    position = it.toLong()
                }
                seeking = null
            },
            enabled = seekable,
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth().height(34.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(displayedPosition.toLong()), color = ChatMuted, style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(duration), color = ChatMuted, style = MaterialTheme.typography.labelMedium)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { showSpeeds = true }, modifier = Modifier.background(ChatSurfaceRaised, CircleShape)) {
                    Text(speedLabel(speed), color = ChatText, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = showSpeeds, onDismissRequest = { showSpeeds = false }) {
                    speeds.forEach { rate ->
                        DropdownMenuItem(text = { Text(speedLabel(rate)) }, onClick = {
                            speed = rate
                            player.setPlaybackSpeed(rate)
                            showSpeeds = false
                        })
                    }
                }
            }
            IconButton(onClick = player::seekBack, enabled = seekable) {
                Icon(Icons.Outlined.Replay10, "Retroceder 10 segundos", tint = ChatText)
            }
            IconButton(
                onClick = {
                    if (playing) player.pause() else {
                        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                        player.play()
                    }
                    playing = player.playWhenReady
                },
                modifier = Modifier.size(56.dp).clip(CircleShape).background(ChatActionGradient)
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "Pausar video" else "Reproducir video",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = player::seekForward, enabled = seekable) {
                Icon(Icons.Outlined.Forward10, "Avanzar 10 segundos", tint = ChatText)
            }
            IconButton(onClick = { muted = !muted; player.volume = if (muted) 0f else 1f }) {
                Icon(if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                    if (muted) "Activar sonido del video" else "Silenciar video", tint = ChatText)
            }
        }
    }
}
