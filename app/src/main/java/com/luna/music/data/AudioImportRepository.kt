package com.luna.music.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Copies audio selected from the system picker into Music/LUNA so MediaStore can index it
 * and the normal library scanner can discover it.
 */
class AudioImportRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    data class ImportResult(val imported: Int, val skipped: Int)

    suspend fun importAudio(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var skipped = 0

        for (uri in uris) {
            val displayName = queryDisplayName(uri)
            val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mimeType = resolver.getType(uri) ?: mimeTypeFor(extension)

            if (extension !in SUPPORTED_EXTENSIONS || mimeType?.startsWith("audio/") != true) {
                skipped++
                continue
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeDisplayName(displayName))
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/LUNA"
                )
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val destination = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (destination == null) {
                skipped++
                continue
            }

            val copied = runCatching {
                val input = resolver.openInputStream(uri) ?: error("Unable to read selected audio")
                val output = resolver.openOutputStream(destination, "w") ?: error("Unable to save selected audio")
                input.use { source -> output.use { target -> source.copyTo(target) } }
                resolver.update(
                    destination,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }.isSuccess

            if (copied) {
                imported++
            } else {
                resolver.delete(destination, null, null)
                skipped++
            }
        }

        ImportResult(imported, skipped)
    }

    private fun queryDisplayName(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "Imported audio.mp3"
    }

    private fun safeDisplayName(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .take(120)
            .ifBlank { "Imported audio.mp3" }

    private fun mimeTypeFor(extension: String): String? = when (extension) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "wav" -> "audio/wav"
        else -> null
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
    }
}
