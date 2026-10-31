package com.luna.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.luna.music.data.Track

class MusicPlayer(context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    fun setQueue(tracks: List<Track>, startIndex: Int) {
        exoPlayer.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) }, startIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun release() = exoPlayer.release()
}
