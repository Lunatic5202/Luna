package com.luna.music

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luna.music.data.MusicRepository
import com.luna.music.data.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Holds the scanned library and top-level navigation so they survive configuration
 * changes (dark-mode toggle, font scale, locale, window resize) instead of being
 * rebuilt, which meant re-reading every file header on each recreation.
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MusicRepository(app)

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var showNowPlaying by mutableStateOf(false)
    internal var currentSection by mutableStateOf(LunaSection.Library)

    private var scanJob: Job? = null
    private var scanned = false
    private var scannedGeneration = NO_GENERATION
    private var scannedAt = 0L

    /**
     * Rescans the library, but only when it may have changed: MediaStore's generation
     * counter (Android 11+) moves when files are added or removed. Older devices fall back
     * to a short time-based throttle. Safe to call on every resume.
     */
    fun refresh(force: Boolean = false) {
        if (scanJob?.isActive == true) return
        val generation = mediaStoreGeneration()
        val unchanged = if (generation != NO_GENERATION) {
            generation == scannedGeneration
        } else {
            SystemClock.elapsedRealtime() - scannedAt < FALLBACK_THROTTLE_MS
        }
        if (!force && scanned && unchanged) return

        scanJob = viewModelScope.launch {
            tracks = repository.loadTracks()
            scanned = true
            scannedGeneration = generation
            scannedAt = SystemClock.elapsedRealtime()
        }
    }

    private fun mediaStoreGeneration(): Long =
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                MediaStore.getGeneration(getApplication(), MediaStore.VOLUME_EXTERNAL)
            }.getOrDefault(NO_GENERATION)
        } else {
            NO_GENERATION
        }

    private companion object {
        const val NO_GENERATION = -1L
        const val FALLBACK_THROTTLE_MS = 30_000L
    }
}
