package com.luna.music.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.luna.music.MainActivity
import com.luna.music.R
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
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.luna_notification_logo)
            }
        )
        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            // Keep the CPU awake while playing so the screen can turn off mid-song.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
            }
        // A file that can't be decoded (deleted, corrupt) skips ahead instead of stalling.
        player.addListener(object : Player.Listener {
            private var failedInARow = 0

            override fun onPlayerError(error: PlaybackException) {
                failedInARow++
                if (player.hasNextMediaItem() && failedInARow <= player.mediaItemCount) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) failedInARow = 0
            }
        })
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
        val openPlayerIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayerIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** Swiping the app away while paused should shut the service down, not leave it idle. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

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

