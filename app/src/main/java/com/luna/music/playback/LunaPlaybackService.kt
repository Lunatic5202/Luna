package com.luna.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
 * lock-screen controls and Bluetooth/headset controls. Audio focus,
 * headphone-unplug handling and the CPU wake lock are configured explicitly
 * on the player below; ExoPlayer does none of them by default.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class LunaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // Pause for calls and other apps, duck for notifications, resume afterwards.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // Pause when headphones are unplugged or Bluetooth disconnects.
            .setHandleAudioBecomingNoisy(true)
            // Local files only need the CPU kept awake, not Wi-Fi.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveErrors = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error, skipping item", error)
                // A deleted or corrupt file shouldn't end the session: move on to the next
                // item, but give up once every item in the queue has failed in a row so a
                // fully broken queue can't loop forever under repeat-all.
                consecutiveErrors++
                if (consecutiveErrors <= player.mediaItemCount && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else {
                    consecutiveErrors = 0
                }
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

        // Tapping the notification or lock-screen card reopens the app.
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.ic_stat_luna)
            }
        )
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
        private const val TAG = "LunaPlayback"
        private val _audioSessionId = MutableStateFlow(0)

        /** Current ExoPlayer audio session id, or 0 when the service isn't running. */
        val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()
    }
}
