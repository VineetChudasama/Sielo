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

    private val _selectedPlaylist = MutableStateFlow<PlaylistCardItem?>(null)
    val selectedPlaylist: StateFlow<PlaylistCardItem?> = _selectedPlaylist.asStateFlow()

    private val _playlistTracks = MutableStateFlow<List<SieloTrack>>(emptyList())
    val playlistTracks: StateFlow<List<SieloTrack>> = _playlistTracks.asStateFlow()

    private val _isPlaylistLoading = MutableStateFlow(false)
    val isPlaylistLoading: StateFlow<Boolean> = _isPlaylistLoading.asStateFlow()

    private val _selectedArtist = MutableStateFlow<com.sielo.music.core.network.models.ArtistDetails?>(null)
    val selectedArtist: StateFlow<com.sielo.music.core.network.models.ArtistDetails?> = _selectedArtist.asStateFlow()

    private val _isArtistLoading = MutableStateFlow(false)
    val isArtistLoading: StateFlow<Boolean> = _isArtistLoading.asStateFlow()

    init {
        loadFeed("Top Hits 2026")
    }

    fun openArtist(artistName: String, imageUrl: String? = null) {
        viewModelScope.launch {
            _isArtistLoading.value = true
            _selectedArtist.value = com.sielo.music.core.network.models.ArtistDetails(
                id = artistName,
                name = artistName,
                imageUrl = imageUrl
            )
            val full = innerTubeClient.getArtistDetails(artistName, imageUrl)
            if (full != null) {
                _selectedArtist.value = full
            }
            _isArtistLoading.value = false
        }
    }

    fun closeArtist() {
        _selectedArtist.value = null
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
            val cleanTracks = if (tracks.isNotEmpty()) {
                tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
            } else defaultStarterTracks()
            _trendingTracks.value = cleanTracks
            _isLoading.value = false
        }
    }

    fun openPlaylist(playlist: PlaylistCardItem) {
        _selectedPlaylist.value = playlist
        viewModelScope.launch {
            _isPlaylistLoading.value = true
            if (playlist.isLiked) {
                val favList = favorites.value
                _playlistTracks.value = favList.map {
                    SieloTrack(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        thumbnailUrl = it.thumbnailUrl
                    )
                }.distinctBy { it.id }
            } else {
                val tracks = innerTubeClient.search(playlist.seedQuery)
                val cleanTracks = if (tracks.isNotEmpty()) {
                    tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                } else defaultGenreTracks(playlist.genre)
                _playlistTracks.value = cleanTracks
            }
            _isPlaylistLoading.value = false
        }
    }

    fun closePlaylist() {
        _selectedPlaylist.value = null
        _playlistTracks.value = emptyList()
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
        ArtistProfile("SZA", "https://yt3.googleusercontent.com/yLBjfGExL_iEyNmOd5VjEVt6tQWg8Upr1mpafHQfsv-MU3875DnCI74VsslG0lZtiGjg0lf1wTk=s500-c-k-c0x00ffffff-no-rj"),
        ArtistProfile("The Weeknd", "https://yt3.googleusercontent.com/WHvw1ak1FcJaHeEiTmG2iN0dqEjjPxAtT_tA8ruJ3MlNr9I-RHsAur1iAenYeQN_d6LNPH2Z8Ic=s500-c-k-c0x00ffffff-no-rj"),
        ArtistProfile("H.E.R.", "https://yt3.googleusercontent.com/OAONz3oAx1BmChjbCCG9ZFMGiOXsBkoTX-qc2noEI9Aik7hK4FuV1n2EiiEZZJ4M3raCiuOdkQ=s500-c-k-c0x00ffffff-no-rj"),
        ArtistProfile("Kendrick Lamar", "https://yt3.googleusercontent.com/j1szYhuen1uT1D1icpjxHMFyBc0xINWK1eMtSzrB0TL5jliB7t3JB_wJ6UA9twV7VelxpKEc=s500-c-k-c0x00ffffff-no-rj"),
        ArtistProfile("Dua Lipa", "https://yt3.googleusercontent.com/c3upBFWLu55hnBvqncQS9ZEF_hkvHsNTQiB7m7ZYYavLFMzfyn9Bwo-1VF4HSPGo3G2EdwGtgWg=s500-c-k-c0x00ffffff-no-rj"),
        ArtistProfile("Metro Boomin", "https://yt3.googleusercontent.com/K3VTXT-b8S-B4G-aqWTHhehGM8z7qJbMIIUGpsbkLbIChdj_-uXKhmNZdP0mfj4fwHoIQw5hYA=s500-c-k-c0x00ffffff-no-rj"),
        ArtistProfile("Billie Eilish", "https://yt3.googleusercontent.com/dirvtoDAmx-u0UR76-pxfhYL6Wxj2vfL2geUcxDwk62tTWWhGG6QDGc63RG3NdOz38-yBwRHDQ=s500-c-k-c0x00ffffff-no-rj"),
        ArtistProfile("Arijit Singh", "https://yt3.googleusercontent.com/DcEzZrPCQRSSs47rMbdJ3UJkQUCN3X8SKf8aCnvOgd2BmPihAz-0jBGJgEVh9_P8EiSBVNyixDs=s500-c-k-c0x00ffffff-no-rj")
    )

    val featuredStations: List<StationItem> = listOf(
        StationItem("Top Hits Station 🎧", "Global Hot 100", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80", "Top Hits 2026"),
        StationItem("Midnight Chill 🌙", "Atmospheric Waves", "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80", "Midnight Chill R&B"),
        StationItem("Synthwave FM 🌊", "Retro Cyberdeck", "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80", "Synthwave Cyberpunk"),
        StationItem("Bollywood Anthems 🔥", "T-Series & Friends", "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80", "Arijit Singh Hits")
    )

    val customPlaylists: List<PlaylistCardItem> = listOf(
        PlaylistCardItem(
            id = "liked",
            title = "Liked Songs",
            subtitle = "Vault • Auto-synced",
            genre = "Your Favorites",
            coverUrl = "",
            seedQuery = "",
            isLiked = true
        ),
        PlaylistCardItem(
            id = "synthwave",
            title = "Synthwave Cyberpunk",
            subtitle = "Neon vibes & retro synths",
            genre = "Electronic",
            coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            seedQuery = "Synthwave Retro 80s Cyberpunk"
        ),
        PlaylistCardItem(
            id = "midnight_rnb",
            title = "Midnight R&B Sessions",
            subtitle = "Smooth late night vibrations",
            genre = "R&B / Soul",
            coverUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80",
            seedQuery = "Midnight R&B Soul Hits"
        ),
        PlaylistCardItem(
            id = "bollywood",
            title = "Bollywood Romance",
            subtitle = "Soulful melodies & ballads",
            genre = "Bollywood",
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            seedQuery = "Arijit Singh Romantic Melodies"
        ),
        PlaylistCardItem(
            id = "lofi",
            title = "Chill Lo-Fi Study",
            subtitle = "Beats to focus & unwind",
            genre = "Lo-Fi Beats",
            coverUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=600&auto=format&fit=crop&q=80",
            seedQuery = "Chill Lo Fi Study Beats"
        ),
        PlaylistCardItem(
            id = "acoustic",
            title = "Hi-Fi Acoustic & Folk",
            subtitle = "Intimate vocals & guitars",
            genre = "Acoustic",
            coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            seedQuery = "Acoustic Pop Folk Melodies"
        )
    )

    fun playArtistRadio(artist: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val tracks = innerTubeClient.search(artist)
            if (tracks.isNotEmpty()) {
                val clean = tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                playTrack(clean.first(), clean)
            }
            _isLoading.value = false
        }
    }

    fun playStation(station: StationItem) {
        viewModelScope.launch {
            _isLoading.value = true
            val tracks = innerTubeClient.search(station.seedQuery)
            if (tracks.isNotEmpty()) {
                val clean = tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                playTrack(clean.first(), clean)
            }
            _isLoading.value = false
        }
    }

    private fun defaultStarterTracks(): List<SieloTrack> = listOf(
        SieloTrack("fHI8X4OXluQ", "Blinding Lights", "The Weeknd", durationText = "3:20", thumbnailUrl = "https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg"),
        SieloTrack("4NRXx6U8ABQ", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50", thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"),
        SieloTrack("b1kbLwvqugk", "Die For You", "The Weeknd", durationText = "4:20", thumbnailUrl = "https://i.ytimg.com/vi/b1kbLwvqugk/hqdefault.jpg"),
        SieloTrack("34Na4j8AVgA", "Save Your Tears", "The Weeknd", durationText = "3:35", thumbnailUrl = "https://i.ytimg.com/vi/34Na4j8AVgA/hqdefault.jpg"),
        SieloTrack("L_LUpnjgPso", "After Hours", "The Weeknd", durationText = "6:01", thumbnailUrl = "https://i.ytimg.com/vi/L_LUpnjgPso/hqdefault.jpg"),
        SieloTrack("sT98E_u6k8Q", "Levitating", "Dua Lipa", durationText = "3:23", thumbnailUrl = "https://i.ytimg.com/vi/sT98E_u6k8Q/hqdefault.jpg"),
        SieloTrack("JGwWNGJdvx8", "Shape of You", "Ed Sheeran", durationText = "3:53", thumbnailUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg"),
        SieloTrack("k2qgadSvNyU", "New Rules", "Dua Lipa", durationText = "3:29", thumbnailUrl = "https://i.ytimg.com/vi/k2qgadSvNyU/hqdefault.jpg"),
        SieloTrack("OPf0YbXqDm0", "Uptown Funk", "Mark Ronson ft. Bruno Mars", durationText = "4:30", thumbnailUrl = "https://i.ytimg.com/vi/OPf0YbXqDm0/hqdefault.jpg"),
        SieloTrack("0VwhorQBxU4", "Stay", "The Kid LAROI & Justin Bieber", durationText = "2:21", thumbnailUrl = "https://i.ytimg.com/vi/0VwhorQBxU4/hqdefault.jpg"),
        SieloTrack("hT_nvWreIhg", "Counting Stars", "OneRepublic", durationText = "4:17", thumbnailUrl = "https://i.ytimg.com/vi/hT_nvWreIhg/hqdefault.jpg"),
        SieloTrack("kJQP7kiw5Fk", "Despacito", "Luis Fonsi ft. Daddy Yankee", durationText = "3:48", thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg"),
        SieloTrack("9bZkp7q19f0", "Gangnam Style", "PSY", durationText = "3:39", thumbnailUrl = "https://i.ytimg.com/vi/9bZkp7q19f0/hqdefault.jpg"),
        SieloTrack("YQHsXMglC9A", "Hello", "Adele", durationText = "4:55", thumbnailUrl = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg")
    )

    private fun defaultGenreTracks(genre: String): List<SieloTrack> = when {
        genre.contains("Electronic", ignoreCase = true) -> listOf(
            SieloTrack("4NRXx6U8ABQ", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50", thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"),
            SieloTrack("OPf0YbXqDm0", "Uptown Funk", "Mark Ronson", durationText = "4:30", thumbnailUrl = "https://i.ytimg.com/vi/OPf0YbXqDm0/hqdefault.jpg")
        )
        genre.contains("Acoustic", ignoreCase = true) -> listOf(
            SieloTrack("JGwWNGJdvx8", "Shape of You", "Ed Sheeran", durationText = "3:53", thumbnailUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg"),
            SieloTrack("YQHsXMglC9A", "Hello", "Adele", durationText = "4:55", thumbnailUrl = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg")
        )
        else -> defaultStarterTracks().take(6)
    }
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
    val id: String,
    val title: String,
    val subtitle: String,
    val genre: String,
    val coverUrl: String,
    val seedQuery: String,
    val isLiked: Boolean = false
)
