package com.luna.music.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.ln

data class EqualizerBandState(
    val index: Int,
    val centerFrequencyHz: Int,
    val levelMb: Int
)

enum class EqualizerProfileKind { Custom, BuiltIn, Device }

/**
 * One selectable sound profile. [id] is stable across launches:
 * `custom:<uuid>`, `builtin:<key>` or `device:<presetIndex>`.
 */
data class EqualizerProfile(
    val id: String,
    val name: String,
    val kind: EqualizerProfileKind
)

data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqualizerBandState> = emptyList(),
    val profiles: List<EqualizerProfile> = emptyList(),
    /** Profile last chosen, or null when the user started from scratch. */
    val activeProfileId: String? = null,
    /** True once bands were dragged after choosing [activeProfileId]. */
    val modified: Boolean = false,
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val message: String = "Start playback to connect the equalizer."
) {
    val activeProfile: EqualizerProfile? get() = profiles.firstOrNull { it.id == activeProfileId }
}

/** A user profile stored as (frequency, level) points so it survives a change in band count. */
private data class CustomProfile(val id: String, val name: String, val points: List<Pair<Int, Int>>)

/** Gains in dB at [BUILT_IN_FREQUENCIES]; applied to any band layout by log-frequency interpolation. */
private data class BuiltInProfile(val key: String, val name: String, val gainsDb: List<Float>)

private val BUILT_IN_FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

