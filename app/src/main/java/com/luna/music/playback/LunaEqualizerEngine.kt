package com.luna.music.playback

import android.content.Context
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBandState(
    val index: Int,
    val centerFrequencyHz: Int,
    val levelMb: Int
)

data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqualizerBandState> = emptyList(),
    val presets: List<String> = emptyList(),
    val selectedPreset: Int = -1,
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val message: String = "Start playback to connect the equalizer."
)

/** Owns one platform equalizer for the active ExoPlayer audio session. */
object LunaEqualizerEngine {
    private const val PREFS_NAME = "luna_equalizer"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PRESET = "preset"
    private const val KEY_LEVELS = "levels"

    private val mutableState = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = mutableState.asStateFlow()

    private var equalizer: Equalizer? = null
    private var sessionId = 0
    private var preferences: android.content.SharedPreferences? = null

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
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val effect = Equalizer(0, newSessionId)
            equalizer = effect
            val preset = preferences?.getInt(KEY_PRESET, -1) ?: -1
            val presetCount = effect.numberOfPresets.toInt()
            if (preset in 0 until presetCount) {
                effect.usePreset(preset.toShort())
            } else {
                val saved = preferences?.getString(KEY_LEVELS, null)
                    ?.split(',')
                    ?.mapNotNull(String::toIntOrNull)
                    .orEmpty()
                if (saved.size == effect.numberOfBands.toInt()) {
                    val range = effect.bandLevelRange
                    saved.forEachIndexed { index, level ->
                        effect.setBandLevel(index.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                    }
                }
            }
            effect.enabled = preferences?.getBoolean(KEY_ENABLED, false) ?: false
            publish(effect, preset.takeIf { it in 0 until presetCount } ?: -1)
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
            publish(effect, mutableState.value.selectedPreset)
        }
    }

    @Synchronized
    fun setBandLevel(index: Int, levelMb: Int) {
        val effect = equalizer ?: return
        runCatching {
            val range = effect.bandLevelRange
            effect.setBandLevel(index.toShort(), levelMb.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            preferences?.edit()
                ?.putInt(KEY_PRESET, -1)
                ?.putString(KEY_LEVELS, (0 until effect.numberOfBands.toInt())
                    .joinToString(",") { effect.getBandLevel(it.toShort()).toString() })
                ?.apply()
            publish(effect, -1)
        }
    }

    @Synchronized
    fun selectPreset(index: Int) {
        val effect = equalizer ?: return
        if (index !in 0 until effect.numberOfPresets.toInt()) return
        runCatching { effect.usePreset(index.toShort()) }.onSuccess {
            preferences?.edit()?.putInt(KEY_PRESET, index)?.apply()
            publish(effect, index)
        }
    }

    @Synchronized
    private fun publish(effect: Equalizer, selectedPreset: Int) {
        runCatching {
            val range = effect.bandLevelRange
            val bandCount = effect.numberOfBands.toInt()
            val presets = (0 until effect.numberOfPresets.toInt()).map { effect.getPresetName(it.toShort()) }
            val bands = (0 until bandCount).map { index ->
                EqualizerBandState(
                    index = index,
                    centerFrequencyHz = (effect.getCenterFreq(index.toShort()) / 1000).coerceAtLeast(1),
                    levelMb = effect.getBandLevel(index.toShort()).toInt()
                )
            }
            mutableState.value = EqualizerState(
                available = true,
                enabled = effect.enabled,
                bands = bands,
                presets = presets,
                selectedPreset = selectedPreset,
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

