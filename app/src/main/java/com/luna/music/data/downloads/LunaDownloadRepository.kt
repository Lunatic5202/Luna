package com.luna.music.data.downloads

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Native, OS-managed downloads for direct audio files the user is authorized to save.
 *
 * Android DownloadManager owns the transfer lifecycle, so downloads survive process death and
 * device restarts. This repository only tracks IDs created by LUNA and never crawls provider pages.
 */
class LunaDownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class DownloadItem(
        val id: Long,
        val title: String,
        val fileName: String,
        val status: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val localUri: Uri?,
        val reason: Int
    ) {
        val progress: Float
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        val isFinished: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
        val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
        val isActive: Boolean get() = status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    }

    @Synchronized
    fun enqueue(source: Uri, title: String, requestedFileName: String? = null): Long {
        require(source.scheme.equals("https", ignoreCase = true)) {
            "Only direct HTTPS audio URLs are supported."
        }
        require(!source.host.isNullOrBlank()) { "The download URL is invalid." }

        val fileName = safeFileName(requestedFileName ?: source.lastPathSegment ?: title)
        val request = DownloadManager.Request(source)
            .setTitle(title.ifBlank { fileName })
            .setDescription("Downloading to Music")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)

        val id = manager.enqueue(request)
        preferences.edit().putString(KEY_PREFIX + id, "$title\n$fileName\n$source").apply()
        return id
    }

    @Synchronized
    fun getDownloads(): List<DownloadItem> {
        val ids = preferences.all.keys.mapNotNull { key ->
            if (key.startsWith(KEY_PREFIX)) key.removePrefix(KEY_PREFIX).toLongOrNull() else null
        }
        if (ids.isEmpty()) return emptyList()

        val query = DownloadManager.Query().setFilterById(*ids.toLongArray())
        val result = mutableListOf<DownloadItem>()
        manager.query(query)?.use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.toItem()
            }
        }
        return result.sortedByDescending { it.id }
    }

    @Synchronized
    fun cancel(id: Long) {
        manager.remove(id)
        preferences.edit().remove(KEY_PREFIX + id).apply()
    }

    /** Re-enqueues a failed transfer from the original HTTPS URL retained by LUNA. */
    @Synchronized
    fun retry(id: Long): Long? {
        val stored = preferences.getString(KEY_PREFIX + id, null) ?: return null
        val parts = stored.split('\n', limit = 3)
        if (parts.size != 3) return null
        val old = getDownloads().firstOrNull { it.id == id } ?: return null
        if (!old.isFailed) return null
        manager.remove(id)
        preferences.edit().remove(KEY_PREFIX + id).apply()
        return enqueue(Uri.parse(parts[2]), parts[0], parts[1])
    }

    private fun Cursor.toItem(): DownloadItem {
        val id = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
        val stored = preferences.getString(KEY_PREFIX + id, null).orEmpty().split('\n', limit = 3)
        val localIndex = getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val local = if (localIndex >= 0) getString(localIndex)?.let(Uri::parse) else null
        return DownloadItem(
            id = id,
            title = stored.getOrNull(0).orEmpty().ifBlank { "Audio download" },
            fileName = stored.getOrNull(1).orEmpty(),
            status = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
            downloadedBytes = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
            totalBytes = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            localUri = local,
            reason = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        )
    }

    private fun safeFileName(raw: String): String {
        val decoded = runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
        val cleaned = decoded.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim().trim('.')
            .take(100)
        require(cleaned.isNotBlank()) { "A valid file name is required." }
        val extension = cleaned.substringAfterLast('.', "")
        require(extension.lowercase() in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")) {
            "The file must use a supported audio extension."
        }
        return cleaned
    }

    companion object {
        private const val PREFERENCES = "luna_downloads"
        private const val KEY_PREFIX = "download_"
    }
}
