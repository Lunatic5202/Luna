package com.luna.music.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

class MusicRepository(private val context: Context) {
    private val acceptedMimeTypes = setOf(
        "audio/mpeg", "audio/mp3", "audio/mp4", "audio/aac", "audio/flac",
        "audio/ogg", "audio/opus", "audio/wav", "audio/x-wav", "audio/3gpp",
        "audio/amr", "audio/alac", "audio/x-m4a"
    )

    /** Uses MediaStore's indexed audio metadata to avoid opening every file during a scan. */
    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            // RELATIVE_PATH needs Android 10; older versions expose the full file path instead.
            if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH
            else @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 8192 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val result = mutableListOf<Track>()
        val coroutineContext = currentCoroutineContext()

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val pathCol = cursor.getColumnIndexOrThrow(
                    if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH
                    else @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
                )

                var scanned = 0
                while (cursor.moveToNext()) {
                    if (scanned++ % 64 == 0) coroutineContext.ensureActive()
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.trim().orEmpty()
                    val artist = cursor.getString(artistCol)?.trim().orEmpty()
                    val displayName = cursor.getString(nameCol)?.trim().orEmpty()
                    val mime = cursor.getString(mimeCol)?.lowercase(Locale.ROOT)?.substringBefore(';')
                    val size = cursor.getLong(sizeCol)
                    val duration = cursor.getLong(durationCol)

                    if (size <= 8192L || duration < 15_000L) continue
                    if (mime == null || mime !in acceptedMimeTypes) continue
                    if (title.isBlank() || isGenericAudioLabel(title) || isGenericAudioLabel(displayName)) continue

                    val albumId = cursor.getLong(albumIdCol)
                    val artwork = ContentUris.withAppendedId(
                        "content://media/external/audio/albumart".toUri(), albumId
                    )
                    result += Track(
                        id = id,
                        title = title,
                        artist = artist.ifBlank { "Unknown artist" },
                        album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                        uri = ContentUris.withAppendedId(collection, id),
                        artworkUri = if (albumId > 0L) artwork else null,
                        dateAdded = cursor.getLong(addedCol),
                        folder = folderName(cursor.getString(pathCol))
                    )
                }
            }
        } catch (error: SecurityException) {
            // Let the Activity distinguish revoked permission from an empty library.
            throw error
        } catch (error: IllegalArgumentException) {
            // Let the Activity show a retry state for incomplete OEM MediaStore providers.
            throw error
        }
        result
    }

    /** "Music/Rock/" (Android 10+) or "/storage/emulated/0/Music/Rock/x.mp3" (older) -> "Music/Rock". */
    private fun folderName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val path = if (raw.startsWith("/")) raw.substringBeforeLast('/') else raw
        return path.removePrefix("/storage/emulated/0/").trim('/').ifBlank { "Internal storage" }
    }

    private fun isGenericAudioLabel(value: String): Boolean {
        val normalized = value.substringBeforeLast('.', value)
            .trim()
            .lowercase(Locale.ROOT)
        // MediaStore sometimes exposes recordings/cache entries as AUD-... or AUDIO-...
        return normalized == "aud" || normalized == "audio" ||
            normalized.startsWith("aud-") || normalized.startsWith("aud_") ||
            normalized.startsWith("aud ") || normalized.startsWith("audio-") ||
            normalized.startsWith("audio_") || normalized.startsWith("audio ")
    }

}
