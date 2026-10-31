package com.luna.music.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {
    /**
     * MediaStore queries can traverse a large local library, so keep the scan
     * off the main thread to avoid freezing the Compose UI.
     */
    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val result = mutableListOf<Track>()

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val artwork = ContentUris.withAppendedId(
                        android.net.Uri.parse("content://media/external/audio/albumart"), albumId
                    )
                    result += Track(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown title",
                        artist = cursor.getString(artistCol) ?: "Unknown artist",
                        album = cursor.getString(albumCol) ?: "Unknown album",
                        uri = ContentUris.withAppendedId(collection, id),
                        artworkUri = if (albumId > 0) artwork else null
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission can be revoked while a scan is in progress.
        }
        result
    }
}