private val BUILT_IN_PROFILES = listOf(
    BuiltInProfile("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    BuiltInProfile("bass_boost", "Bass Boost", listOf(6f, 5.5f, 4.5f, 2.5f, 1f, 0f, 0f, 0f, 0f, 0f)),
    BuiltInProfile("bass_reducer", "Bass Reducer", listOf(-5f, -4.5f, -3f, -1.5f, 0f, 0f, 0f, 0f, 0f, 0f)),
    BuiltInProfile("treble_boost", "Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 5f, 5.5f)),
    BuiltInProfile("vocal", "Vocal Boost", listOf(-3f, -2f, -1f, 1f, 3f, 4f, 3.5f, 2f, 0f, -1f)),
    BuiltInProfile("rock", "Rock", listOf(4.5f, 3.5f, 2f, -1f, -2f, -1f, 2f, 3.5f, 4.5f, 5f)),
    BuiltInProfile("pop", "Pop", listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f)),
    BuiltInProfile("hip_hop", "Hip-Hop", listOf(5f, 4.5f, 2f, 3f, -1f, -1f, 1f, -0.5f, 2f, 3f)),
    BuiltInProfile("electronic", "Electronic", listOf(5f, 4f, 1f, 0f, -2f, 2f, 1f, 2f, 4f, 5f)),
    BuiltInProfile("jazz", "Jazz", listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f)),
    BuiltInProfile("classical", "Classical", listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
    BuiltInProfile("acoustic", "Acoustic", listOf(4f, 4f, 3f, 1f, 2f, 1f, 2f, 3f, 3f, 2f)),
    BuiltInProfile("loudness", "Loudness", listOf(6f, 4f, 0f, -1f, -1f, -1f, 0f, -1f, 4f, 5f)),
    BuiltInProfile("spoken_word", "Spoken Word", listOf(-4f, -3f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -2f))
)

/** Owns one platform equalizer for the active ExoPlayer audio session. */
object LunaEqualizerEngine {
    private const val PREFS_NAME = "luna_equalizer"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PRESET = "preset" // legacy: device preset index
    private const val KEY_LEVELS = "levels"
    private const val KEY_ACTIVE = "active_profile"
    private const val KEY_MODIFIED = "modified"
    private const val KEY_CUSTOM = "custom_profiles"
    const val MAX_PROFILE_NAME_LENGTH = 24

    private val mutableState = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = mutableState.asStateFlow()

    private var equalizer: Equalizer? = null
    private var sessionId = 0
    private var preferences: SharedPreferences? = null
    private var customProfiles: List<CustomProfile> = emptyList()
    private var activeProfileId: String? = null
    private var modified = false

    @Synchronized
    fun attach(context: Context, newSessionId: Int) {
        if (newSessionId <= 0) {
            detach()
            mutableState.value = EqualizerState(message = "Start playback to connect the equalizer.")
            return
        }
        if (sessionId == newSessionId && equalizer != null) return

        releaseEffect()
        sessionId = newSessionId
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences = prefs
        customProfiles = readCustomProfiles(prefs)
        activeProfileId = prefs.getString(KEY_ACTIVE, null)
            ?: prefs.getInt(KEY_PRESET, -1).takeIf { it >= 0 }?.let { "device:$it" }
        modified = prefs.getBoolean(KEY_MODIFIED, false)
        try {
            val effect = Equalizer(0, newSessionId)
            equalizer = effect
            val saved = prefs.getString(KEY_LEVELS, null)
                ?.split(',')
                ?.mapNotNull(String::toIntOrNull)
                .orEmpty()
            val presetIndex = activeProfileId
                ?.takeIf { it.startsWith("device:") }
                ?.removePrefix("device:")
                ?.toIntOrNull()
            if (saved.size == effect.numberOfBands.toInt()) {
                applyLevels(effect, saved)
            } else if (presetIndex != null && presetIndex in 0 until effect.numberOfPresets.toInt()) {
                effect.usePreset(presetIndex.toShort())
            }
            effect.enabled = prefs.getBoolean(KEY_ENABLED, false)
            persistLevels(effect)
            publish(effect)
        } catch (_: Exception) {
            releaseEffect()
            mutableState.value = EqualizerState(message = "The system equalizer isn't available on this device.")
        }
    }

    @Synchronized
    fun detach() {
        releaseEffect()
        sessionId = 0
        mutableState.value = EqualizerState(message = "Start playback to connect the equalizer.")
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        val effect = equalizer ?: return
        runCatching { effect.enabled = enabled }.onSuccess {
            preferences?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
            publish(effect)
        }
    }

    /** Manual band change. Keeps the base profile so a custom one can be updated in place. */
    @Synchronized
    fun setBandLevel(index: Int, levelMb: Int) {
        val effect = equalizer ?: return
        runCatching {
            val range = effect.bandLevelRange
            effect.setBandLevel(index.toShort(), levelMb.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            modified = true
            persistLevels(effect)
            publish(effect)
        }
    }

    @Synchronized
    fun selectProfile(id: String) {
        val effect = equalizer ?: return
        val applied = runCatching {
            when {
                id.startsWith("device:") -> {
                    val index = id.removePrefix("device:").toIntOrNull() ?: return
                    if (index !in 0 until effect.numberOfPresets.toInt()) return
                    effect.usePreset(index.toShort())
                    true
                }
                id.startsWith("builtin:") -> {
                    val profile = BUILT_IN_PROFILES.firstOrNull { "builtin:${it.key}" == id } ?: return
                    val points = BUILT_IN_FREQUENCIES.mapIndexed { i, hz -> hz to (profile.gainsDb[i] * 100f).toInt() }
                    applyLevels(effect, levelsFromPoints(effect, points))
                    true
                }
                id.startsWith("custom:") -> {
                    val profile = customProfiles.firstOrNull { it.id == id } ?: return
                    applyLevels(effect, levelsFromPoints(effect, profile.points))
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
        if (!applied) return
        activeProfileId = id
        modified = false
        persistLevels(effect)
        publish(effect)
    }

    /** Saves the current band levels as a new custom profile (or overwrites one with the same name). */
    @Synchronized
    fun saveAsCustomProfile(rawName: String): Boolean {
        val effect = equalizer ?: return false
        val name = cleanName(rawName) ?: return false
        val points = currentPoints(effect)
        val existing = customProfiles.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val profile = CustomProfile(existing?.id ?: "custom:${UUID.randomUUID()}", name, points)
        customProfiles = customProfiles.filterNot { it.id == profile.id } + profile
        activeProfileId = profile.id
        modified = false
        writeCustomProfiles()
        persistLevels(effect)
        publish(effect)
        return true
    }

    /** Writes the current band levels back into the active custom profile. */
    @Synchronized
    fun updateActiveCustomProfile(): Boolean {
        val effect = equalizer ?: return false
        val id = activeProfileId?.takeIf { it.startsWith("custom:") } ?: return false
        if (customProfiles.none { it.id == id }) return false
        val points = currentPoints(effect)
        customProfiles = customProfiles.map { if (it.id == id) it.copy(points = points) else it }
        modified = false
        writeCustomProfiles()
        persistLevels(effect)
        publish(effect)
        return true
    }

    @Synchronized
    fun renameCustomProfile(id: String, rawName: String): Boolean {
        val name = cleanName(rawName) ?: return false
        if (customProfiles.none { it.id == id }) return false
        if (customProfiles.any { it.id != id && it.name.equals(name, ignoreCase = true) }) return false
        customProfiles = customProfiles.map { if (it.id == id) it.copy(name = name) else it }
        writeCustomProfiles()
        equalizer?.let(::publish)
        return true
    }

    @Synchronized
    fun deleteCustomProfile(id: String) {
        if (customProfiles.none { it.id == id }) return
        customProfiles = customProfiles.filterNot { it.id == id }
        if (activeProfileId == id) {
            activeProfileId = null
            modified = false
        }
        writeCustomProfiles()
        equalizer?.let {
            persistLevels(it)
            publish(it)
        }
    }

    @Synchronized
    fun resetToFlat() = selectProfile("builtin:flat")

    private fun cleanName(raw: String): String? =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_PROFILE_NAME_LENGTH).takeIf { it.isNotBlank() }

    private fun applyLevels(effect: Equalizer, levelsMb: List<Int>) {
        val range = effect.bandLevelRange
        levelsMb.forEachIndexed { index, level ->
            effect.setBandLevel(index.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
        }
    }

    private fun currentPoints(effect: Equalizer): List<Pair<Int, Int>> =
        (0 until effect.numberOfBands.toInt()).map { index ->
            (effect.getCenterFreq(index.toShort()) / 1000).coerceAtLeast(1) to effect.getBandLevel(index.toShort()).toInt()
        }

    /** Log-frequency interpolation of [points] onto the device's own band centres. */
    private fun levelsFromPoints(effect: Equalizer, points: List<Pair<Int, Int>>): List<Int> {
        val sorted = points.sortedBy { it.first }
        return (0 until effect.numberOfBands.toInt()).map { index ->
            val hz = (effect.getCenterFreq(index.toShort()) / 1000).coerceAtLeast(1)
            interpolate(sorted, hz)
        }
    }

    private fun interpolate(sorted: List<Pair<Int, Int>>, hz: Int): Int {
        if (sorted.isEmpty()) return 0
        if (hz <= sorted.first().first) return sorted.first().second
        if (hz >= sorted.last().first) return sorted.last().second
        val upperIndex = sorted.indexOfFirst { it.first >= hz }
        val (loHz, loLevel) = sorted[upperIndex - 1]
        val (hiHz, hiLevel) = sorted[upperIndex]
        if (hiHz == loHz) return hiLevel
        val t = (ln(hz.toFloat()) - ln(loHz.toFloat())) / (ln(hiHz.toFloat()) - ln(loHz.toFloat()))
        return (loLevel + (hiLevel - loLevel) * t).toInt()
    }

    private fun persistLevels(effect: Equalizer) {
        runCatching {
            preferences?.edit()
                ?.putString(KEY_LEVELS, (0 until effect.numberOfBands.toInt())
                    .joinToString(",") { effect.getBandLevel(it.toShort()).toString() })
                ?.putString(KEY_ACTIVE, activeProfileId)
                ?.putBoolean(KEY_MODIFIED, modified)
                ?.remove(KEY_PRESET)
                ?.apply()
        }
    }

    private fun readCustomProfiles(prefs: SharedPreferences): List<CustomProfile> = runCatching {
        val array = JSONArray(prefs.getString(KEY_CUSTOM, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.getJSONObject(i)
            val pointsJson = item.getJSONArray("points")
            val points = (0 until pointsJson.length()).map { p ->
                val point = pointsJson.getJSONArray(p)
                point.getInt(0) to point.getInt(1)
            }
            CustomProfile(item.getString("id"), item.getString("name"), points).takeIf { points.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    private fun writeCustomProfiles() {
        val array = JSONArray()
        customProfiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("points", JSONArray(profile.points.map { JSONArray(listOf(it.first, it.second)) }))
            )
        }
        preferences?.edit()?.putString(KEY_CUSTOM, array.toString())?.apply()
    }

    @Synchronized
    private fun publish(effect: Equalizer) {
        runCatching {
            val range = effect.bandLevelRange
            val bandCount = effect.numberOfBands.toInt()
            val bands = (0 until bandCount).map { index ->
                EqualizerBandState(
                    index = index,
                    centerFrequencyHz = (effect.getCenterFreq(index.toShort()) / 1000).coerceAtLeast(1),
                    levelMb = effect.getBandLevel(index.toShort()).toInt()
                )
            }
            val profiles = customProfiles.sortedBy { it.name.lowercase() }
                .map { EqualizerProfile(it.id, it.name, EqualizerProfileKind.Custom) } +
                BUILT_IN_PROFILES.map { EqualizerProfile("builtin:${it.key}", it.name, EqualizerProfileKind.BuiltIn) } +
                (0 until effect.numberOfPresets.toInt()).map {
                    EqualizerProfile("device:$it", effect.getPresetName(it.toShort()), EqualizerProfileKind.Device)
                }
            val active = activeProfileId?.takeIf { id -> profiles.any { it.id == id } }
            mutableState.value = EqualizerState(
                available = true,
                enabled = effect.enabled,
                bands = bands,
                profiles = profiles,
                activeProfileId = active,
                modified = modified,
                minLevelMb = range[0].toInt(),
                maxLevelMb = range[1].toInt(),
                message = ""
            )
        }.onFailure {
            mutableState.value = EqualizerState(message = "The system equalizer isn't available on this device.")
        }
    }

    private fun releaseEffect() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}
