package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.database.dao.ArtistStat
import com.sielo.music.core.database.dao.HourCount
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.dao.SongStat
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val playerManager: PlayerManager
) : ViewModel() {

    // Stats for the past 30 days
    private val thirtyDaysAgoMs: Long = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

    val totalListeningTime: StateFlow<Long?> = listeningHistoryDao.getTotalListeningTime(thirtyDaysAgoMs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalUniqueSongs: StateFlow<Int> = listeningHistoryDao.getTotalUniqueSongs(thirtyDaysAgoMs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUniqueArtists: StateFlow<Int> = listeningHistoryDao.getTotalUniqueArtists(thirtyDaysAgoMs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topArtists: StateFlow<List<ArtistStat>> = listeningHistoryDao.getTopArtists(thirtyDaysAgoMs, 5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSongs: StateFlow<List<SongStat>> = listeningHistoryDao.getTopSongs(thirtyDaysAgoMs, 10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hourlyDistribution: StateFlow<List<HourCount>> = listeningHistoryDao.getHourlyDistribution(thirtyDaysAgoMs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playSongStat(stat: SongStat) {
        val track = SieloTrack(
            id = stat.songId,
            title = stat.songTitle,
            artist = stat.artistName,
            thumbnailUrl = stat.thumbnailUrl
        )
        playerManager.playTrack(track, listOf(track))
    }
}
