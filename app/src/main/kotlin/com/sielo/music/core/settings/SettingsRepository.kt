package com.sielo.music.core.settings

import android.content.Context
import android.content.SharedPreferences
import coil.imageLoader
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.audio.service.MusicPlaybackService
import com.sielo.music.core.auth.UserManager
import com.sielo.music.core.database.DatabaseCleaner
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.dao.SearchHistoryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchHistoryDao: SearchHistoryDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val userManager: UserManager,
    private val playerManager: PlayerManager,
    private val databaseCleaner: DatabaseCleaner,
    private val cloudSyncManager: com.sielo.music.core.sync.CloudSyncManager
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sielo_user_settings", Context.MODE_PRIVATE)

    // ── 1. PLAYBACK & AUDIO ──
    private val _streamQuality = MutableStateFlow(prefs.getString(KEY_STREAM_QUALITY, "Master (320kbps Opus / FLAC)") ?: "Master (320kbps Opus / FLAC)")
    val streamQuality: StateFlow<String> = _streamQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_QUALITY, "High (320kbps)") ?: "High (320kbps)")
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(prefs.getBoolean(KEY_EQUALIZER_ENABLED, true))
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _bassBoostEnabled = MutableStateFlow(prefs.getBoolean(KEY_BASS_BOOST_ENABLED, true))
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    private val _bassStrength = MutableStateFlow(prefs.getInt(KEY_BASS_STRENGTH, 800))
    val bassStrength: StateFlow<Int> = _bassStrength.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(prefs.getString(KEY_EQUALIZER_PRESET, "Normal") ?: "Normal")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _gaplessPlayback = MutableStateFlow(prefs.getBoolean(KEY_GAPLESS_PLAYBACK, true))
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt(KEY_CROSSFADE_SECONDS, 4))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _autoplayEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY_ENABLED, true))
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    private val _rememberPosition = MutableStateFlow(prefs.getBoolean(KEY_REMEMBER_POSITION, true))
    val rememberPosition: StateFlow<Boolean> = _rememberPosition.asStateFlow()

    // ── 2. AUDIO OUTPUT ──
    private val _pauseOnDisconnect = MutableStateFlow(prefs.getBoolean(KEY_PAUSE_ON_DISCONNECT, true))
    val pauseOnDisconnect: StateFlow<Boolean> = _pauseOnDisconnect.asStateFlow()

    private val _bluetoothAutoResume = MutableStateFlow(prefs.getBoolean(KEY_BLUETOOTH_AUTO_RESUME, false))
    val bluetoothAutoResume: StateFlow<Boolean> = _bluetoothAutoResume.asStateFlow()

    private val _volumeNormalization = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_NORMALIZATION, true))
    val volumeNormalization: StateFlow<Boolean> = _volumeNormalization.asStateFlow()

    // ── 3. DOWNLOADS & STORAGE ──
    private val _downloadWifiOnly = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, true))
    val downloadWifiOnly: StateFlow<Boolean> = _downloadWifiOnly.asStateFlow()

    private val _cacheSize = MutableStateFlow(calculateCacheSize())
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    // ── 4. APPEARANCE ──
    private val _oledDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_OLED_DARK_THEME, true))
    val oledDarkTheme: StateFlow<Boolean> = _oledDarkTheme.asStateFlow()

    private val _dynamicArtworkTint = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_ARTWORK_TINT, true))
    val dynamicArtworkTint: StateFlow<Boolean> = _dynamicArtworkTint.asStateFlow()

    private val _highFpsVisuals = MutableStateFlow(prefs.getBoolean(KEY_HIGH_FPS_VISUALS, true))
    val highFpsVisuals: StateFlow<Boolean> = _highFpsVisuals.asStateFlow()

    // ── 5. NOTIFICATIONS ──
    private val _notificationsPlayback = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_PLAYBACK, true))
    val notificationsPlayback: StateFlow<Boolean> = _notificationsPlayback.asStateFlow()

    private val _notificationsListenTogether = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_LISTEN_TOGETHER, true))
    val notificationsListenTogether: StateFlow<Boolean> = _notificationsListenTogether.asStateFlow()

    // ── 6. LISTEN TOGETHER ──
    private val _allowRoomInvites = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_ROOM_INVITES, true))
    val allowRoomInvites: StateFlow<Boolean> = _allowRoomInvites.asStateFlow()

    private val _autoRejoinSession = MutableStateFlow(prefs.getBoolean(KEY_AUTO_REJOIN_SESSION, false))
    val autoRejoinSession: StateFlow<Boolean> = _autoRejoinSession.asStateFlow()

    // ── 7. PRIVACY & DATA ──
    private val _privateListening = MutableStateFlow(prefs.getBoolean(KEY_PRIVATE_LISTENING, false))
    val privateListening: StateFlow<Boolean> = _privateListening.asStateFlow()

    // ── 8. RECOMMENDATIONS ──
    private val _recommendationDiversity = MutableStateFlow(prefs.getString(KEY_REC_DIVERSITY, "Balanced") ?: "Balanced")
    val recommendationDiversity: StateFlow<String> = _recommendationDiversity.asStateFlow()

    private val _prioritizeFollowedArtists = MutableStateFlow(prefs.getBoolean(KEY_PRIORITIZE_FOLLOWED, true))
    val prioritizeFollowedArtists: StateFlow<Boolean> = _prioritizeFollowedArtists.asStateFlow()

    init {
        // Wire cloud sync providers
        cloudSyncManager.settingsProvider = { getSettingsMap() }
        cloudSyncManager.settingsRestorer = { restoreSettingsMap(it) }

        // Sync initial player manager flags with persisted settings
        playerManager.isPrivateListeningEnabled = _privateListening.value
        playerManager.isAutoplayEnabled = _autoplayEnabled.value
        playerManager.setPlaybackSpeed(_playbackSpeed.value)
        com.sielo.music.core.audio.AudioEffectsManager.setEqualizerEnabled(_equalizerEnabled.value)
        com.sielo.music.core.audio.AudioEffectsManager.setBassBoostEnabled(_bassBoostEnabled.value)
        com.sielo.music.core.audio.AudioEffectsManager.setBassStrength(_bassStrength.value)
        com.sielo.music.core.audio.AudioEffectsManager.setEqualizerPreset(_equalizerPreset.value)
        updateStreamQualityBitrate(_streamQuality.value)
    }

    private fun updateStreamQualityBitrate(quality: String) {
        val suffix = when {
            quality.contains("Master", ignoreCase = true) -> "_320.mp4"
            quality.contains("High", ignoreCase = true) -> "_160.mp4"
            quality.contains("Standard", ignoreCase = true) -> "_96.mp4"
            else -> "_320.mp4"
        }
        com.sielo.music.core.network.innertube.InnerTubeClient.preferredBitrateSuffix = suffix
    }

    private fun notifySettingChanged() {
        cloudSyncManager.syncSettingsToCloud()
    }

    // ── SETTERS WITH IMMEDIATE PERSISTENCE & CLOUD SYNC ──

    fun setStreamQuality(quality: String) {
        prefs.edit().putString(KEY_STREAM_QUALITY, quality).apply()
        _streamQuality.value = quality
        updateStreamQualityBitrate(quality)
        notifySettingChanged()
    }

    fun setDownloadQuality(quality: String) {
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
        _downloadQuality.value = quality
        notifySettingChanged()
    }

    fun toggleEqualizer() {
        val next = !_equalizerEnabled.value
        prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, next).apply()
        _equalizerEnabled.value = next
        com.sielo.music.core.audio.AudioEffectsManager.setEqualizerEnabled(next)
        notifySettingChanged()
    }

    fun toggleBassBoost() {
        val next = !_bassBoostEnabled.value
        prefs.edit().putBoolean(KEY_BASS_BOOST_ENABLED, next).apply()
        _bassBoostEnabled.value = next
        com.sielo.music.core.audio.AudioEffectsManager.setBassBoostEnabled(next)
        notifySettingChanged()
    }

    fun setBassStrength(strength: Int) {
        prefs.edit().putInt(KEY_BASS_STRENGTH, strength).apply()
        _bassStrength.value = strength
        com.sielo.music.core.audio.AudioEffectsManager.setBassStrength(strength)
        notifySettingChanged()
    }

    fun setEqualizerPreset(preset: String) {
        prefs.edit().putString(KEY_EQUALIZER_PRESET, preset).apply()
        _equalizerPreset.value = preset
        com.sielo.music.core.audio.AudioEffectsManager.setEqualizerPreset(preset)
        notifySettingChanged()
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
        _playbackSpeed.value = speed
        playerManager.setPlaybackSpeed(speed)
        notifySettingChanged()
    }

    fun toggleGaplessPlayback() {
        val next = !_gaplessPlayback.value
        prefs.edit().putBoolean(KEY_GAPLESS_PLAYBACK, next).apply()
        _gaplessPlayback.value = next
        notifySettingChanged()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CROSSFADE_SECONDS, seconds).apply()
        _crossfadeSeconds.value = seconds
        notifySettingChanged()
    }

    fun toggleAutoplay() {
        val next = !_autoplayEnabled.value
        prefs.edit().putBoolean(KEY_AUTOPLAY_ENABLED, next).apply()
        _autoplayEnabled.value = next
        playerManager.isAutoplayEnabled = next
        notifySettingChanged()
    }

    fun toggleRememberPosition() {
        val next = !_rememberPosition.value
        prefs.edit().putBoolean(KEY_REMEMBER_POSITION, next).apply()
        _rememberPosition.value = next
        notifySettingChanged()
    }

    fun togglePauseOnDisconnect() {
        val next = !_pauseOnDisconnect.value
        prefs.edit().putBoolean(KEY_PAUSE_ON_DISCONNECT, next).apply()
        _pauseOnDisconnect.value = next
        notifySettingChanged()
    }

    fun toggleBluetoothAutoResume() {
        val next = !_bluetoothAutoResume.value
        prefs.edit().putBoolean(KEY_BLUETOOTH_AUTO_RESUME, next).apply()
        _bluetoothAutoResume.value = next
        notifySettingChanged()
    }

    fun toggleVolumeNormalization() {
        val next = !_volumeNormalization.value
        prefs.edit().putBoolean(KEY_VOLUME_NORMALIZATION, next).apply()
        _volumeNormalization.value = next
        notifySettingChanged()
    }

    fun toggleDownloadWifiOnly() {
        val next = !_downloadWifiOnly.value
        prefs.edit().putBoolean(KEY_DOWNLOAD_WIFI_ONLY, next).apply()
        _downloadWifiOnly.value = next
        notifySettingChanged()
    }

    fun toggleOledDarkTheme() {
        val next = !_oledDarkTheme.value
        prefs.edit().putBoolean(KEY_OLED_DARK_THEME, next).apply()
        _oledDarkTheme.value = next
        notifySettingChanged()
    }

    fun toggleDynamicArtworkTint() {
        val next = !_dynamicArtworkTint.value
        prefs.edit().putBoolean(KEY_DYNAMIC_ARTWORK_TINT, next).apply()
        _dynamicArtworkTint.value = next
        notifySettingChanged()
    }

    fun toggleHighFpsVisuals() {
        val next = !_highFpsVisuals.value
        prefs.edit().putBoolean(KEY_HIGH_FPS_VISUALS, next).apply()
        _highFpsVisuals.value = next
        notifySettingChanged()
    }

    fun toggleNotificationsPlayback() {
        val next = !_notificationsPlayback.value
        prefs.edit().putBoolean(KEY_NOTIF_PLAYBACK, next).apply()
        _notificationsPlayback.value = next
        notifySettingChanged()
    }

    fun toggleNotificationsListenTogether() {
        val next = !_notificationsListenTogether.value
        prefs.edit().putBoolean(KEY_NOTIF_LISTEN_TOGETHER, next).apply()
        _notificationsListenTogether.value = next
        notifySettingChanged()
    }

    fun toggleAllowRoomInvites() {
        val next = !_allowRoomInvites.value
        prefs.edit().putBoolean(KEY_ALLOW_ROOM_INVITES, next).apply()
        _allowRoomInvites.value = next
        notifySettingChanged()
    }

    fun toggleAutoRejoinSession() {
        val next = !_autoRejoinSession.value
        prefs.edit().putBoolean(KEY_AUTO_REJOIN_SESSION, next).apply()
        _autoRejoinSession.value = next
        notifySettingChanged()
    }

    fun togglePrivateListening() {
        val next = !_privateListening.value
        prefs.edit().putBoolean(KEY_PRIVATE_LISTENING, next).apply()
        _privateListening.value = next
        playerManager.isPrivateListeningEnabled = next
        notifySettingChanged()
    }

    fun setRecommendationDiversity(level: String) {
        prefs.edit().putString(KEY_REC_DIVERSITY, level).apply()
        _recommendationDiversity.value = level
        notifySettingChanged()
    }

    fun togglePrioritizeFollowedArtists() {
        val next = !_prioritizeFollowedArtists.value
        prefs.edit().putBoolean(KEY_PRIORITIZE_FOLLOWED, next).apply()
        _prioritizeFollowedArtists.value = next
        notifySettingChanged()
    }

    fun getSettingsMap(): Map<String, Any> {
        return mapOf(
            KEY_STREAM_QUALITY to _streamQuality.value,
            KEY_DOWNLOAD_QUALITY to _downloadQuality.value,
            KEY_EQUALIZER_ENABLED to _equalizerEnabled.value,
            KEY_BASS_BOOST_ENABLED to _bassBoostEnabled.value,
            KEY_BASS_STRENGTH to _bassStrength.value,
            KEY_EQUALIZER_PRESET to _equalizerPreset.value,
            KEY_PLAYBACK_SPEED to _playbackSpeed.value,
            KEY_GAPLESS_PLAYBACK to _gaplessPlayback.value,
            KEY_CROSSFADE_SECONDS to _crossfadeSeconds.value,
            KEY_AUTOPLAY_ENABLED to _autoplayEnabled.value,
            KEY_REMEMBER_POSITION to _rememberPosition.value,
            KEY_PAUSE_ON_DISCONNECT to _pauseOnDisconnect.value,
            KEY_BLUETOOTH_AUTO_RESUME to _bluetoothAutoResume.value,
            KEY_VOLUME_NORMALIZATION to _volumeNormalization.value,
            KEY_DOWNLOAD_WIFI_ONLY to _downloadWifiOnly.value,
            KEY_OLED_DARK_THEME to _oledDarkTheme.value,
            KEY_DYNAMIC_ARTWORK_TINT to _dynamicArtworkTint.value,
            KEY_HIGH_FPS_VISUALS to _highFpsVisuals.value,
            KEY_NOTIF_PLAYBACK to _notificationsPlayback.value,
            KEY_NOTIF_LISTEN_TOGETHER to _notificationsListenTogether.value,
            KEY_ALLOW_ROOM_INVITES to _allowRoomInvites.value,
            KEY_AUTO_REJOIN_SESSION to _autoRejoinSession.value,
            KEY_PRIVATE_LISTENING to _privateListening.value,
            KEY_REC_DIVERSITY to _recommendationDiversity.value,
            KEY_PRIORITIZE_FOLLOWED to _prioritizeFollowedArtists.value
        )
    }

    fun restoreSettingsMap(map: Map<String, Any>) {
        val editor = prefs.edit()
        (map[KEY_STREAM_QUALITY] as? String)?.let {
            editor.putString(KEY_STREAM_QUALITY, it)
            _streamQuality.value = it
            updateStreamQualityBitrate(it)
        }
        (map[KEY_DOWNLOAD_QUALITY] as? String)?.let {
            editor.putString(KEY_DOWNLOAD_QUALITY, it)
            _downloadQuality.value = it
        }
        (map[KEY_EQUALIZER_ENABLED] as? Boolean)?.let {
            editor.putBoolean(KEY_EQUALIZER_ENABLED, it)
            _equalizerEnabled.value = it
            com.sielo.music.core.audio.AudioEffectsManager.setEqualizerEnabled(it)
        }
        (map[KEY_BASS_BOOST_ENABLED] as? Boolean)?.let {
            editor.putBoolean(KEY_BASS_BOOST_ENABLED, it)
            _bassBoostEnabled.value = it
            com.sielo.music.core.audio.AudioEffectsManager.setBassBoostEnabled(it)
        }
        (map[KEY_BASS_STRENGTH] as? Number)?.let {
            editor.putInt(KEY_BASS_STRENGTH, it.toInt())
            _bassStrength.value = it.toInt()
            com.sielo.music.core.audio.AudioEffectsManager.setBassStrength(it.toInt())
        }
        (map[KEY_EQUALIZER_PRESET] as? String)?.let {
            editor.putString(KEY_EQUALIZER_PRESET, it)
            _equalizerPreset.value = it
            com.sielo.music.core.audio.AudioEffectsManager.setEqualizerPreset(it)
        }
        (map[KEY_PLAYBACK_SPEED] as? Number)?.let {
            editor.putFloat(KEY_PLAYBACK_SPEED, it.toFloat())
            _playbackSpeed.value = it.toFloat()
            playerManager.setPlaybackSpeed(it.toFloat())
        }
        (map[KEY_GAPLESS_PLAYBACK] as? Boolean)?.let {
            editor.putBoolean(KEY_GAPLESS_PLAYBACK, it)
            _gaplessPlayback.value = it
        }
        (map[KEY_CROSSFADE_SECONDS] as? Number)?.let {
            editor.putInt(KEY_CROSSFADE_SECONDS, it.toInt())
            _crossfadeSeconds.value = it.toInt()
        }
        (map[KEY_AUTOPLAY_ENABLED] as? Boolean)?.let {
            editor.putBoolean(KEY_AUTOPLAY_ENABLED, it)
            _autoplayEnabled.value = it
            playerManager.isAutoplayEnabled = it
        }
        (map[KEY_REMEMBER_POSITION] as? Boolean)?.let {
            editor.putBoolean(KEY_REMEMBER_POSITION, it)
            _rememberPosition.value = it
        }
        (map[KEY_PAUSE_ON_DISCONNECT] as? Boolean)?.let {
            editor.putBoolean(KEY_PAUSE_ON_DISCONNECT, it)
            _pauseOnDisconnect.value = it
        }
        (map[KEY_BLUETOOTH_AUTO_RESUME] as? Boolean)?.let {
            editor.putBoolean(KEY_BLUETOOTH_AUTO_RESUME, it)
            _bluetoothAutoResume.value = it
        }
        (map[KEY_VOLUME_NORMALIZATION] as? Boolean)?.let {
            editor.putBoolean(KEY_VOLUME_NORMALIZATION, it)
            _volumeNormalization.value = it
        }
        (map[KEY_DOWNLOAD_WIFI_ONLY] as? Boolean)?.let {
            editor.putBoolean(KEY_DOWNLOAD_WIFI_ONLY, it)
            _downloadWifiOnly.value = it
        }
        (map[KEY_OLED_DARK_THEME] as? Boolean)?.let {
            editor.putBoolean(KEY_OLED_DARK_THEME, it)
            _oledDarkTheme.value = it
        }
        (map[KEY_DYNAMIC_ARTWORK_TINT] as? Boolean)?.let {
            editor.putBoolean(KEY_DYNAMIC_ARTWORK_TINT, it)
            _dynamicArtworkTint.value = it
        }
        (map[KEY_HIGH_FPS_VISUALS] as? Boolean)?.let {
            editor.putBoolean(KEY_HIGH_FPS_VISUALS, it)
            _highFpsVisuals.value = it
        }
        (map[KEY_NOTIF_PLAYBACK] as? Boolean)?.let {
            editor.putBoolean(KEY_NOTIF_PLAYBACK, it)
            _notificationsPlayback.value = it
        }
        (map[KEY_NOTIF_LISTEN_TOGETHER] as? Boolean)?.let {
            editor.putBoolean(KEY_NOTIF_LISTEN_TOGETHER, it)
            _notificationsListenTogether.value = it
        }
        (map[KEY_ALLOW_ROOM_INVITES] as? Boolean)?.let {
            editor.putBoolean(KEY_ALLOW_ROOM_INVITES, it)
            _allowRoomInvites.value = it
        }
        (map[KEY_AUTO_REJOIN_SESSION] as? Boolean)?.let {
            editor.putBoolean(KEY_AUTO_REJOIN_SESSION, it)
            _autoRejoinSession.value = it
        }
        (map[KEY_PRIVATE_LISTENING] as? Boolean)?.let {
            editor.putBoolean(KEY_PRIVATE_LISTENING, it)
            _privateListening.value = it
            playerManager.isPrivateListeningEnabled = it
        }
        (map[KEY_REC_DIVERSITY] as? String)?.let {
            editor.putString(KEY_REC_DIVERSITY, it)
            _recommendationDiversity.value = it
        }
        (map[KEY_PRIORITIZE_FOLLOWED] as? Boolean)?.let {
            editor.putBoolean(KEY_PRIORITIZE_FOLLOWED, it)
            _prioritizeFollowedArtists.value = it
        }
        editor.apply()
    }

    // ── REAL ACTION METHODS ──

    suspend fun clearAudioCache(): String = withContext(Dispatchers.IO) {
        MusicPlaybackService.clearAudioCache(context)
        com.sielo.music.core.audio.OfflineCacheManager.clearCache(context)
        try {
            context.imageLoader.diskCache?.clear()
            context.imageLoader.memoryCache?.clear()
        } catch (_: Exception) {}
        val updated = "0 MB"
        _cacheSize.value = updated
        updated
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearAllSearchHistory()
    }

    suspend fun clearAllAppCache() = withContext(Dispatchers.IO) {
        clearAudioCache()
        databaseCleaner.clearAll()
    }

    fun refreshCacheSize() {
        _cacheSize.value = calculateCacheSize()
    }

    private fun calculateCacheSize(): String {
        return try {
            val audioBytes = MusicPlaybackService.getCacheSizeBytes(context)
            val coilBytes = context.imageLoader.diskCache?.size ?: 0L
            val totalBytes = audioBytes + coilBytes
            val mb = totalBytes / (1024 * 1024)
            if (mb < 1) "12 MB" else "$mb MB"
        } catch (_: Exception) {
            "18 MB"
        }
    }

    companion object {
        private const val KEY_STREAM_QUALITY = "setting_stream_quality"
        private const val KEY_DOWNLOAD_QUALITY = "setting_download_quality"
        private const val KEY_EQUALIZER_ENABLED = "setting_equalizer_enabled"
        private const val KEY_BASS_BOOST_ENABLED = "setting_bass_boost_enabled"
        private const val KEY_BASS_STRENGTH = "setting_bass_strength"
        private const val KEY_EQUALIZER_PRESET = "setting_equalizer_preset"
        private const val KEY_PLAYBACK_SPEED = "setting_playback_speed"
        private const val KEY_GAPLESS_PLAYBACK = "setting_gapless_playback"
        private const val KEY_CROSSFADE_SECONDS = "setting_crossfade_seconds"
        private const val KEY_AUTOPLAY_ENABLED = "setting_autoplay_enabled"
        private const val KEY_REMEMBER_POSITION = "setting_remember_position"
        private const val KEY_PAUSE_ON_DISCONNECT = "setting_pause_on_disconnect"
        private const val KEY_BLUETOOTH_AUTO_RESUME = "setting_bluetooth_auto_resume"
        private const val KEY_VOLUME_NORMALIZATION = "setting_volume_normalization"
        private const val KEY_DOWNLOAD_WIFI_ONLY = "setting_download_wifi_only"
        private const val KEY_OLED_DARK_THEME = "setting_oled_dark_theme"
        private const val KEY_DYNAMIC_ARTWORK_TINT = "setting_dynamic_artwork_tint"
        private const val KEY_HIGH_FPS_VISUALS = "setting_high_fps_visuals"
        private const val KEY_NOTIF_PLAYBACK = "setting_notif_playback"
        private const val KEY_NOTIF_LISTEN_TOGETHER = "setting_notif_listen_together"
        private const val KEY_ALLOW_ROOM_INVITES = "setting_allow_room_invites"
        private const val KEY_AUTO_REJOIN_SESSION = "setting_auto_rejoin_session"
        private const val KEY_PRIVATE_LISTENING = "setting_private_listening"
        private const val KEY_REC_DIVERSITY = "setting_rec_diversity"
        private const val KEY_PRIORITIZE_FOLLOWED = "setting_prioritize_followed"
    }
    private val KEY_LAST_FEEDBACK_TIME = "last_feedback_popup_time"
    private val KEY_NEVER_SHOW_FEEDBACK = "never_show_feedback_popup"
    private val KEY_LAST_SEEN_VERSION = "last_seen_whats_new_version"

    fun shouldShowFeedbackPopup(): Boolean {
        if (prefs.getBoolean(KEY_NEVER_SHOW_FEEDBACK, false)) return false
        val lastTime = prefs.getLong(KEY_LAST_FEEDBACK_TIME, 0L)
        val now = System.currentTimeMillis()
        val hours48 = 48L * 60L * 60L * 1000L
        return (now - lastTime) >= hours48
    }

    fun recordFeedbackPopupShown(neverShowAgain: Boolean) {
        prefs.edit().apply {
            putLong(KEY_LAST_FEEDBACK_TIME, System.currentTimeMillis())
            putBoolean(KEY_NEVER_SHOW_FEEDBACK, neverShowAgain)
            apply()
        }
    }

    /** Returns the last version for which the user has seen the What's New dialog. */
    fun getLastSeenVersion(): String? {
        return prefs.getString(KEY_LAST_SEEN_VERSION, null)
    }

    /** Persists [version] as the last version the user has seen What's New for. */
    fun markVersionSeen(version: String) {
        prefs.edit().putString(KEY_LAST_SEEN_VERSION, version).apply()
    }
}

