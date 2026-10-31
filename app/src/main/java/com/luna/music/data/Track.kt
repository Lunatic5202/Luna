package com.luna.music.data

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val artworkUri: Uri?
)
