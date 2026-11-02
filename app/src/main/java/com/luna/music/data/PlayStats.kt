package com.luna.music.data

import android.content.Context

/** Local play counts for the "Top" library view. Stored on the device only. */
class PlayStats(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("luna_play_stats", Context.MODE_PRIVATE)

    fun increment(trackId: Long) {
        prefs.edit().putInt("p_$trackId", prefs.getInt("p_$trackId", 0) + 1).apply()
    }

    fun counts(): Map<Long, Int> = prefs.all.mapNotNull { (key, value) ->
        val id = key.removePrefix("p_").toLongOrNull()
        if (key.startsWith("p_") && id != null && value is Int) id to value else null
    }.toMap()
}
