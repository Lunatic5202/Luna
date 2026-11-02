package com.luna.music.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pauses playback after a delay, or at the end of the current track. A countdown fades the
 * volume out over a few seconds first so the music doesn't stop abruptly.
 *
 * Lives in the service process next to the player and is driven by the UI through [state].
 * Everything runs on the main thread, where ExoPlayer is created.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object LunaSleepTimer {
    sealed interface State {
        data object Off : State
        data class Countdown(val endsAtMillis: Long) : State
        data object EndOfTrack : State
    }

    private const val FADE_MILLIS = 6_000L
    private const val FADE_STEP_MILLIS = 200L

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var fadeStartedAt = 0L

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // The "end of track" pause has happened; the timer is done.
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _state.value == State.EndOfTrack
            ) {
                cancel()
            }
        }
    }

    private val startFade = Runnable {
        fadeStartedAt = System.currentTimeMillis()
        fadeStep.run()
    }

    private val fadeStep = object : Runnable {
        override fun run() {
            val exo = player ?: return
            val progress = ((System.currentTimeMillis() - fadeStartedAt) / FADE_MILLIS.toFloat()).coerceIn(0f, 1f)
            if (progress >= 1f) {
                exo.pause()
                exo.volume = 1f
                _state.value = State.Off
            } else {
                exo.volume = 1f - progress
                handler.postDelayed(this, FADE_STEP_MILLIS)
            }
        }
    }

    fun attach(exo: ExoPlayer) {
        player = exo
        exo.addListener(listener)
    }

    fun detach() {
        cancel()
        player?.removeListener(listener)
        player = null
    }

    /** Fade out and pause after [minutes]. Replaces any timer already running. */
    fun startCountdown(minutes: Int) {
        val exo = player ?: return
        cancel()
        val delay = minutes * 60_000L
        _state.value = State.Countdown(System.currentTimeMillis() + delay)
        handler.postDelayed(startFade, (delay - FADE_MILLIS).coerceAtLeast(0L))
        exo.volume = 1f
    }

    /** Let the current track finish, then pause. */
    fun startEndOfTrack() {
        val exo = player ?: return
        cancel()
        exo.pauseAtEndOfMediaItems = true
        _state.value = State.EndOfTrack
    }

    fun cancel() {
        handler.removeCallbacks(startFade)
        handler.removeCallbacks(fadeStep)
        player?.let {
            it.pauseAtEndOfMediaItems = false
            it.volume = 1f
        }
        _state.value = State.Off
    }
}
