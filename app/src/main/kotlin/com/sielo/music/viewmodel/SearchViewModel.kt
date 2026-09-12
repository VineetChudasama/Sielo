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
