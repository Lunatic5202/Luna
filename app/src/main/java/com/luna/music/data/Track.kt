package com.luna.music.data

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val artworkUri: Uri?,
    /** Seconds since epoch, from MediaStore; 0 when unknown. */
    val dateAdded: Long = 0L,
    /** Folder the file lives in, e.g. "Music/Rock". Empty when unknown. */
    val folder: String = ""
)
