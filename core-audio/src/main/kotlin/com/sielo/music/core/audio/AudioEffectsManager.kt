package com.sielo.music.core.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log

object AudioEffectsManager {
    private const val TAG = "AudioEffectsManager"

    private var currentSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    var isEqualizerEnabled: Boolean = true
        private set
    var isBassBoostEnabled: Boolean = true
        private set
    var bassStrength: Int = 800 // 0 to 1000
        private set
    var selectedPreset: String = "Normal"
        private set

    @Synchronized
    fun onAudioSessionIdChanged(sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        currentSessionId = sessionId
        initEffects()
    }

    @Synchronized
    private fun initEffects() {
        if (currentSessionId <= 0) return
        try {
            releaseEffects()

            // Initialize Equalizer
            try {
                equalizer = Equalizer(0, currentSessionId).apply {
                    enabled = isEqualizerEnabled
                    applyCurrentPreset(this)
                }
                Log.d(TAG, "Hardware DSP Equalizer initialized on session $currentSessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Hardware Equalizer not supported on this device/session: ${e.message}")
            }

            // Initialize Bass Boost
            try {
                bassBoost = BassBoost(0, currentSessionId).apply {
                    enabled = isBassBoostEnabled
                    if (strengthSupported) {
                        setStrength(bassStrength.coerceIn(0, 1000).toShort())
                    }
                }
                Log.d(TAG, "Hardware DSP BassBoost initialized on session $currentSessionId (strength=$bassStrength)")
            } catch (e: Exception) {
                Log.w(TAG, "Hardware BassBoost not supported on this device/session: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring AudioEffects: ${e.message}", e)
        }
    }

    @Synchronized
    fun setEqualizerEnabled(enabled: Boolean) {
        isEqualizerEnabled = enabled
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Could not set equalizer state: ${e.message}")
        }
    }

    @Synchronized
    fun setBassBoostEnabled(enabled: Boolean) {
        isBassBoostEnabled = enabled
        try {
            bassBoost?.enabled = enabled
            if (enabled && bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(bassStrength.coerceIn(0, 1000).toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set bass boost state: ${e.message}")
        }
    }

    @Synchronized
    fun setBassStrength(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        try {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(bassStrength.toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set bass strength: ${e.message}")
        }
    }

    @Synchronized
    fun setEqualizerPreset(presetName: String) {
        selectedPreset = presetName
        equalizer?.let { applyCurrentPreset(it) }
    }

    private fun applyCurrentPreset(eq: Equalizer) {
        try {
            val numPresets = eq.numberOfPresets
            for (i in 0 until numPresets) {
                if (eq.getPresetName(i.toShort()).equals(selectedPreset, ignoreCase = true)) {
                    eq.usePreset(i.toShort())
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply preset $selectedPreset: ${e.message}")
        }
    }

    fun getAvailablePresets(): List<String> {
        val list = mutableListOf("Normal", "Acoustic", "Bass Boost", "Classical", "Dance", "Electronic", "Hip Hop", "Jazz", "Pop", "Rock", "Vocal")
        try {
            equalizer?.let { eq ->
                val num = eq.numberOfPresets
                if (num > 0) {
                    val hardwarePresets = (0 until num).mapNotNull {
                        try { eq.getPresetName(it.toShort()) } catch (_: Exception) { null }
                    }
                    if (hardwarePresets.isNotEmpty()) return hardwarePresets
                }
            }
        } catch (_: Exception) {}
        return list
    }

    @Synchronized
    private fun releaseEffects() {
        try {
            equalizer?.release()
            equalizer = null
        } catch (_: Exception) {}
        try {
            bassBoost?.release()
            bassBoost = null
        } catch (_: Exception) {}
    }

    @Synchronized
    fun release() {
        releaseEffects()
        currentSessionId = 0
    }
}
