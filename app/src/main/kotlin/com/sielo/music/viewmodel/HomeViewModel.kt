package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.entity.FavoriteTrackEntity
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val innerTubeClient: InnerTubeClient,
    private val playerManager: PlayerManager,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val listeningHistoryDao: ListeningHistoryDao
) : ViewModel() {

    private val _greeting = MutableStateFlow(computeGreeting())
    val greeting: StateFlow<String> = _greeting.asStateFlow()

    private val _selectedMood = MutableStateFlow("⚡ Top Hits")
    val selectedMood: StateFlow<String> = _selectedMood.asStateFlow()

    private val _trendingTracks = MutableStateFlow<List<SieloTrack>>(emptyList())
    val trendingTracks: StateFlow<List<SieloTrack>> = _trendingTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val recentHistory: StateFlow<List<ListeningEventEntity>> = listeningHistoryDao.getRecentHistory(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites = favoriteTrackDao.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadFeed("Top Hits 2026")
    }

    private fun computeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Late Night Session"
        }
    }

    fun selectMood(mood: String) {
        _selectedMood.value = mood
        val cleanedQuery = mood.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
        loadFeed(cleanedQuery)
    }

    private fun loadFeed(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val tracks = innerTubeClient.search(query)
            _trendingTracks.value = if (tracks.isNotEmpty()) tracks else defaultStarterTracks()
            _isLoading.value = false
        }
    }

    fun playTrack(track: SieloTrack, queue: List<SieloTrack>) {
        playerManager.playTrack(track, queue)
    }

    fun playHistoryEvent(event: ListeningEventEntity) {
        val track = SieloTrack(
            id = event.songId,
            title = event.songTitle,
            artist = event.artistName,
            thumbnailUrl = event.thumbnailUrl
        )
        playerManager.playTrack(track, listOf(track))
    }

    fun toggleFavorite(track: SieloTrack) {
        viewModelScope.launch {
            favoriteTrackDao.insertFavorite(
                FavoriteTrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    thumbnailUrl = track.thumbnailUrl
                )
            )
        }
    }

    val similarArtists: List<ArtistProfile> = listOf(
        ArtistProfile("SZA", "https://i.scdn.co/image/ab6761610000e5ebc58f000b0e50085ef1ad7944"),
        ArtistProfile("The Weeknd", "https://i.scdn.co/image/ab6761610000e5eb214f3cf1cbe7139c1e26ffbb"),
        ArtistProfile("H.E.R.", "https://i.scdn.co/image/ab6761610000e5eb1e7d0130ee99df73ec78e4d2"),
        ArtistProfile("PARTY", "https://i.scdn.co/image/ab6761610000e5eb25b6a7b643a6ec00827291a9"),
        ArtistProfile("Jhené Aiko", "https://i.scdn.co/image/ab6761610000e5eb14ef4b47098e9fc7df2c2b38"),
        ArtistProfile("Dua Lipa", "https://i.scdn.co/image/ab6761610000e5ebd42a27db3286b58553da8858"),
        ArtistProfile("Metro Boomin", "https://i.scdn.co/image/ab6761610000e5eb74ce2b027d11f7447477c776"),
        ArtistProfile("Billie Eilish", "https://i.scdn.co/image/ab6761610000e5ebd8b9980db67272cb4d4c3dfe"),
        ArtistProfile("Arijit Singh", "https://i.scdn.co/image/ab6761610000e5eb0261696c5df3be99da6ed3f3")
    )

    val featuredStations: List<StationItem> = listOf(
        StationItem("Top Hits Station 🎧", "Sielo Official", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80", "Top Hits 2026"),
        StationItem("Midnight Chill 🌙", "Atmospheric Waves", "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80", "Midnight Chill R&B"),
        StationItem("Synthwave FM 🌊", "Retro Cyberdeck", "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80", "Synthwave Cyberpunk"),
        StationItem("Bollywood Anthems 🔥", "T-Series & Friends", "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80", "Arijit Singh Hits")
    )

    val customPlaylists: List<PlaylistCardItem> = listOf(
        PlaylistCardItem("Liked Music", "Favorites • Auto-synced", "", isLiked = true),
        PlaylistCardItem("Night Drive Sessions", "50 songs", "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80"),
        PlaylistCardItem("Chill Frequency", "42 songs", "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=600&auto=format&fit=crop&q=80"),
        PlaylistCardItem("Hi-Fi Acoustic", "35 songs", "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80")
    )

    fun playArtistRadio(artist: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val tracks = innerTubeClient.search(artist)
            if (tracks.isNotEmpty()) {
                playTrack(tracks.first(), tracks)
            }
            _isLoading.value = false
        }
    }

    fun playStation(station: StationItem) {
        viewModelScope.launch {
            _isLoading.value = true
            val tracks = innerTubeClient.search(station.seedQuery)
            if (tracks.isNotEmpty()) {
                playTrack(tracks.first(), tracks)
            }
            _isLoading.value = false
        }
    }

    private fun defaultStarterTracks(): List<SieloTrack> = listOf(
        SieloTrack("fHI8X4OXluQ", "Blinding Lights", "The Weeknd", durationText = "3:20", thumbnailUrl = "https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg"),
        SieloTrack("4NRXx6U8ABQ", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50", thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"),
        SieloTrack("b1kbLwvqugk", "Die For You", "The Weeknd", durationText = "4:20", thumbnailUrl = "https://i.ytimg.com/vi/b1kbLwvqugk/hqdefault.jpg"),
        SieloTrack("34Na4j8AVgA", "Save Your Tears", "The Weeknd", durationText = "3:35", thumbnailUrl = "https://i.ytimg.com/vi/34Na4j8AVgA/hqdefault.jpg"),
        SieloTrack("L_LUpnjgPso", "After Hours", "The Weeknd", durationText = "6:01", thumbnailUrl = "https://i.ytimg.com/vi/L_LUpnjgPso/hqdefault.jpg")
    )
}

data class ArtistProfile(
    val name: String,
    val imageUrl: String
)

data class StationItem(
    val title: String,
    val subtitle: String,
    val bannerUrl: String,
    val seedQuery: String
)

data class PlaylistCardItem(
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val isLiked: Boolean = false
)
