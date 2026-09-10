package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.entity.FavoriteTrackEntity
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
    private val favoriteTrackDao: FavoriteTrackDao
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
            delay(300) // 300ms Debounce
            executeSearch(query, _filterCategory.value)
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
        viewModelScope.launch {
            _isSearching.value = true
            when (category) {
                "Artists" -> {
                    val artists = innerTubeClient.searchArtists(query)
                    _artistResults.value = artists
                    _searchResults.value = emptyList()
                }
                "Songs" -> {
                    val tracks = innerTubeClient.search(query)
                    _searchResults.value = tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                    _artistResults.value = emptyList()
                }
                else -> { // "All" or other
                    val tracks = innerTubeClient.search(query)
                    val artists = innerTubeClient.searchArtists(query)
                    _searchResults.value = tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                    _artistResults.value = artists
                }
            }
            _isSearching.value = false
        }
    }

    fun openArtist(artist: SieloArtist) {
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
        playerManager.playTrack(track, queue)
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
