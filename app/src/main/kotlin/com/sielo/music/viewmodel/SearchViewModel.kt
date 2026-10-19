    package com.sielo.music.viewmodel
    
    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.viewModelScope
    import com.sielo.music.core.audio.PlayerManager
    import com.sielo.music.core.audio.model.PlaybackState
    import com.sielo.music.core.database.dao.FavoriteTrackDao
    import com.sielo.music.core.database.dao.ListeningHistoryDao
    import com.sielo.music.core.database.dao.SearchHistoryDao
    import com.sielo.music.core.database.dao.SearchPlayHistoryDao
    import com.sielo.music.core.database.entity.FavoriteTrackEntity
    import com.sielo.music.core.database.entity.ListeningEventEntity
    import com.sielo.music.core.database.entity.SearchHistoryEntity
    import com.sielo.music.core.database.entity.SearchPlayHistoryEntity
    import com.sielo.music.core.network.innertube.InnerTubeClient
    import com.sielo.music.core.network.innertube.TrackMatchValidator
    import com.sielo.music.core.network.models.ArtistDetails
    import com.sielo.music.core.network.models.SieloArtist
    import com.sielo.music.core.network.models.SieloTrack
    import dagger.hilt.android.lifecycle.HiltViewModel
    import java.util.Locale
    import kotlinx.coroutines.CancellationException
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.flow.first
    import kotlinx.coroutines.withContext
    import com.sielo.music.core.network.metadata.MusicMetadataRepository
    import com.sielo.music.core.recommendations.autoplay.CandidatePoolBuilder
    import com.sielo.music.core.recommendations.autoplay.SongClassifier
    import com.sielo.music.core.recommendations.autoplay.SongLanguage
    import com.sielo.music.core.recommendations.autoplay.SongVibe
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
        private val musicMetadataRepository: MusicMetadataRepository,
        private val playerManager: PlayerManager,
        private val favoriteTrackDao: FavoriteTrackDao,
        private val searchHistoryDao: SearchHistoryDao,
        private val searchPlayHistoryDao: SearchPlayHistoryDao,
        private val listeningHistoryDao: ListeningHistoryDao,
        private val userManager: com.sielo.music.core.auth.UserManager
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
    
        init {
            // Search history preserved safely without destructive prefix deletions
        }

        private var searchJob: Job? = null
        private var searchSequence: Long = 0L

        data class CachedSearchResult(
            val tracks: List<SieloTrack>,
            val artists: List<SieloArtist>,
            val timestampMs: Long
        )

        class SearchResultCache(
            private val maxEntries: Int = 40,
            private val ttlMs: Long = 60_000L
        ) {
            private val lock = Any()
            private val cache = object : LinkedHashMap<String, CachedSearchResult>(maxEntries, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearchResult>?): Boolean {
                    return size > maxEntries
                }
            }

            fun get(category: String, normalizedQuery: String): CachedSearchResult? = synchronized(lock) {
                val key = "${category.lowercase(Locale.ROOT)}:$normalizedQuery"
                val entry = cache[key] ?: return null
                if (System.currentTimeMillis() - entry.timestampMs > ttlMs) {
                    cache.remove(key)
                    return null
                }
                entry
            }

            fun put(category: String, normalizedQuery: String, tracks: List<SieloTrack>, artists: List<SieloArtist>) = synchronized(lock) {
                val key = "${category.lowercase(Locale.ROOT)}:$normalizedQuery"
                cache[key] = CachedSearchResult(
                    tracks = tracks,
                    artists = artists,
                    timestampMs = System.currentTimeMillis()
                )
            }

            fun clear() = synchronized(lock) {
                cache.clear()
            }

            fun size(): Int = synchronized(lock) {
                cache.size
            }
        }

        internal val searchCache = SearchResultCache(maxEntries = 40, ttlMs = 60_000L)

        fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
            searchJob?.cancel()
            val currentSeq = ++searchSequence
            val normalized = normalizeSearchQuery(query)
            if (normalized.isBlank()) {
                _searchResults.value = emptyList()
                _artistResults.value = emptyList()
                _isSearching.value = false
                return
            }

            val cached = searchCache.get(_filterCategory.value, normalized)
            if (cached != null) {
                _searchResults.value = cached.tracks
                _artistResults.value = cached.artists
                _isSearching.value = false
                return
            }

            searchJob = viewModelScope.launch {
                delay(280)
                executeSearch(query, _filterCategory.value, currentSeq)
            }
        }

        fun submitSearch(query: String) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return
            _searchQuery.value = trimmed
            saveSearchQuery(trimmed)
            searchJob?.cancel()
            val currentSeq = ++searchSequence
            val normalized = normalizeSearchQuery(trimmed)
            val cached = searchCache.get(_filterCategory.value, normalized)
            if (cached != null) {
                _searchResults.value = cached.tracks
                _artistResults.value = cached.artists
                _isSearching.value = false
                return
            }
            searchJob = viewModelScope.launch {
                executeSearch(trimmed, _filterCategory.value, currentSeq)
            }
        }

        fun saveSearchQuery(query: String) {
            val trimmed = query.trim()
            if (trimmed.length >= 2) {
                viewModelScope.launch {
                    try {
                        searchHistoryDao.insertSearchQuery(
                            SearchHistoryEntity(query = trimmed, timestampMs = System.currentTimeMillis())
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        fun deleteSearchQuery(query: String) {
            viewModelScope.launch {
                try {
                    searchHistoryDao.deleteSearchQuery(query)
                } catch (_: Exception) {}
            }
        }

        fun clearAllSearches() {
            viewModelScope.launch {
                try {
                    searchHistoryDao.clearAllSearchHistory()
                } catch (_: Exception) {}
            }
        }

        fun setCategory(category: String) {
            if (_filterCategory.value == category) return
            _filterCategory.value = category
            val currentQuery = _searchQuery.value.trim()
            searchJob?.cancel()
            val currentSeq = ++searchSequence
            if (currentQuery.isNotBlank()) {
                val normalized = normalizeSearchQuery(currentQuery)
                val cached = searchCache.get(category, normalized)
                if (cached != null) {
                    _searchResults.value = cached.tracks
                    _artistResults.value = cached.artists
                    _isSearching.value = false
                } else {
                    _searchResults.value = emptyList()
                    _artistResults.value = emptyList()
                    _isSearching.value = true
                    searchJob = viewModelScope.launch {
                        executeSearch(currentQuery, category, currentSeq)
                    }
                }
            } else {
                _searchResults.value = emptyList()
                _artistResults.value = emptyList()
                _isSearching.value = false
            }
        }

        fun clearSearch() {
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            _artistResults.value = emptyList()
            _isSearching.value = false
            searchJob?.cancel()
            searchSequence++
        }

        fun normalizeSearchQuery(raw: String): String = Companion.normalizeSearchQuery(raw)
    
    data class UserTasteSnapshot(
        val topArtistNames: Set<String> = emptySet(),
        val topArtistWeights: Map<String, Int> = emptyMap(),
        val favoriteSongIds: Set<String> = emptySet(),
        val searchPlayedSongIds: Set<String> = emptySet(),
        val playedSongCounts: Map<String, Int> = emptyMap(),
        val topLanguages: Set<SongLanguage> = emptySet(),
        val topVibes: Set<SongVibe> = emptySet(),
        val favoriteGenres: Set<String> = emptySet()
    )

    private var cachedTasteSnapshot: UserTasteSnapshot? = null
    private var lastTasteSnapshotTime: Long = 0L

    private suspend fun getUserTasteSnapshot(): UserTasteSnapshot {
        val now = System.currentTimeMillis()
        val current = cachedTasteSnapshot
        if (current != null && (now - lastTasteSnapshotTime) < 45_000L) {
            return current
        }
        return withContext(Dispatchers.IO) {
            try {
                val thirtyDaysAgo = now - 30L * 24L * 60L * 60L * 1000L
                val topArtists = listeningHistoryDao.getTopArtistsSnapshot(thirtyDaysAgo, limit = 25)
                val topSongs = listeningHistoryDao.getTopSongsSnapshot(thirtyDaysAgo, limit = 60)
                val recentEvents = listeningHistoryDao.getRecentUniquePlayedSongs(limit = 25).first()
                val favorites = favoriteTrackDao.getAllFavoritesList()
                val searchPlays = searchPlayHistoryDao.getRecentSearchPlays(25).first()
                val userProfile = userManager.currentUser.value

                val topArtistWeights = mutableMapOf<String, Int>()
                topArtists.forEach { stat ->
                    topArtistWeights[stat.artistName.trim().lowercase()] = stat.playCount
                }
                userProfile?.favoriteArtists?.forEach { artist ->
                    val key = artist.trim().lowercase()
                    topArtistWeights[key] = (topArtistWeights[key] ?: 0) + 10
                }
                userProfile?.artistTasteWeights?.forEach { (artist, weight) ->
                    val key = artist.trim().lowercase()
                    topArtistWeights[key] = (topArtistWeights[key] ?: 0) + weight
                }

                val playedSongCounts = mutableMapOf<String, Int>()
                topSongs.forEach { stat ->
                    playedSongCounts[stat.songId] = stat.playCount
                }

                val languageCounts = mutableMapOf<SongLanguage, Int>()
                val vibeCounts = mutableMapOf<SongVibe, Int>()

                recentEvents.forEach { event ->
                    val dummyTrack = SieloTrack(id = event.songId, title = event.songTitle, artist = event.artistName)
                    val lang = SongClassifier.detectLanguage(dummyTrack)
                    languageCounts[lang] = (languageCounts[lang] ?: 0) + 1
                    val vibe = SongClassifier.detectVibe(dummyTrack)
                    vibeCounts[vibe] = (vibeCounts[vibe] ?: 0) + 1
                }

                val topLangs = languageCounts.entries.sortedByDescending { it.value }.map { it.key }.toSet()
                val topVibes = vibeCounts.entries.sortedByDescending { it.value }.map { it.key }.toSet()
                val favGenres = (userProfile?.favoriteGenres ?: emptyList()).map { it.trim().lowercase() }.toSet()

                val snapshot = UserTasteSnapshot(
                    topArtistNames = topArtistWeights.keys,
                    topArtistWeights = topArtistWeights,
                    favoriteSongIds = favorites.map { it.id }.toSet(),
                    searchPlayedSongIds = searchPlays.map { it.songId }.toSet(),
                    playedSongCounts = playedSongCounts,
                    topLanguages = topLangs,
                    topVibes = topVibes,
                    favoriteGenres = favGenres
                )
                cachedTasteSnapshot = snapshot
                lastTasteSnapshotTime = now
                snapshot
            } catch (_: Exception) {
                cachedTasteSnapshot ?: UserTasteSnapshot()
            }
        }
    }

    internal fun levenshteinDistance(s1: String, s2: String, maxDistance: Int = 2): Int =
        Companion.levenshteinDistance(s1, s2, maxDistance)

    private suspend fun executeSearch(query: String, category: String, sequenceId: Long) {
        val trimmed = query.trim()
        val normalized = normalizeSearchQuery(trimmed)
        if (normalized.isBlank()) {
            if (sequenceId == searchSequence) {
                _searchResults.value = emptyList()
                _artistResults.value = emptyList()
                _isSearching.value = false
            }
            return
        }

        // 1. Check in-memory cache first
        val cached = searchCache.get(category, normalized)
        if (cached != null) {
            if (sequenceId == searchSequence && _filterCategory.value == category) {
                _searchResults.value = cached.tracks
                _artistResults.value = cached.artists
                _isSearching.value = false
            }
            return
        }

        if (sequenceId == searchSequence) {
            _isSearching.value = true
        }

        try {
            val taste = getUserTasteSnapshot()
            when (category) {
                "Artists" -> {
                    val artists = coroutineScope {
                        val ytDeferred = async {
                            try {
                                innerTubeClient.searchArtists(trimmed)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                        val mbDeferred = async {
                            try {
                                musicMetadataRepository.searchArtist(trimmed)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                        mergeArtists(ytDeferred.await(), mbDeferred.await())
                    }

                    if (sequenceId == searchSequence &&
                        normalizeSearchQuery(_searchQuery.value) == normalized &&
                        _filterCategory.value == category
                    ) {
                        val ranked = rankArtists(artists, trimmed, taste)
                        _artistResults.value = ranked
                        _searchResults.value = emptyList()
                        if (ranked.isNotEmpty()) {
                            searchCache.put(category, normalized, emptyList(), ranked)
                        }
                    }
                }
                "Songs" -> {
                    val tracks = coroutineScope {
                        val primaryDeferred = async {
                            try {
                                innerTubeClient.search(trimmed)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                        val altDeferred = async {
                            if (normalized != trimmed.lowercase(Locale.ROOT)) {
                                try {
                                    innerTubeClient.search(normalized)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            } else emptyList()
                        }
                        val combined = primaryDeferred.await() + altDeferred.await()
                        TrackMatchValidator.deduplicateTracks(combined).distinctBy { it.id }
                    }

                    if (sequenceId == searchSequence &&
                        normalizeSearchQuery(_searchQuery.value) == normalized &&
                        _filterCategory.value == category
                    ) {
                        val ranked = rankTracks(tracks, trimmed, taste)
                        _searchResults.value = ranked
                        _artistResults.value = emptyList()
                        if (ranked.isNotEmpty()) {
                            searchCache.put(category, normalized, ranked, emptyList())
                        }
                    }
                }
                else -> { // "All" category: songs and artists are retrieved independently
                    val (tracks, artists) = coroutineScope {
                        val tracksDeferred = async {
                            try {
                                val primary = innerTubeClient.search(trimmed)
                                val alt = if (normalized != trimmed.lowercase(Locale.ROOT)) {
                                    try { innerTubeClient.search(normalized) } catch (_: Exception) { emptyList() }
                                } else emptyList()
                                TrackMatchValidator.deduplicateTracks(primary + alt).distinctBy { it.id }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }

                        val artistsDeferred = async {
                            val yt = try {
                                innerTubeClient.searchArtists(trimmed)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                            val mb = try {
                                musicMetadataRepository.searchArtist(trimmed)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                            mergeArtists(yt, mb)
                        }

                        tracksDeferred.await() to artistsDeferred.await()
                    }

                    if (sequenceId == searchSequence &&
                        normalizeSearchQuery(_searchQuery.value) == normalized &&
                        _filterCategory.value == category
                    ) {
                        val rankedTracks = rankTracks(tracks, trimmed, taste)
                        val rankedArtists = rankArtists(artists, trimmed, taste)
                        _searchResults.value = rankedTracks
                        _artistResults.value = rankedArtists
                        if (rankedTracks.isNotEmpty() || rankedArtists.isNotEmpty()) {
                            searchCache.put(category, normalized, rankedTracks, rankedArtists)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Failure in one provider must not crash the entire search screen
        } finally {
            if (sequenceId == searchSequence &&
                normalizeSearchQuery(_searchQuery.value) == normalized &&
                _filterCategory.value == category
            ) {
                _isSearching.value = false
            }
        }
    }

    internal fun rankTracks(tracks: List<SieloTrack>, query: String, taste: UserTasteSnapshot = UserTasteSnapshot()): List<SieloTrack> =
        Companion.rankTracks(tracks, query, taste)

    fun refreshArtist() {
        val current = _selectedArtist.value ?: return
        viewModelScope.launch {
            _isArtistLoading.value = true
            val saavnId = if (current.id.all { it.isDigit() }) current.id else null
            val fullDetails = musicMetadataRepository.getArtistDetails(
                artistName = current.name,
                imageUrl = current.imageUrl,
                artistId = saavnId,
                forceRefresh = true
            )
            if (fullDetails != null) {
                _selectedArtist.value = fullDetails
            }
            _isArtistLoading.value = false
        }
    }

    internal fun cleanForArtistMatch(name: String): String = Companion.cleanForArtistMatch(name)

    private fun mergeArtists(primary: List<SieloArtist>, secondary: List<SieloArtist>): List<SieloArtist> {
        val result = mutableListOf<SieloArtist>()
        result.addAll(primary)
        for (sec in secondary) {
            val secClean = cleanForArtistMatch(sec.name)
            val existing = result.find { cleanForArtistMatch(it.name) == secClean }
            if (existing == null) {
                if (primary.isEmpty() || !sec.imageUrl.isNullOrBlank()) {
                    result.add(sec)
                }
            } else {
                val existingHasYtImage = existing.imageUrl?.let {
                    it.contains("googleusercontent.com") || it.contains("ggpht.com") || it.contains("ytimg.com")
                } == true
                val secHasVerifiedImage = sec.imageUrl?.let {
                    it.contains("wikimedia.org") || it.contains("wikipedia.org") || it.contains("saavncdn.com") || it.contains("dzcdn.net")
                } == true
                if (existing.imageUrl.isNullOrBlank() || (existingHasYtImage && secHasVerifiedImage)) {
                    val idx = result.indexOf(existing)
                    result[idx] = existing.copy(imageUrl = sec.imageUrl)
                }
            }
        }
        return result
    }

    internal fun rankArtists(artists: List<SieloArtist>, query: String, taste: UserTasteSnapshot = UserTasteSnapshot()): List<SieloArtist> =
        Companion.rankArtists(artists, query, taste)

        private val artistHistoryStack = ArrayDeque<ArtistDetails>()

        fun openArtist(artist: SieloArtist) {
            saveSearchQuery(artist.name)
            val current = _selectedArtist.value
            if (current != null && !current.name.equals(artist.name, ignoreCase = true)) {
                artistHistoryStack.addLast(current)
            }
            viewModelScope.launch {
                _isArtistLoading.value = true
                _selectedArtist.value = ArtistDetails(
                    id = artist.id,
                    name = artist.name,
                    imageUrl = artist.imageUrl
                )
                val saavnId = if (artist.id.all { it.isDigit() }) artist.id else null
                val fullDetails = musicMetadataRepository.getArtistDetails(
                    artistName = artist.name,
                    imageUrl = artist.imageUrl,
                    artistId = saavnId
                )
                if (fullDetails != null) {
                    _selectedArtist.value = fullDetails
                }
                _isArtistLoading.value = false
            }
        }
    
        val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    
        fun isArtistFollowed(artistName: String): Boolean = userManager.isArtistFollowed(artistName)
    
        fun toggleFollowArtist(artistName: String): Boolean = userManager.toggleFollowArtist(artistName)
    
        fun closeArtist(): Boolean {
            if (artistHistoryStack.isNotEmpty()) {
                _selectedArtist.value = artistHistoryStack.removeLast()
                return true
            } else {
                _selectedArtist.value = null
                return false
            }
        }
    
        suspend fun getAlbumSongs(album: com.sielo.music.core.network.models.SieloAlbum): List<SieloTrack> {
            return musicMetadataRepository.getAlbumTracks(album)
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
    
        fun playAlbum(track: SieloTrack, queue: List<SieloTrack>) {
            playerManager.playAlbum(track, queue)
        }
    
        fun playArtistRadio(track: SieloTrack, queue: List<SieloTrack>, artistName: String) {
            playerManager.playArtistRadio(track, queue, artistName)
        }
    
        fun appendToQueue(tracks: List<SieloTrack>) {
            playerManager.appendToQueue(tracks)
        }
    
        fun appendToQueue(track: SieloTrack) {
            playerManager.appendToQueue(track)
        }
    
        fun openCategory(category: BrowseCategory) {
            _selectedCategory.value = category
            viewModelScope.launch {
                _isCategoryLoading.value = true
                val query = category.seedQueries.shuffled().firstOrNull() ?: category.seedQueries.first()
                val tracks = innerTubeClient.search(query)
                val clean = if (tracks.isNotEmpty()) {
                    TrackMatchValidator.deduplicateTracks(tracks).distinctBy { it.id }
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
                SieloTrack("tvTRZJ-4EyI", "HUMBLE.", "Kendrick Lamar", durationText = "2:57", thumbnailUrl = "https://i.ytimg.com/vi/tvTRZJ-4EyI/hqdefault.jpg"),
                SieloTrack("xpVfcZ0ZcFM", "God's Plan", "Drake", durationText = "3:18", thumbnailUrl = "https://i.ytimg.com/vi/xpVfcZ0ZcFM/hqdefault.jpg"),
                SieloTrack("6ONRf7h3Mdk", "SICKO MODE", "Travis Scott", durationText = "5:12", thumbnailUrl = "https://i.ytimg.com/vi/6ONRf7h3Mdk/hqdefault.jpg"),
                SieloTrack("UceaB4D0jpo", "Rockstar", "Post Malone ft. 21 Savage", durationText = "3:38", thumbnailUrl = "https://i.ytimg.com/vi/UceaB4D0jpo/hqdefault.jpg"),
                SieloTrack("Dst9gZkq1a8", "Goosebumps", "Travis Scott", durationText = "4:03", thumbnailUrl = "https://i.ytimg.com/vi/Dst9gZkq1a8/hqdefault.jpg")
            )
            "bollywood" -> listOf(
                SieloTrack("BddP6PYo2gs", "Kesariya", "Arijit Singh", durationText = "4:28", thumbnailUrl = "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg"),
                SieloTrack("Umqb9KENgmk", "Tum Hi Ho", "Arijit Singh", durationText = "4:22", thumbnailUrl = "https://i.ytimg.com/vi/Umqb9KENgmk/hqdefault.jpg"),
                SieloTrack("bzSThTQv67A", "Channa Mereya", "Arijit Singh", durationText = "4:49", thumbnailUrl = "https://i.ytimg.com/vi/bzSThTQv67A/hqdefault.jpg"),
                SieloTrack("gvyUuxdRdR4", "Raataan Lambiyan", "Jubin Nautiyal & Asees Kaur", durationText = "3:50", thumbnailUrl = "https://i.ytimg.com/vi/gvyUuxdRdR4/hqdefault.jpg"),
                SieloTrack("ElZfdU54Cp8", "Apna Bana Le", "Arijit Singh & Sachin-Jigar", durationText = "4:21", thumbnailUrl = "https://i.ytimg.com/vi/ElZfdU54Cp8/hqdefault.jpg")
            )
            "lofi" -> listOf(
                SieloTrack("5qap5aO4i9A", "Lofi Hip Hop Radio - Beats to Relax/Study to", "Lofi Girl", durationText = "3:00", thumbnailUrl = "https://i.ytimg.com/vi/5qap5aO4i9A/hqdefault.jpg"),
                SieloTrack("DWcJFNfaw9c", "Coffee Shop Rain", "Chillhop Music", durationText = "2:30", thumbnailUrl = "https://i.ytimg.com/vi/DWcJFNfaw9c/hqdefault.jpg"),
                SieloTrack("MCkTebktHVc", "Tokyo Night Drive", "Kudasaibeats", durationText = "3:10", thumbnailUrl = "https://i.ytimg.com/vi/MCkTebktHVc/hqdefault.jpg")
            )
            "indie" -> listOf(
                SieloTrack("bpOSxM0rNPM", "Do I Wanna Know?", "Arctic Monkeys", durationText = "4:32", thumbnailUrl = "https://i.ytimg.com/vi/bpOSxM0rNPM/hqdefault.jpg"),
                SieloTrack("GCdwKhTtNNw", "Sweater Weather", "The Neighbourhood", durationText = "4:00", thumbnailUrl = "https://i.ytimg.com/vi/GCdwKhTtNNw/hqdefault.jpg"),
                SieloTrack("qU9mHegkTc4", "505", "Arctic Monkeys", durationText = "4:13", thumbnailUrl = "https://i.ytimg.com/vi/qU9mHegkTc4/hqdefault.jpg")
            )
            "edm" -> listOf(
                SieloTrack("gCYcTST8s4o", "Animals", "Martin Garrix", durationText = "3:12", thumbnailUrl = "https://i.ytimg.com/vi/gCYcTST8s4o/hqdefault.jpg"),
                SieloTrack("IcrbM1l_BoI", "Wake Me Up", "Avicii", durationText = "4:07", thumbnailUrl = "https://i.ytimg.com/vi/IcrbM1l_BoI/hqdefault.jpg"),
                SieloTrack("JRfuAukYTKg", "Titanium", "David Guetta ft. Sia", durationText = "4:05", thumbnailUrl = "https://i.ytimg.com/vi/JRfuAukYTKg/hqdefault.jpg")
            )
            "rock" -> listOf(
                SieloTrack("7wtfhZwyrcc", "Believer", "Imagine Dragons", durationText = "3:24", thumbnailUrl = "https://i.ytimg.com/vi/7wtfhZwyrcc/hqdefault.jpg"),
                SieloTrack("eVTXPUF4Oz4", "In the End", "Linkin Park", durationText = "3:36", thumbnailUrl = "https://i.ytimg.com/vi/eVTXPUF4Oz4/hqdefault.jpg"),
                SieloTrack("kXYiU_JCYtU", "Numb", "Linkin Park", durationText = "3:07", thumbnailUrl = "https://i.ytimg.com/vi/kXYiU_JCYtU/hqdefault.jpg")
            )
            "workout" -> listOf(
                SieloTrack("Y1wRGuCg4ek", "'Till I Collapse", "Eminem", durationText = "4:57", thumbnailUrl = "https://i.ytimg.com/vi/Y1wRGuCg4ek/hqdefault.jpg"),
                SieloTrack("PsO6Zn4V07g", "Stronger", "Kanye West", durationText = "5:11", thumbnailUrl = "https://i.ytimg.com/vi/PsO6Zn4V07g/hqdefault.jpg"),
                SieloTrack("2zNSgSzhBfM", "Can't Hold Us", "Macklemore & Ryan Lewis", durationText = "4:18", thumbnailUrl = "https://i.ytimg.com/vi/2zNSgSzhBfM/hqdefault.jpg")
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
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val isFav = favoriteTrackDao.isFavorite(track.id).first()
                    if (isFav) {
                        favoriteTrackDao.deleteById(track.id)
                    } else {
                        favoriteTrackDao.insertFavorite(
                            FavoriteTrackEntity(
                                id = track.id,
                                title = track.title,
                                artist = track.artist,
                                thumbnailUrl = track.thumbnailUrl
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        companion object {
            fun normalizeSearchQuery(raw: String): String {
                return raw.trim()
                    .replace(Regex("\\s+"), " ")
                    .lowercase(Locale.ROOT)
            }

            internal fun cleanForArtistMatch(name: String): String {
                return name.trim().lowercase(Locale.ROOT)
                    .replace(Regex("^(the|a|an)\\s+"), "")
                    .replace(Regex("[^\\p{L}\\p{Nd}]"), "")
            }

            internal fun levenshteinDistance(s1: String, s2: String, maxDistance: Int = 2): Int {
                if (s1 == s2) return 0
                val l1 = s1.length
                val l2 = s2.length
                if (kotlin.math.abs(l1 - l2) > maxDistance) return maxDistance + 1

                var prev = IntArray(l2 + 1) { it }
                var curr = IntArray(l2 + 1)

                for (i in 1..l1) {
                    curr[0] = i
                    var minInRow = curr[0]
                    for (j in 1..l2) {
                        val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                        curr[j] = minOf(
                            curr[j - 1] + 1,
                            prev[j] + 1,
                            prev[j - 1] + cost
                        )
                        minInRow = minOf(minInRow, curr[j])
                    }
                    if (minInRow > maxDistance) return maxDistance + 1
                    val temp = prev
                    prev = curr
                    curr = temp
                }
                return prev[l2]
            }

            internal fun rankTracks(tracks: List<SieloTrack>, query: String, taste: UserTasteSnapshot = UserTasteSnapshot()): List<SieloTrack> {
                val q = normalizeSearchQuery(query)
                if (q.isBlank()) return tracks
                val qClean = cleanForArtistMatch(q)
                val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }

                val scoredTracks = tracks.map { track ->
                    val title = normalizeSearchQuery(track.title)
                    val artist = normalizeSearchQuery(track.artist)
                    val cleanArtist = cleanForArtistMatch(artist)
                    var textScore = 0

                    // 1. Text Relevance Match
                    // A. Exact song title match
                    if (title == q) {
                        textScore += 100_000
                    }
                    // B. Exact artist name match
                    else if (artist == q || cleanArtist == qClean) {
                        textScore += 80_000
                    }
                    // C. Song title starts with the query
                    else if (title.startsWith(q)) {
                        val lenDiff = (title.length - q.length).coerceAtLeast(0)
                        textScore += 50_000 - minOf(lenDiff * 50, 10_000)
                    }
                    // D. Artist name starts with the query
                    else if (artist.startsWith(q) || cleanArtist.startsWith(qClean)) {
                        val lenDiff = (cleanArtist.length - qClean.length).coerceAtLeast(0)
                        textScore += 40_000 - minOf(lenDiff * 50, 10_000)
                    }
                    // E. Strong partial title match
                    else if (title.contains(" $q") || title.contains("($q") || title.contains("[$q")) {
                        textScore += 30_000
                    } else if (title.contains(q)) {
                        textScore += 20_000
                    }
                    // F. Strong partial artist match
                    else if (artist.contains(" $q") || cleanArtist.contains(" $qClean")) {
                        textScore += 25_000
                    } else if (artist.contains(q) || cleanArtist.contains(qClean)) {
                        textScore += 15_000
                    } else {
                        // G. Fuzzy match for small spelling mistakes
                        val allowDist = if (q.length >= 8) 2 else if (q.length >= 4) 1 else 0
                        if (allowDist > 0) {
                            val titleWords = title.split(Regex("\\s+")).filter { it.isNotBlank() }
                            var bestWordDist = allowDist + 1
                            for (tw in titleWords) {
                                if (kotlin.math.abs(tw.length - q.length) <= allowDist) {
                                    val d = levenshteinDistance(q, tw, allowDist)
                                    if (d < bestWordDist) bestWordDist = d
                                }
                            }
                            if (bestWordDist <= allowDist) {
                                textScore += 10_000 - (bestWordDist * 2500)
                            } else if (kotlin.math.abs(title.length - q.length) <= allowDist) {
                                val d = levenshteinDistance(q, title, allowDist)
                                if (d <= allowDist) {
                                    textScore += 10_000 - (d * 2500)
                                }
                            }

                            if (textScore == 0) {
                                val artistWords = artist.split(Regex("\\s+")).filter { it.isNotBlank() }
                                var bestArtistDist = allowDist + 1
                                for (aw in artistWords) {
                                    if (kotlin.math.abs(aw.length - q.length) <= allowDist) {
                                        val d = levenshteinDistance(q, aw, allowDist)
                                        if (d < bestArtistDist) bestArtistDist = d
                                    }
                                }
                                if (bestArtistDist <= allowDist) {
                                    textScore += 8_000 - (bestArtistDist * 2000)
                                }
                            }
                        }
                    }

                    // Multi-word / token matching bonus
                    var matchingWords = 0
                    for (w in words) {
                        if (w.length > 1) {
                            var hit = false
                            if (title.startsWith(w)) {
                                textScore += 600
                                hit = true
                            } else if (title.contains(w)) {
                                textScore += 300
                                hit = true
                            }
                            if (artist.startsWith(w)) {
                                textScore += 400
                                hit = true
                            } else if (artist.contains(w)) {
                                textScore += 200
                                hit = true
                            }
                            if (hit) matchingWords++
                        }
                    }

                    // Clean studio bonus & noise penalties
                    if (CandidatePoolBuilder.isCleanStudioTrack(track.title, track.artist)) {
                        textScore += 500
                    }
                    val noiseKeywords = listOf("remix", "karaoke", "instrumental", "cover", "slowed", "reverb", "parody", "mashup")
                    if (noiseKeywords.any { title.contains(it) && !q.contains(it) }) {
                        textScore -= 3000
                    }

                    // 2. Plays / Popularity Boost (0 to 800 pts)
                    var popScore = 0
                    if (track.viewCount > 0) {
                        val logViews = kotlin.math.log10(track.viewCount.toDouble()).coerceIn(0.0, 10.0)
                        popScore += (logViews * 80).toInt()
                    }

                    // 3. User Listening History & Personalization (Strictly capped at max 3,500 pts!)
                    var personalScore = 0
                    val userPlayCount = taste.playedSongCounts[track.id] ?: 0
                    if (userPlayCount > 0) {
                        personalScore += 500 + minOf(userPlayCount * 50, 500)
                    }
                    if (track.id in taste.searchPlayedSongIds) {
                        personalScore += 600
                    }
                    if (track.id in taste.favoriteSongIds) {
                        personalScore += 800
                    }

                    val artistWeight = taste.topArtistWeights.entries.firstOrNull { (name, _) ->
                        artist.contains(name) || name.contains(artist) || cleanForArtistMatch(name) == cleanArtist
                    }?.value ?: 0
                    if (artistWeight > 0) {
                        personalScore += minOf(400 + (artistWeight * 50), 1000)
                    }

                    val trackLang = SongClassifier.detectLanguage(track)
                    if (trackLang in taste.topLanguages) {
                        personalScore += 300
                    }

                    val trackVibe = SongClassifier.detectVibe(track)
                    if (trackVibe in taste.topVibes) {
                        personalScore += 200
                    }

                    val normalizedPersonalScore = minOf(personalScore, 3500)
                    val totalScore = textScore + popScore + normalizedPersonalScore
                    track to Triple(totalScore, textScore, matchingWords)
                }

                val filtered = if (words.size >= 2) {
                    scoredTracks.filter { (_, meta) ->
                        val (_, textScore, matchingWords) = meta
                        textScore >= 300 || matchingWords >= (words.size / 2).coerceAtLeast(1)
                    }
                } else {
                    scoredTracks.filter { (_, meta) -> meta.second > 0 }
                }

                val finalSelection = if (filtered.isNotEmpty()) filtered else scoredTracks
                return finalSelection.sortedByDescending { it.second.first }.map { it.first }
            }

            internal fun rankArtists(artists: List<SieloArtist>, query: String, taste: UserTasteSnapshot = UserTasteSnapshot()): List<SieloArtist> {
                val q = normalizeSearchQuery(query)
                if (q.isBlank()) return emptyList()
                val qClean = cleanForArtistMatch(q)
                val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }

                val scored = artists.mapIndexed { index, artist ->
                    val name = normalizeSearchQuery(artist.name)
                    val aClean = cleanForArtistMatch(name)
                    var score = 0

                    // 1. Exact match
                    if (aClean == qClean || name == q) {
                        score += 100_000
                    }
                    // 2. Starts with query
                    else if (aClean.startsWith(qClean) || name.startsWith(q)) {
                        val lenDiff = (aClean.length - qClean.length).coerceAtLeast(0)
                        score += 50_000 - minOf(lenDiff * 100, 10_000)
                    }
                    // 3. Distinct word contains
                    else if (name.contains(" $q") || name.contains(" $q ")) {
                        score += 25_000
                    }
                    // 4. Substring contains
                    else if (name.contains(q) || aClean.contains(qClean)) {
                        score += 15_000
                    }
                    else {
                        // Fuzzy match for small spelling mistakes
                        val allowDist = if (qClean.length >= 8) 2 else if (qClean.length >= 4) 1 else 0
                        var fuzzyHit = false
                        if (allowDist > 0) {
                            val d = levenshteinDistance(qClean, aClean, allowDist)
                            if (d <= allowDist) {
                                score += 10_000 - (d * 2500)
                                fuzzyHit = true
                            } else {
                                val aWords = aClean.split(Regex("\\s+")).filter { it.isNotBlank() }
                                for (aw in aWords) {
                                    val wd = levenshteinDistance(qClean, aw, allowDist)
                                    if (wd <= allowDist) {
                                        score += 8_000 - (wd * 2000)
                                        fuzzyHit = true
                                        break
                                    }
                                }
                            }
                        }
                        if (!fuzzyHit) {
                            score += (artists.size - index).coerceAtLeast(0) * 10
                        }
                    }

                    // Token match bonus
                    for (w in words) {
                        if (w.length > 1) {
                            if (name.startsWith(w) || aClean.startsWith(w)) score += 500
                            else if (name.contains(w)) score += 200
                        }
                    }

                    // User played artist boost (capped at 4,000 pts so it can't beat textual match)
                    val artistWeight = taste.topArtistWeights.entries.firstOrNull { (tName, _) ->
                        tName == aClean || name == tName || aClean.contains(cleanForArtistMatch(tName)) || cleanForArtistMatch(tName).contains(aClean)
                    }?.value ?: 0
                    if (artistWeight > 0) {
                        score += minOf(1500 + (artistWeight * 100), 4000)
                    }

                    // Severe penalty for spam / noise in artist name
                    val spamNoise = listOf(" bar", " party", " stanna", " fan", " cover", " tribute", " remix", " mix", "ai studio")
                    if (spamNoise.any { name.contains(it) }) {
                        score -= 50_000
                    }

                    artist to score
                }

                val valid = scored.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
                val distinctArtists = mutableListOf<SieloArtist>()
                val seenClean = mutableSetOf<String>()
                for (a in valid) {
                    val clean = cleanForArtistMatch(a.name)
                    if (clean.isNotBlank() && !seenClean.contains(clean)) {
                        seenClean.add(clean)
                        distinctArtists.add(a)
                    }
                }

                val seenImages = mutableSetOf<String>()
                return distinctArtists.map { artist ->
                    if (!artist.imageUrl.isNullOrBlank() && !seenImages.contains(artist.imageUrl)) {
                        seenImages.add(artist.imageUrl!!)
                        artist
                    } else if (!artist.imageUrl.isNullOrBlank()) {
                        artist.copy(imageUrl = null)
                    } else {
                        artist
                    }
                }
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
