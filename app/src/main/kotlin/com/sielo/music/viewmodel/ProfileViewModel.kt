package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.dao.ListeningHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val favoriteTrackDao: FavoriteTrackDao,
    private val listeningHistoryDao: ListeningHistoryDao
) : ViewModel() {

    val favoritesCount = favoriteTrackDao.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamQuality = MutableStateFlow("Master (320kbps Opus)")
    val streamQuality: StateFlow<String> = _streamQuality.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(true)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _gaplessPlayback = MutableStateFlow(true)
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()

    private val _cacheSize = MutableStateFlow("142 MB")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    fun setStreamQuality(quality: String) {
        _streamQuality.value = quality
    }

    fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
    }

    fun toggleGapless() {
        _gaplessPlayback.value = !_gaplessPlayback.value
    }

    fun clearCache() {
        viewModelScope.launch {
            _cacheSize.value = "0 MB"
        }
    }
}
