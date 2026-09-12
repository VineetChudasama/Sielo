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
import kotlinx.coroutines.flow.combine
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

    // 5 Most Recently Played Unique Tracks (guaranteed exact 5, replay maintains position without dropping others)
    val recentPlayedSongs: StateFlow<List<ListeningEventEntity>> = listeningHistoryDao.getRecentUniquePlayedSongs(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks played before 2 days ago that user hasn't listened to in the last 2 days
    val rediscoveredFavorites: StateFlow<List<ListeningEventEntity>> = listeningHistoryDao.getRediscoveredFavorites(
        twoDaysAgoMs = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000L),
        limit = 5
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Most listened artist dynamically computed from listening history
    val topArtistStat = listeningHistoryDao.getTopArtists(sinceMs = 0L, limit = 1)
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

    // Trending Genres State
    val trendingGenres: List<GenreItem> = listOf(
        GenreItem("pop", "Pop", "🔥", "Top Global Pop Hits 2026", 0xFF6366F1, "Chart-topping global anthems"),
        GenreItem("hiphop", "Hip-Hop & Rap", "⚡", "Hip Hop Rap Bangers 2026", 0xFFEC4899, "Heavy 808s & modern flow"),
        GenreItem("rnb", "R&B / Soul", "🌙", "Midnight R&B Soul Hits", 0xFF8B5CF6, "Late night sensual melodies"),
        GenreItem("indie", "Indie & Alt", "🎸", "Indie Alternative Rock Hits", 0xFF10B981, "Dreamy guitars & indie vibe"),
        GenreItem("edm", "Electronic & EDM", "🌊", "EDM Dance Club Hits 2026", 0xFF06B6D4, "High energy synths & festival beats"),
        GenreItem("bollywood", "Bollywood Melodies", "🪕", "Bollywood Romantic Hits Arijit Singh", 0xFFF59E0B, "Soulful Indian melodies & romance"),
        GenreItem("lofi", "Chill Lo-Fi", "☕", "Chill Lo Fi Study Beats", 0xFF64748B, "Warm tape hiss & relaxing beats"),
        GenreItem("rock", "Rock & Metal", "🥁", "Modern Rock Guitar Anthems", 0xFFEF4444, "Raw guitars & stadium anthems"),
        GenreItem("acoustic", "Acoustic & Folk", "🌾", "Acoustic Pop Folk Melodies", 0xFFD97706, "Intimate strings & stripped vocals"),
        GenreItem("latin", "Latin & Reggaeton", "🌴", "Latin Reggaeton Fiesta Hits", 0xFF14B8A6, "Sun-soaked infectious rhythms")
    )

    private val _selectedGenre = MutableStateFlow<GenreItem?>(null)
    val selectedGenre: StateFlow<GenreItem?> = _selectedGenre.asStateFlow()

    private val _genreTracks = MutableStateFlow<List<SieloTrack>>(emptyList())
    val genreTracks: StateFlow<List<SieloTrack>> = _genreTracks.asStateFlow()

    private val _isGenreLoading = MutableStateFlow(false)
    val isGenreLoading: StateFlow<Boolean> = _isGenreLoading.asStateFlow()

    init {
        loadFeed("Top Hits 2026")
    }

    fun openGenre(genre: GenreItem) {
        _selectedGenre.value = genre
        viewModelScope.launch {
            _isGenreLoading.value = true
            val tracks = innerTubeClient.search(genre.seedQuery)
            val clean = if (tracks.isNotEmpty()) {
                tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
            } else defaultGenreTracks(genre.name)
            _genreTracks.value = clean
            _isGenreLoading.value = false
        }
    }

    fun closeGenre() {
        _selectedGenre.value = null
        _genreTracks.value = emptyList()
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

    private val _dynamicSimilarArtists = MutableStateFlow<List<ArtistProfile>>(emptyList())
    val dynamicSimilarArtists: StateFlow<List<ArtistProfile>> = _dynamicSimilarArtists.asStateFlow()

    private var lastResolvedArtist: String? = null

    fun loadDynamicSimilarArtists(topArtistName: String) {
        if (topArtistName == lastResolvedArtist && _dynamicSimilarArtists.value.isNotEmpty()) return
        lastResolvedArtist = topArtistName
        viewModelScope.launch {
            val names = getSimilarArtistNames(topArtistName)
            val list = mutableListOf<ArtistProfile>()
            for (name in names) {
                try {
                    val searchResult = innerTubeClient.searchArtists(name)
                    val img = searchResult.firstOrNull()?.imageUrl
                    val finalImg = if (!img.isNullOrBlank() && !img.contains("default-music") && !img.contains("default-film")) {
                        img
                    } else {
                        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80"
                    }
                    list.add(ArtistProfile(name, finalImg))
                } catch (e: Exception) {
                    list.add(ArtistProfile(name, "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80"))
                }
            }
            if (list.isNotEmpty()) {
                _dynamicSimilarArtists.value = list
            }
        }
    }

    private fun getSimilarArtistNames(topArtistName: String): List<String> {
        val lower = topArtistName.lowercase()
        return when {
            lower.contains("sza") || lower.contains("summer walker") || lower.contains("jhené") -> listOf(
                "Summer Walker", "H.E.R.", "Frank Ocean", "Daniel Caesar", "Jhené Aiko"
            )
            lower.contains("weeknd") || lower.contains("bruno mars") || lower.contains("dua lipa") -> listOf(
                "The Weeknd", "Dua Lipa", "Bruno Mars", "Post Malone", "Khalid"
            )
            lower.contains("kendrick") || lower.contains("drake") || lower.contains("travis") || lower.contains("metro") -> listOf(
                "Kendrick Lamar", "Drake", "J. Cole", "Travis Scott", "21 Savage"
            )
            lower.contains("arijit") || lower.contains("atif") || lower.contains("shreya") -> listOf(
                "Arijit Singh", "Atif Aslam", "Shreya Ghoshal", "Jubin Nautiyal", "Armaan Malik"
            )
            lower.contains("taylor") || lower.contains("olivia") || lower.contains("billie") -> listOf(
                "Taylor Swift", "Olivia Rodrigo", "Billie Eilish", "Sabrina Carpenter", "Lorde"
            )
            else -> listOf(
                "The Weeknd", "SZA", "Kendrick Lamar", "Dua Lipa", "Taylor Swift"
            )
        }
    }

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

    private fun defaultStarterTracks(): List<SieloTrack> = listOf(
        SieloTrack("fW-Mxsnu", "Blinding Lights", "The Weeknd", durationText = "3:20"),
        SieloTrack("TcDP-KUl", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50"),
        SieloTrack("9q41tYDn", "Die For You", "The Weeknd", durationText = "4:20"),
        SieloTrack("tvxo4Jm0", "Save Your Tears", "The Weeknd", durationText = "3:35"),
        SieloTrack("NIVEQweX", "After Hours", "The Weeknd", durationText = "6:01"),
        SieloTrack("3IoDK8qI", "Levitating", "Dua Lipa", durationText = "3:23"),
        SieloTrack("wwSCc15h", "Shape of You", "Ed Sheeran", durationText = "3:53"),
        SieloTrack("EWoDxjbu", "New Rules", "Dua Lipa", durationText = "3:29"),
        SieloTrack("wLxoOff5", "Uptown Funk", "Mark Ronson ft. Bruno Mars", durationText = "4:30"),
        SieloTrack("kd8JSDbB", "Stay", "The Kid LAROI & Justin Bieber", durationText = "2:21")
    )

    private fun defaultGenreTracks(genre: String): List<SieloTrack> = when {
        genre.contains("Electronic", ignoreCase = true) || genre.contains("EDM", ignoreCase = true) -> listOf(
            SieloTrack("TcDP-KUl", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50"),
            SieloTrack("wLxoOff5", "Uptown Funk", "Mark Ronson", durationText = "4:30")
        )
        genre.contains("Acoustic", ignoreCase = true) -> listOf(
            SieloTrack("wwSCc15h", "Shape of You", "Ed Sheeran", durationText = "3:53"),
            SieloTrack("3IoDK8qI", "Levitating", "Dua Lipa", durationText = "3:23")
        )
        else -> defaultStarterTracks().take(6)
    }
}

data class GenreItem(
    val id: String,
    val name: String,
    val icon: String,
    val seedQuery: String,
    val accentColor: Long,
    val description: String
)

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
