package com.luna.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small, local-only persistence for user-created playlists. */
class ComboRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("luna_combos", Context.MODE_PRIVATE)

    fun load(): List<MusicCombo> = runCatching {
        val saved = preferences.getString(KEY_COMBOS, "[]") ?: "[]"
        val array = JSONArray(saved)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val trackIds = item.optJSONArray("tracks") ?: JSONArray()
                val ids = buildList {
                    for (trackIndex in 0 until trackIds.length()) {
                        val id = trackIds.optLong(trackIndex, -1L)
                        if (id >= 0L) add(id)
                    }
                }
                val name = item.optString("name").trim()
                val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
                if (name.isNotEmpty() && ids.isNotEmpty()) add(MusicCombo(id, name, ids))
            }
        }
    }.getOrDefault(emptyList())

    fun create(name: String, trackIds: List<Long>): List<MusicCombo> {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || trackIds.isEmpty()) return load()
        val combos = load() + MusicCombo(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            trackIds = trackIds.distinct()
        )
        save(combos)
        return combos
    }

    fun delete(comboId: String): List<MusicCombo> = load()
        .filterNot { it.id == comboId }
        .also(::save)

    private fun save(combos: List<MusicCombo>) {
        val array = JSONArray()
        combos.forEach { combo ->
            val trackIds = JSONArray().apply { combo.trackIds.forEach(::put) }
            array.put(
                JSONObject()
                    .put("id", combo.id)
                    .put("name", combo.name)
                    .put("tracks", trackIds)
            )
        }
        preferences.edit().putString(KEY_COMBOS, array.toString()).apply()
    }

    private companion object {
        const val KEY_COMBOS = "saved_combos"
    }
}

