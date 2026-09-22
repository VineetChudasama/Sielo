package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.auth.UserManager
import com.sielo.music.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    val userManager: UserManager,
    private val playerManager: PlayerManager
) : ViewModel() {

    val currentUser = userManager.currentUser

    // ── 1. PLAYBACK & AUDIO ──
    val streamQuality: StateFlow<String> = settingsRepository.streamQuality
    val downloadQuality: StateFlow<String> = settingsRepository.downloadQuality
    val equalizerEnabled: StateFlow<Boolean> = settingsRepository.equalizerEnabled
    val bassBoostEnabled: StateFlow<Boolean> = settingsRepository.bassBoostEnabled
    val bassStrength: StateFlow<Int> = settingsRepository.bassStrength
    val equalizerPreset: StateFlow<String> = settingsRepository.equalizerPreset
    val gaplessPlayback: StateFlow<Boolean> = settingsRepository.gaplessPlayback
    val crossfadeSeconds: StateFlow<Int> = settingsRepository.crossfadeSeconds
    val playbackSpeed: StateFlow<Float> = settingsRepository.playbackSpeed
    val autoplayEnabled: StateFlow<Boolean> = settingsRepository.autoplayEnabled
    val rememberPosition: StateFlow<Boolean> = settingsRepository.rememberPosition

    // ── 2. AUDIO OUTPUT ──
    val pauseOnDisconnect: StateFlow<Boolean> = settingsRepository.pauseOnDisconnect
    val bluetoothAutoResume: StateFlow<Boolean> = settingsRepository.bluetoothAutoResume
    val volumeNormalization: StateFlow<Boolean> = settingsRepository.volumeNormalization

    // ── 3. DOWNLOADS & STORAGE ──
    val downloadWifiOnly: StateFlow<Boolean> = settingsRepository.downloadWifiOnly
    val cacheSize: StateFlow<String> = settingsRepository.cacheSize

    // ── 4. APPEARANCE ──
    val oledDarkTheme: StateFlow<Boolean> = settingsRepository.oledDarkTheme
    val dynamicArtworkTint: StateFlow<Boolean> = settingsRepository.dynamicArtworkTint
    val highFpsVisuals: StateFlow<Boolean> = settingsRepository.highFpsVisuals

    // ── 5. NOTIFICATIONS ──
    val notificationsPlayback: StateFlow<Boolean> = settingsRepository.notificationsPlayback
    val notificationsNewReleases: StateFlow<Boolean> = settingsRepository.notificationsNewReleases
    val notificationsListenTogether: StateFlow<Boolean> = settingsRepository.notificationsListenTogether

    // ── 6. LISTEN TOGETHER ──
    val allowRoomInvites: StateFlow<Boolean> = settingsRepository.allowRoomInvites
    val autoRejoinSession: StateFlow<Boolean> = settingsRepository.autoRejoinSession

    // ── 7. PRIVACY & DATA ──
    val privateListening: StateFlow<Boolean> = settingsRepository.privateListening

    // ── 8. RECOMMENDATIONS ──
    val recommendationDiversity: StateFlow<String> = settingsRepository.recommendationDiversity
    val prioritizeFollowedArtists: StateFlow<Boolean> = settingsRepository.prioritizeFollowedArtists

    fun setStreamQuality(quality: String) {
        settingsRepository.setStreamQuality(quality)
    }

    fun setDownloadQuality(quality: String) {
        settingsRepository.setDownloadQuality(quality)
    }

    fun toggleEqualizer() {
        settingsRepository.toggleEqualizer()
    }

    fun toggleBassBoost() {
        settingsRepository.toggleBassBoost()
    }

    fun setBassStrength(strength: Int) {
        settingsRepository.setBassStrength(strength)
    }

    fun setEqualizerPreset(preset: String) {
        settingsRepository.setEqualizerPreset(preset)
    }

    fun setPlaybackSpeed(speed: Float) {
        settingsRepository.setPlaybackSpeed(speed)
    }

    fun updateProfile(name: String, username: String?, bio: String?) {
        userManager.updateProfile(name, username, bio)
    }

    fun openAuthDialog() {
        userManager.openAuthDialog()
    }

    fun toggleGaplessPlayback() {
        settingsRepository.toggleGaplessPlayback()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        settingsRepository.setCrossfadeSeconds(seconds)
    }

    fun toggleAutoplay() {
        settingsRepository.toggleAutoplay()
    }

    fun toggleRememberPosition() {
        settingsRepository.toggleRememberPosition()
    }

    fun togglePauseOnDisconnect() {
        settingsRepository.togglePauseOnDisconnect()
    }

    fun toggleBluetoothAutoResume() {
        settingsRepository.toggleBluetoothAutoResume()
    }

    fun toggleVolumeNormalization() {
        settingsRepository.toggleVolumeNormalization()
    }

    fun toggleDownloadWifiOnly() {
        settingsRepository.toggleDownloadWifiOnly()
    }

    fun toggleOledDarkTheme() {
        settingsRepository.toggleOledDarkTheme()
    }

    fun toggleDynamicArtworkTint() {
        settingsRepository.toggleDynamicArtworkTint()
    }

    fun toggleHighFpsVisuals() {
        settingsRepository.toggleHighFpsVisuals()
    }

    fun toggleNotificationsPlayback() {
        settingsRepository.toggleNotificationsPlayback()
    }

    fun toggleNotificationsNewReleases() {
        settingsRepository.toggleNotificationsNewReleases()
    }

    fun toggleNotificationsListenTogether() {
        settingsRepository.toggleNotificationsListenTogether()
    }

    fun toggleAllowRoomInvites() {
        settingsRepository.toggleAllowRoomInvites()
    }

    fun toggleAutoRejoinSession() {
        settingsRepository.toggleAutoRejoinSession()
    }

    fun togglePrivateListening() {
        settingsRepository.togglePrivateListening()
    }

    fun setRecommendationDiversity(level: String) {
        settingsRepository.setRecommendationDiversity(level)
    }

    fun togglePrioritizeFollowedArtists() {
        settingsRepository.togglePrioritizeFollowedArtists()
    }

    fun clearAudioCache(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val updated = settingsRepository.clearAudioCache()
            
            onComplete(updated)
        }
    }

    fun clearSearchHistory(onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearSearchHistory()
            onComplete()
        }
    }

    fun clearAllAppCache(onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearAllAppCache()
            onComplete()
        }
    }

    fun signOut(onComplete: () -> Unit) {
        userManager.signOut()
        onComplete()
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            userManager.deleteAccount()
            settingsRepository.clearAllAppCache()
            onComplete()
        }
    }
    fun shouldShowFeedbackPopup(): Boolean {
        return settingsRepository.shouldShowFeedbackPopup()
    }

    fun recordFeedbackPopupShown(neverShowAgain: Boolean) {
        settingsRepository.recordFeedbackPopupShown(neverShowAgain)
    }
}


