package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.dao.SearchHistoryDao
import com.sielo.music.core.database.dao.SearchPlayHistoryDao
import com.sielo.music.core.database.entity.FavoriteTrackEntity
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.core.database.entity.SearchHistoryEntity
import com.sielo.music.core.database.entity.SearchPlayHistoryEntity
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.models.ArtistDetails
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val innerTubeClient: InnerTubeClient,
    private val playerManager: PlayerManager,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val searchPlayHistoryDao: SearchPlayHistoryDao,
    private val listeningHistoryDao: ListeningHistoryDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterCategory = MutableStateFlow("All")
    val filterCategory: StateFlow<String> = _filterCategory.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SieloTrack>>(emptyList())
    val searchResults: StateFlow<List<SieloTrack>> = _searchResults.asStateFlow()

    private val _artistResults = MutableStateFlow<List<SieloArtist>>(emptyList())
    val artistResults: StateFlow<List<SieloArtist>> = _artistResults.asStateFlow()

    private val _selectedArtist = MutableStateFlow<ArtistDetails?>(null)
    val selectedArtist: StateFlow<ArtistDetails?> = _selectedArtist.asStateFlow()

    private val _isArtistLoading = MutableStateFlow(false)
    val isArtistLoading: StateFlow<Boolean> = _isArtistLoading.asStateFlow()

    private val _selectedCategory = MutableStateFlow<BrowseCategory?>(null)
    val selectedCategory: StateFlow<BrowseCategory?> = _selectedCategory.asStateFlow()

    private val _categoryTracks = MutableStateFlow<List<SieloTrack>>(emptyList())
    val categoryTracks: StateFlow<List<SieloTrack>> = _categoryTracks.asStateFlow()

    private val _isCategoryLoading = MutableStateFlow(false)
    val isCategoryLoading: StateFlow<Boolean> = _isCategoryLoading.asStateFlow()

    val categoriesList: List<BrowseCategory> = listOf(
        BrowseCategory("pop", "Pop & Hits", listOf("Top Global Pop Hits 2026", "Viral Pop Anthems", "Trending Pop Hits Global", "Top Dance Pop"), "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop", 0xFF8338EC, "Chart-topping global anthems & pop essentials"),
        BrowseCategory("hiphop", "Hip-Hop & R&B", listOf("Top Hip Hop R&B Hits", "Hip Hop Rap Bangers 2026", "Trending Melodic Rap", "Midnight R&B Soul"), "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?q=80&w=600&auto=format&fit=crop", 0xFFD4A373, "Heavy 808s, lyrical flow & midnight R&B"),
        BrowseCategory("lofi", "Chill & Lo-Fi", listOf("Chill Lo-Fi Study Beats", "Lofi Fruits Chill", "Coffee Shop Lofi Chillhop", "Midnight Focus Lofi"), "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?q=80&w=600&auto=format&fit=crop", 0xFF2A9D8F, "Relaxing ambient beats for study & focus"),
        BrowseCategory("indie", "Indie & Alt", listOf("Top Indie Alternative Rock Hits", "Dreamy Indie Pop Melodies", "Trending Alt Indie Hits", "Indie Folk Guitars"), "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?q=80&w=600&auto=format&fit=crop", 0xFFE76F51, "Dreamy guitars, poetic lyrics & indie vibe"),
        BrowseCategory("edm", "Electronic & Dance", listOf("Top EDM Dance Electronic Hits", "EDM Dance Club Hits 2026", "Electronic Festival Bangers", "House Dance Hits"), "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop", 0xFF00B4D8, "High-energy festival beats & club remixes"),
        BrowseCategory("bollywood", "Bollywood & Sufi", listOf("Top Bollywood Romantic Hits Arijit Singh", "Bollywood Love Melodies 2026", "Soulful Hindi Romance Hits", "Bollywood Trending Songs"), "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop", 0xFFE63946, "Soulful Indian melodies & romantic tracks"),
        BrowseCategory("rock", "Rock & Classic", listOf("Top Classic Modern Rock Hits", "Modern Rock Guitar Anthems", "Legendary Rock Hits", "Alternative Stadium Rock"), "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?q=80&w=600&auto=format&fit=crop", 0xFF6C1D45, "Stadium rock, electrifying riffs & legends"),
        BrowseCategory("workout", "Workout & Energy", listOf("Top Workout Energy Motivation Beats", "High Energy Gym Motivation Hits", "Workout Cardio Power Tracks", "Bass Heavy Workout Bangers"), "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=600&auto=format&fit=crop", 0xFFF77F00, "High BPM power tracks to fuel your session")
    )

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Recent Search Queries
    val recentSearches: StateFlow<List<SearchHistoryEntity>> = searchHistoryDao.getRecentSearchQueries(15)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Previously Searched & Played Tracks (ONLY tracks played after searching)
    val previousPlayedSongs: StateFlow<List<SearchPlayHistoryEntity>> = searchPlayHistoryDao.getRecentSearchPlays(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites = favoriteTrackDao.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _artistResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            // Ultra-responsive debounce for instant single-alphabet feedback
            delay(120)
            executeSearch(query, _filterCategory.value)
        }
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _searchQuery.value = trimmed
        saveSearchQuery(trimmed)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(trimmed, _filterCategory.value)
        }
    }

    fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank() && trimmed.length >= 1) {
            viewModelScope.launch {
                searchHistoryDao.insertSearchQuery(SearchHistoryEntity(query = trimmed, timestampMs = System.currentTimeMillis()))
            }
        }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch {
            searchHistoryDao.deleteSearchQuery(query)
        }
    }

    fun clearAllSearches() {
        viewModelScope.launch {
            searchHistoryDao.clearAllSearchHistory()
        }
    }

    fun setCategory(category: String) {
        _filterCategory.value = category
        if (_searchQuery.value.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                executeSearch(_searchQuery.value, category)
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _artistResults.value = emptyList()
        _isSearching.value = false
        searchJob?.cancel()
    }

    private fun executeSearch(query: String, category: String) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            saveSearchQuery(trimmed)
        }
        viewModelScope.launch {
            _isSearching.value = true
            when (category) {
                "Artists" -> {
                    val artists = innerTubeClient.searchArtists(trimmed)
                    _artistResults.value = rankArtists(artists, trimmed)
                    _searchResults.value = emptyList()
                }
                "Songs" -> {
                    val tracks = innerTubeClient.search(trimmed)
                    val deduplicated = tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                    _searchResults.value = rankTracks(deduplicated, trimmed)
                    _artistResults.value = emptyList()
                }
                else -> { // "All" or other
                    val tracks = innerTubeClient.search(trimmed)
                    val artists = innerTubeClient.searchArtists(trimmed)
                    val deduplicated = tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                    _searchResults.value = rankTracks(deduplicated, trimmed)
                    _artistResults.value = rankArtists(artists, trimmed)
                }
            }
            _isSearching.value = false
        }
    }

    private fun rankTracks(tracks: List<SieloTrack>, query: String): List<SieloTrack> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return tracks
        val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }

        return tracks.sortedByDescending { track ->
            val title = track.title.trim().lowercase()
            val artist = track.artist.trim().lowercase()
            var score = 0

            // Exact match
            if (title == q) score += 1000
            else if (artist == q) score += 800
            // Starts with exact query string
            else if (title.startsWith(q)) score += 600
            else if (artist.startsWith(q)) score += 500
            // Word boundary match (e.g. "Star" in "A Star Is Born" or "The Starboy")
            else if (title.contains(" $q") || title.contains("($q") || title.contains("[$q")) score += 400
            else if (artist.contains(" $q")) score += 350
            // Substring contains
            else if (title.contains(q)) score += 200
            else if (artist.contains(q)) score += 150

            // Multi-word / token matching
            for (w in words) {
                if (title.startsWith(w)) score += 60
                else if (title.contains(w)) score += 30
                if (artist.startsWith(w)) score += 40
                else if (artist.contains(w)) score += 20
            }
            score
        }
    }

    private fun rankArtists(artists: List<SieloArtist>, query: String): List<SieloArtist> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return artists
        val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }

        return artists.sortedByDescending { artist ->
            val name = artist.name.trim().lowercase()
            var score = 0

            if (name == q) score += 1000
            else if (name.startsWith(q)) score += 700
            else if (name.contains(" $q")) score += 500
            else if (name.contains(q)) score += 300

            for (w in words) {
                if (name.startsWith(w)) score += 80
                else if (name.contains(w)) score += 40
            }
            score
        }
    }

    fun openArtist(artist: SieloArtist) {
        saveSearchQuery(artist.name)
        viewModelScope.launch {
            _isArtistLoading.value = true
            _selectedArtist.value = ArtistDetails(
                id = artist.id,
                name = artist.name,
                imageUrl = artist.imageUrl
            )
            val fullDetails = innerTubeClient.getArtistDetails(artist.id, artist.imageUrl)
            if (fullDetails != null) {
                _selectedArtist.value = fullDetails
            }
            _isArtistLoading.value = false
        }
    }

    fun closeArtist() {
        _selectedArtist.value = null
    }

    fun playTrack(track: SieloTrack, queue: List<SieloTrack>) {
        if (_searchQuery.value.isNotBlank()) {
            saveSearchQuery(_searchQuery.value)
        } else {
            saveSearchQuery(track.title)
        }
        // Save to Search Play History specifically
        viewModelScope.launch {
            searchPlayHistoryDao.insertSearchPlay(
                SearchPlayHistoryEntity(
                    songId = track.id,
                    songTitle = track.title,
                    artistName = track.artist,
                    albumName = track.album,
                    thumbnailUrl = track.thumbnailUrl,
                    playedAtMs = System.currentTimeMillis()
                )
            )
        }
        playerManager.playTrack(track, queue)
    }

    fun openCategory(category: BrowseCategory) {
        _selectedCategory.value = category
        viewModelScope.launch {
            _isCategoryLoading.value = true
            val query = category.seedQueries.shuffled().firstOrNull() ?: category.seedQueries.first()
            val tracks = innerTubeClient.search(query)
            val clean = if (tracks.isNotEmpty()) {
                tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
            } else {
                defaultCategoryTracks(category.id).shuffled()
            }
            _categoryTracks.value = clean
            _isCategoryLoading.value = false
        }
    }

    fun closeCategory() {
        _selectedCategory.value = null
        _categoryTracks.value = emptyList()
    }

    private fun defaultCategoryTracks(categoryId: String): List<SieloTrack> = when (categoryId) {
        "pop" -> listOf(
            SieloTrack("fW-Mxsnu", "Blinding Lights", "The Weeknd", durationText = "3:20", thumbnailUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg"),
            SieloTrack("TcDP-KUl", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50", thumbnailUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg"),
            SieloTrack("3IoDK8qI", "Levitating", "Dua Lipa", durationText = "3:23", thumbnailUrl = "https://c.saavncdn.com/665/Future-Nostalgia-English-2020-20260306223201-500x500.jpg"),
            SieloTrack("wwSCc15h", "Shape of You", "Ed Sheeran", durationText = "3:53", thumbnailUrl = "https://c.saavncdn.com/286/WMG_190295851286-English-2017-500x500.jpg"),
            SieloTrack("kd8JSDbB", "Stay", "The Kid LAROI & Justin Bieber", durationText = "2:21", thumbnailUrl = "https://c.saavncdn.com/895/Stay-English-2021-20210706223809-500x500.jpg"),
            SieloTrack("EWoDxjbu", "New Rules", "Dua Lipa", durationText = "3:29", thumbnailUrl = "https://c.saavncdn.com/343/New-Rules-English-2017-20250327204128-500x500.jpg"),
            SieloTrack("wLxoOff5", "Uptown Funk", "Mark Ronson ft. Bruno Mars", durationText = "4:30", thumbnailUrl = "https://c.saavncdn.com/049/Uptown-Funk-English-2014-500x500.jpg"),
            SieloTrack("9q41tYDn", "Die For You", "The Weeknd", durationText = "4:20", thumbnailUrl = "https://c.saavncdn.com/133/Die-For-You-English-2023-20230227063244-500x500.jpg")
        )
        "hiphop" -> listOf(
            SieloTrack("tvxo4Jm0", "HUMBLE.", "Kendrick Lamar", durationText = "2:57", thumbnailUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg"),
            SieloTrack("EWoDxjbu", "God's Plan", "Drake", durationText = "3:18", thumbnailUrl = "https://c.saavncdn.com/343/New-Rules-English-2017-20250327204128-500x500.jpg"),
            SieloTrack("wLxoOff5", "SICKO MODE", "Travis Scott", durationText = "5:12", thumbnailUrl = "https://c.saavncdn.com/049/Uptown-Funk-English-2014-500x500.jpg"),
            SieloTrack("rockstar", "Rockstar", "Post Malone ft. 21 Savage", durationText = "3:38", thumbnailUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg"),
            SieloTrack("goosebumps", "Goosebumps", "Travis Scott", durationText = "4:03", thumbnailUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg")
        )
        "bollywood" -> listOf(
            SieloTrack("kesariya", "Kesariya", "Arijit Singh", durationText = "4:28", thumbnailUrl = "https://c.saavncdn.com/191/Kesariya-From-Brahmastra-Hindi-2022-20220717092820-500x500.jpg"),
            SieloTrack("tumhiho", "Tum Hi Ho", "Arijit Singh", durationText = "4:22", thumbnailUrl = "https://c.saavncdn.com/459/Aashiqui-2-Hindi-2013-500x500.jpg"),
            SieloTrack("channa", "Channa Mereya", "Arijit Singh", durationText = "4:49", thumbnailUrl = "https://c.saavncdn.com/604/Ae-Dil-Hai-Mushkil-Hindi-2016-500x500.jpg"),
            SieloTrack("raataan", "Raataan Lambiyan", "Jubin Nautiyal & Asees Kaur", durationText = "3:50", thumbnailUrl = "https://c.saavncdn.com/643/Shershaah-Original-Motion-Picture-Soundtrack-Hindi-2021-20210815181610-500x500.jpg"),
            SieloTrack("apnabana", "Apna Bana Le", "Arijit Singh & Sachin-Jigar", durationText = "4:21", thumbnailUrl = "https://c.saavncdn.com/803/Bhediya-Hindi-2022-20221124151008-500x500.jpg")
        )
        "lofi" -> listOf(
            SieloTrack("lofi1", "Midnight Study Beats", "Lofi Fruits Music", durationText = "2:45", thumbnailUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("lofi2", "Coffee Shop Rain", "Chillhop Music", durationText = "2:30", thumbnailUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("lofi3", "Tokyo Night Drive", "Kudasaibeats", durationText = "3:10", thumbnailUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80")
        )
        "indie" -> listOf(
            SieloTrack("indie1", "Do I Wanna Know?", "Arctic Monkeys", durationText = "4:32", thumbnailUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("indie2", "Sweater Weather", "The Neighbourhood", durationText = "4:00", thumbnailUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("indie3", "505", "Arctic Monkeys", durationText = "4:13", thumbnailUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80")
        )
        "edm" -> listOf(
            SieloTrack("edm1", "Animals", "Martin Garrix", durationText = "3:12", thumbnailUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("edm2", "Wake Me Up", "Avicii", durationText = "4:07", thumbnailUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("edm3", "Titanium", "David Guetta ft. Sia", durationText = "4:05", thumbnailUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80")
        )
        "rock" -> listOf(
            SieloTrack("rock1", "Believer", "Imagine Dragons", durationText = "3:24", thumbnailUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("rock2", "In the End", "Linkin Park", durationText = "3:36", thumbnailUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("rock3", "Numb", "Linkin Park", durationText = "3:07", thumbnailUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600&auto=format&fit=crop&q=80")
        )
        "workout" -> listOf(
            SieloTrack("work1", "'Till I Collapse", "Eminem", durationText = "4:57", thumbnailUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("work2", "Stronger", "Kanye West", durationText = "5:11", thumbnailUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=600&auto=format&fit=crop&q=80"),
            SieloTrack("work3", "Can't Hold Us", "Macklemore & Ryan Lewis", durationText = "4:18", thumbnailUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=600&auto=format&fit=crop&q=80")
        )
        else -> defaultCategoryTracks("pop")
    }

    fun playSearchPlayEvent(event: SearchPlayHistoryEntity) {
        val track = SieloTrack(
            id = event.songId,
            title = event.songTitle,
            artist = event.artistName,
            album = event.albumName,
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
}

data class BrowseCategory(
    val id: String,
    val title: String,
    val seedQueries: List<String>,
    val imageUrl: String,
    val accentColor: Long,
    val subtitle: String = "Popular in category"
)
