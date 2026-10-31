package com.luna.music.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the player independently of the Activity lifecycle.
 *
 * MediaSessionService integrates with Android's media notification,
 * lock-screen controls, Bluetooth/headset controls, and audio focus.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class LunaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { setAudioAttributes(AudioAttributes.DEFAULT, true) }
        // Service and UI share a process, so publish the audio session id directly.
        // (A MediaController cannot read it across the session boundary.)
        _audioSessionId.value = player.audioSessionId
        LunaEqualizerEngine.attach(this, player.audioSessionId)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                _audioSessionId.value = audioSessionId
                LunaEqualizerEngine.attach(this@LunaPlaybackService, audioSessionId)
            }
        })
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        _audioSessionId.value = 0
        LunaEqualizerEngine.detach()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private val _audioSessionId = MutableStateFlow(0)

        /** Current ExoPlayer audio session id, or 0 when the service isn't running. */
        val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()
    }
}

