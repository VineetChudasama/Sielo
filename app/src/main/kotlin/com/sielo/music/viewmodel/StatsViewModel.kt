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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class StatsTimeframe(val label: String, val days: Int) {
    WEEK_7D("7 Days", 7),
    MONTH_30D("1 Month", 30),
    ALL_TIME("All Time", 3650)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _selectedTimeframe = MutableStateFlow(StatsTimeframe.MONTH_30D)
    val selectedTimeframe: StateFlow<StatsTimeframe> = _selectedTimeframe.asStateFlow()

    fun selectTimeframe(timeframe: StatsTimeframe) {
        _selectedTimeframe.value = timeframe
    }

    private fun sinceMsForTimeframe(timeframe: StatsTimeframe): Long {
        return if (timeframe == StatsTimeframe.ALL_TIME) {
            0L
        } else {
            System.currentTimeMillis() - (timeframe.days.toLong() * 24 * 60 * 60 * 1000L)
        }
    }

    val totalListeningTime: StateFlow<Long?> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getTotalListeningTime(sinceMsForTimeframe(tf)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalUniqueSongs: StateFlow<Int> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getTotalUniqueSongs(sinceMsForTimeframe(tf)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUniqueArtists: StateFlow<Int> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getTotalUniqueArtists(sinceMsForTimeframe(tf)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalStreamCount: StateFlow<Int> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getTotalStreamCount(sinceMsForTimeframe(tf)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topArtists: StateFlow<List<ArtistStat>> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getTopArtists(sinceMsForTimeframe(tf), 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allArtists: StateFlow<List<ArtistStat>> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getTopArtists(sinceMsForTimeframe(tf), 100) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSongs: StateFlow<List<SongStat>> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getTopSongs(sinceMsForTimeframe(tf), 10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSongs: StateFlow<List<SongStat>> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getTopSongs(sinceMsForTimeframe(tf), 100) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streamHistory: StateFlow<List<com.sielo.music.core.database.entity.ListeningEventEntity>> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getStreamHistory(sinceMsForTimeframe(tf), 100) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hourlyDistribution: StateFlow<List<HourCount>> = _selectedTimeframe
        .flatMapLatest { tf -> listeningHistoryDao.getHourlyDistribution(sinceMsForTimeframe(tf)) }
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

    fun playHistoryEvent(event: com.sielo.music.core.database.entity.ListeningEventEntity) {
        val track = SieloTrack(
            id = event.songId,
            title = event.songTitle,
            artist = event.artistName,
            album = event.albumName,
            thumbnailUrl = event.thumbnailUrl
        )
        playerManager.playTrack(track, listOf(track))
    }

    fun computeListenerPersona(hourly: List<HourCount>): String {
        if (hourly.isEmpty()) return "Sonic Explorer"
        val nightPlays = hourly.filter { it.hourOfDay in 22..23 || it.hourOfDay in 0..4 }.sumOf { it.count }
        val eveningPlays = hourly.filter { it.hourOfDay in 17..21 }.sumOf { it.count }
        val morningPlays = hourly.filter { it.hourOfDay in 6..11 }.sumOf { it.count }

        return when {
            nightPlays >= eveningPlays && nightPlays >= morningPlays -> "🌙 Night Owl Audiophile"
            morningPlays >= eveningPlays -> "☀️ Early Bird Listener"
            eveningPlays >= morningPlays -> "⚡ Evening Pulse Seeker"
            else -> "✨ High-Vibe Connoisseur"
        }
    }

    fun computePeakHourSlot(hourly: List<HourCount>): String {
        if (hourly.isEmpty()) return "—"
        val maxHour = hourly.maxByOrNull { it.count }?.hourOfDay ?: return "—"
        val startPeriod = if (maxHour < 12) "${maxHour.coerceAtLeast(1)} AM" else "${(maxHour - 12).let { if (it == 0) 12 else it }} PM"
        val nextHour = (maxHour + 2) % 24
        val endPeriod = if (nextHour < 12) "${nextHour.coerceAtLeast(1)} AM" else "${(nextHour - 12).let { if (it == 0) 12 else it }} PM"
        return "$startPeriod - $endPeriod"
    }
}
