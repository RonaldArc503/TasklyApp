package com.ronaldcolocho.taskly.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.ronaldcolocho.taskly.domain.model.DownloadedSong
import java.io.File

object DownloadedSongResolver {
    private const val TAG = "DownloadedSongResolver"

    /**
     * Resolves the existing physical file for a [DownloadedSong] through multiple fallback strategies:
     * 1. Direct path in [DownloadedSong.localPath]
     * 2. Internal app directory: `context.filesDir/Music/<filename>`
     * 3. External app directory: `context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)/<filename>`
     * 4. Taskly internal audio cache: `context.filesDir/Taskly/Audio/` matching by exact file size
     *
     * When resolved from a cache or external directory, it makes an internal backup in
     * `context.filesDir/Music/<filename>` so it remains permanent across restarts and system cleans.
     */
    fun resolveSongFile(context: Context, song: DownloadedSong): File? {
        val direct = File(song.localPath)
        if (direct.exists() && direct.length() > 0) {
            return direct
        }

        val fileName = direct.name.ifBlank { "${song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.mp3" }

        // 2. Internal filesDir/Music
        val internalDir = File(context.filesDir, "Music")
        val internalFile = File(internalDir, fileName)
        if (internalFile.exists() && internalFile.length() > 0) {
            return internalFile
        }

        // 3. External filesDir DIRECTORY_MUSIC
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (externalDir != null) {
            val externalFile = File(externalDir, fileName)
            if (externalFile.exists() && externalFile.length() > 0) {
                runCatching {
                    if (!internalDir.exists()) internalDir.mkdirs()
                    externalFile.copyTo(internalFile, overwrite = true)
                }
                return externalFile
            }
        }

        // 4. Taskly internal audio cache (files/Taskly/Audio/)
        val audioDir = File(File(context.filesDir, "Taskly"), "Audio")
        if (audioDir.exists() && audioDir.isDirectory) {
            val candidates = audioDir.listFiles().orEmpty()

            // Match by file size (sizeBytes)
            if (song.sizeBytes > 0) {
                val matchBySize = candidates.firstOrNull { it.length() == song.sizeBytes }
                if (matchBySize != null && matchBySize.exists()) {
                    runCatching {
                        if (!internalDir.exists()) internalDir.mkdirs()
                        matchBySize.copyTo(internalFile, overwrite = true)
                        Log.d(TAG, "Restored song '${song.title}' from audio cache to ${internalFile.absolutePath}")
                    }
                    return matchBySize
                }
            }
        }

        return null
    }

    /**
     * Attempts to resolve an audio file for a given track, especially when its URL points to a file://
     * or when matching a downloaded song in the history.
     */
    fun resolveTrackFile(
        context: Context,
        trackUrl: String,
        trackName: String = "",
        trackId: String = "",
        downloadedSongs: List<DownloadedSong> = emptyList()
    ): File? {
        // 1. If trackUrl is a local file URI (e.g. file:///storage/...)
        if (trackUrl.startsWith("file://") || trackUrl.startsWith("/")) {
            val cleanPath = if (trackUrl.startsWith("file://")) android.net.Uri.parse(trackUrl).path.orEmpty() else trackUrl
            val f = File(cleanPath)
            if (f.exists() && f.length() > 0) return f

            val fileName = f.name
            val internalFile = File(File(context.filesDir, "Music"), fileName)
            if (internalFile.exists() && internalFile.length() > 0) return internalFile

            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            if (externalDir != null) {
                val externalFile = File(externalDir, fileName)
                if (externalFile.exists() && externalFile.length() > 0) return externalFile
            }
        }

        // 2. Match with downloaded history list by id or title
        val match = downloadedSongs.firstOrNull {
            (trackId.isNotBlank() && it.id == trackId) ||
                (trackName.isNotBlank() && it.title.equals(trackName, ignoreCase = true))
        }
        if (match != null) {
            val resolved = resolveSongFile(context, match)
            if (resolved != null && resolved.exists()) return resolved
        }

        // 3. Fallback: match by title or ID directly in filesDir/Music
        val internalMusicDir = File(context.filesDir, "Music")
        if (internalMusicDir.exists() && internalMusicDir.isDirectory) {
            val matchByName = internalMusicDir.listFiles()?.firstOrNull { file ->
                (trackName.isNotBlank() && file.nameWithoutExtension.equals(trackName, ignoreCase = true)) ||
                    (trackId.isNotBlank() && file.nameWithoutExtension == trackId)
            }
            if (matchByName != null && matchByName.exists() && matchByName.length() > 0) {
                return matchByName
            }
        }

        return null
    }
}
