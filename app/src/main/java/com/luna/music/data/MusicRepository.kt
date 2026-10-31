package com.luna.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MusicRepository(private val context: Context) {
    private val acceptedMimeTypes = setOf(
        "audio/mpeg", "audio/mp3", "audio/mp4", "audio/aac", "audio/flac",
        "audio/ogg", "audio/opus", "audio/wav", "audio/x-wav", "audio/3gpp",
        "audio/amr", "audio/alac", "audio/x-m4a"
    )

    /**
     * MediaStore's IS_MUSIC flag is only a first-pass hint. Verify the MIME,
     * size, duration, metadata and file signature before exposing a track.
     */
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
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 8192 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val result = mutableListOf<Track>()

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

                while (cursor.moveToNext()) {
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

                    val uri = ContentUris.withAppendedId(collection, id)
                    if (!hasRecognizedAudioSignature(uri, mime.orEmpty())) continue

                    val albumId = cursor.getLong(albumIdCol)
                    val artwork = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )
                    result += Track(
                        id = id,
                        title = title,
                        artist = artist.ifBlank { "Unknown artist" },
                        album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                        uri = uri,
                        artworkUri = if (albumId > 0L) artwork else null
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission may be revoked while the library is being scanned.
        } catch (_: IllegalArgumentException) {
            // Some OEM MediaStore providers expose incomplete audio columns.
        }
        result
    }

    private fun isGenericAudioLabel(value: String): Boolean {
        val normalized = value.substringBeforeLast('.', value)
            .trim()
            .lowercase(Locale.ROOT)
        return normalized.matches(Regex("audio[-_ ]?.{0,32}")) &&
            (normalized == "audio" || normalized.startsWith("audio-") || normalized.startsWith("audio_"))
    }

    private fun hasRecognizedAudioSignature(uri: Uri, mime: String): Boolean {
        return try {
            val header = context.contentResolver.openInputStream(uri)?.use { input ->
                ByteArray(16).also { buffer ->
                    var offset = 0
                    while (offset < buffer.size) {
                        val count = input.read(buffer, offset, buffer.size - offset)
                        if (count <= 0) break
                        offset += count
                    }
                }
            } ?: return false

            val ascii = header.toString(Charsets.ISO_8859_1)
            when (mime) {
                "audio/flac" -> ascii.startsWith("fLaC")
                "audio/ogg", "audio/opus" -> ascii.startsWith("OggS")
                "audio/wav", "audio/x-wav" -> ascii.startsWith("RIFF") && ascii.substring(8, 12) == "WAVE"
                "audio/mp4", "audio/x-m4a", "audio/alac" -> ascii.substring(4, 8) == "ftyp"
                "audio/aac" -> header.size >= 2 && header[0] == 0xFF.toByte() &&
                    (header[1].toInt() and 0xF6) == 0xF0
                "audio/mpeg", "audio/mp3" -> ascii.startsWith("ID3") ||
                    (header.size >= 2 && header[0] == 0xFF.toByte() &&
                        (header[1].toInt() and 0xE0) == 0xE0)
                "audio/3gpp", "audio/amr" -> ascii.startsWith("#!AMR") ||
                    ascii.substring(4, 8) == "ftyp"
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
