package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.database.dao.ArtistStat
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
import kotlinx.coroutines.flow.firstOrNull
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // 5 Most Recently Played Unique Tracks (snapshot loaded on launch & updated ONLY on refresh/relaunch)
    private val _recentPlayedSongs = MutableStateFlow<List<ListeningEventEntity>>(emptyList())
    val recentPlayedSongs: StateFlow<List<ListeningEventEntity>> = _recentPlayedSongs.asStateFlow()

    // Tracks played before 2 days ago that user hasn't listened to in the last 2 days (snapshot loaded on launch & updated ONLY on refresh/relaunch)
    private val _rediscoveredFavorites = MutableStateFlow<List<ListeningEventEntity>>(emptyList())
    val rediscoveredFavorites: StateFlow<List<ListeningEventEntity>> = _rediscoveredFavorites.asStateFlow()

    // Most listened artist dynamically computed from listening history (snapshot loaded on launch & updated ONLY on refresh/relaunch)
    private val _topArtistStat = MutableStateFlow<List<ArtistStat>>(emptyList())
    val topArtistStat: StateFlow<List<ArtistStat>> = _topArtistStat.asStateFlow()

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

    private val _dynamicSimilarArtists = MutableStateFlow<List<ArtistProfile>>(emptyList())
    val dynamicSimilarArtists: StateFlow<List<ArtistProfile>> = _dynamicSimilarArtists.asStateFlow()

    private var lastResolvedArtist: String? = null

    init {
        loadInitialFeed()
    }

    private fun loadInitialFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch initial history snapshots
                val recent = listeningHistoryDao.getRecentUniquePlayedSongs(5).firstOrNull() ?: emptyList()
                val rediscovered = listeningHistoryDao.getRediscoveredFavorites(
                    twoDaysAgoMs = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000L),
                    limit = 5
                ).firstOrNull() ?: emptyList()
                val topArtists = listeningHistoryDao.getTopArtists(sinceMs = 0L, limit = 1).firstOrNull() ?: emptyList()

                _recentPlayedSongs.value = recent
                _rediscoveredFavorites.value = rediscovered
                _topArtistStat.value = topArtists

                val cleanedQuery = _selectedMood.value.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
                val query = if (cleanedQuery.isNotEmpty()) cleanedQuery else "Top Hits 2026"
                val tracks = innerTubeClient.search(query)
                val cleanTracks = if (tracks.isNotEmpty()) {
                    tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                } else defaultStarterTracks()
                _trendingTracks.value = cleanTracks

                val topArtistName = topArtists.firstOrNull()?.artistName ?: "The Weeknd"
                lastResolvedArtist = topArtistName
                val similarList = fetchSimilarArtistProfiles(topArtistName)
                if (similarList.isNotEmpty()) {
                    _dynamicSimilarArtists.value = similarList
                }
            } catch (e: Exception) {
                _trendingTracks.value = defaultStarterTracks()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val startTime = System.currentTimeMillis()

            try {
                // 1. Compute new greeting in memory
                val newGreeting = computeGreeting()

                // 2. Query history snapshots in memory
                val newRecent = listeningHistoryDao.getRecentUniquePlayedSongs(5).firstOrNull() ?: emptyList()
                val newRediscovered = listeningHistoryDao.getRediscoveredFavorites(
                    twoDaysAgoMs = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000L),
                    limit = 5
                ).firstOrNull() ?: emptyList()
                val newTopArtists = listeningHistoryDao.getTopArtists(sinceMs = 0L, limit = 1).firstOrNull() ?: emptyList()

                // 3. Query feed tracks in memory
                val cleanedQuery = _selectedMood.value.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
                val query = if (cleanedQuery.isNotEmpty()) cleanedQuery else "Top Hits 2026"
                val tracks = innerTubeClient.search(query)
                val newTracks = if (tracks.isNotEmpty()) {
                    tracks.distinctBy { it.id }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                } else defaultStarterTracks().shuffled()

                // 4. Query dynamic similar artists in memory
                val topArtistName = newTopArtists.firstOrNull()?.artistName ?: "The Weeknd"
                lastResolvedArtist = topArtistName
                val newSimilarArtists = fetchSimilarArtistProfiles(topArtistName)

                // 5. Ensure refresh animation runs for a smooth minimum time so user sees the animation
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 800) {
                    kotlinx.coroutines.delay(800 - elapsed)
                }

                // 6. SYNC ALL REFRESHES TOGETHER ATOMICALLY
                _greeting.value = newGreeting
                _recentPlayedSongs.value = newRecent
                _rediscoveredFavorites.value = newRediscovered
                _topArtistStat.value = newTopArtists
                _trendingTracks.value = newTracks
                if (newSimilarArtists.isNotEmpty()) {
                    _dynamicSimilarArtists.value = newSimilarArtists
                }
            } catch (e: Exception) {
                // Retain current states on error
            } finally {
                _isRefreshing.value = false
            }
        }
    }

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
            } else defaultGenreTracks(genre.name).shuffled()
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

    private suspend fun fetchSimilarArtistProfiles(topArtistName: String): List<ArtistProfile> {
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
        return list
    }

    fun loadDynamicSimilarArtists(topArtistName: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && topArtistName == lastResolvedArtist && _dynamicSimilarArtists.value.isNotEmpty()) return
        lastResolvedArtist = topArtistName
        viewModelScope.launch {
            val list = fetchSimilarArtistProfiles(topArtistName)
            if (list.isNotEmpty()) {
                _dynamicSimilarArtists.value = list
            }
        }
    }

    private suspend fun getSimilarArtistNames(topArtistName: String): List<String> {
        val lower = topArtistName.trim().lowercase()

        // 1. Indian Electronic / EDM / Dance Scene (e.g. Lost Stories, Nucleya, Zaeden...)
        val indianEdmCluster = listOf(
            "Nucleya", "Zaeden", "Ritviz", "KSHMR", "Sickflip", "Anyasa", "Dualist Inquiry",
            "DJ Chetas", "Progressive Brothers", "MojoJojo", "Sartek", "DJ Shaan",
            "Su Real", "Van Moon", "Arjun Vagale", "Siana Catherine", "Zephyrtone", "Anish Sood"
        )

        // 2. Indian Indie, Folk & Acoustic Pop (e.g. Prateek Kuhad, Anuv Jain...)
        val indianIndieCluster = listOf(
            "Prateek Kuhad", "Anuv Jain", "Jasleen Royal", "The Local Train", "When Chai Met Toast",
            "Taba Chake", "Sanam", "Osho Jain", "Dream Note", "Bharat Chauhan", "Tanmaya Bhatnagar",
            "Raghav Meattle", "Twin Strings", "Aditya A", "Kasyap", "JalRaj"
        )

        // 3. Indian Bollywood Romantic & Film Melodies (e.g. Arijit Singh, Atif Aslam...)
        val bollywoodCluster = listOf(
            "Arijit Singh", "Atif Aslam", "Mohit Chauhan", "KK", "Shreya Ghoshal",
            "Jubin Nautiyal", "Armaan Malik", "Vishal Mishra", "Darshan Raval", "Sonu Nigam",
            "Pritam", "Sachin-Jigar", "Shankar Mahadevan", "Sunidhi Chauhan", "Javed Ali",
            "Akhil Sachdeva", "B Praak", "A.R. Rahman", "Amit Trivedi", "Neeti Mohan"
        )

        // 4. Desi Hip Hop & Indian Rap (e.g. DIVINE, Seedhe Maut, KR$NA...)
        val desiHipHopCluster = listOf(
            "DIVINE", "Seedhe Maut", "KR\$NA", "King", "MC Stan", "Raftaar", "Badshah",
            "Emiway Bantai", "Ikka", "Bella", "Paradox", "Fotty Seven", "EPR", "Dino James",
            "Raga", "Karma", "Talha Anjum", "Talhah Yunus", "Young Stunners"
        )

        // 5. Punjabi Pop & Regional Hits (e.g. AP Dhillon, Diljit Dosanjh...)
        val punjabiCluster = listOf(
            "AP Dhillon", "Diljit Dosanjh", "Karan Aujla", "Sidhu Moose Wala", "Shubh",
            "Talwiinder", "Gurinder Gill", "Amrit Maan", "B Praak", "Jassie Gill",
            "Harrdy Sandhu", "Prem Dhillon", "Jordan Sandhu", "Arjan Dhillon", "Tegi Pannu", "PropheC"
        )

        // 6. South Indian (Tamil, Telugu, Malayalam, Kannada)
        val southIndianCluster = listOf(
            "Anirudh Ravichander", "A.R. Rahman", "Sid Sriram", "Yuvan Shankar Raja", "Harris Jayaraj",
            "Santhosh Narayanan", "Devi Sri Prasad", "Thaman S", "Sushin Shyam", "GV Prakash Kumar",
            "D. Imman", "Hesham Abdul Wahab", "Anurag Kulkarni"
        )

        // 7. Global Electronic & Festival EDM (e.g. Martin Garrix, Avicii, Calvin Harris...)
        val globalEdmCluster = listOf(
            "Martin Garrix", "Avicii", "Calvin Harris", "David Guetta", "The Chainsmokers",
            "Alan Walker", "Marshmello", "Kygo", "DJ Snake", "Tiësto", "Skrillex",
            "Fred again..", "Zedd", "Galantis", "Major Lazer", "Swedish House Mafia", "Alesso", "Hardwell"
        )

        // 8. Western Hip-Hop & Rap (e.g. Kendrick Lamar, Drake, Travis Scott...)
        val westernHipHopCluster = listOf(
            "Kendrick Lamar", "Drake", "J. Cole", "Travis Scott", "21 Savage", "Future",
            "Metro Boomin", "Gunna", "Lil Baby", "A\$AP Rocky", "Playboi Carti", "Lil Uzi Vert",
            "Tyler, The Creator", "Kid Cudi", "Kanye West", "Don Toliver", "Central Cee", "Dave"
        )

        // 9. Global Pop & Mainstream (e.g. The Weeknd, Bruno Mars, Dua Lipa...)
        val globalPopCluster = listOf(
            "The Weeknd", "Bruno Mars", "Dua Lipa", "Post Malone", "Harry Styles",
            "The Kid LAROI", "Charlie Puth", "Shawn Mendes", "Olivia Rodrigo", "Billie Eilish",
            "Sabrina Carpenter", "Tate McRae", "Conan Gray", "Troye Sivan", "Lorde",
            "Taylor Swift", "Ariana Grande", "Justin Bieber", "Ed Sheeran", "Sam Smith"
        )

        // 10. Global R&B & Neo-Soul (e.g. SZA, Frank Ocean, Daniel Caesar...)
        val globalRnbCluster = listOf(
            "SZA", "Frank Ocean", "Daniel Caesar", "Summer Walker", "Jhené Aiko",
            "Brent Faiyaz", "Giveon", "H.E.R.", "Steve Lacy", "Kali Uchis",
            "Kehlani", "Bryson Tiller", "6LACK", "PartyNextDoor", "Leon Bridges", "Snoh Aalegra", "Jorja Smith"
        )

        // 11. Global Indie & Alternative Rock (e.g. Arctic Monkeys, The Neighbourhood...)
        val globalIndieCluster = listOf(
            "Arctic Monkeys", "The Neighbourhood", "Lana Del Rey", "Lorde", "Cigarettes After Sex",
            "Hozier", "Mac DeMarco", "Phoebe Bridgers", "Clairo", "Rex Orange County",
            "Wallows", "Girl in Red", "Dominic Fike", "The 1975", "Cage The Elephant", "Vance Joy"
        )

        // 12. Global Rock & Stadium Rock (e.g. Imagine Dragons, Linkin Park, Coldplay...)
        val globalRockCluster = listOf(
            "Imagine Dragons", "Coldplay", "Linkin Park", "Twenty One Pilots", "Fall Out Boy",
            "Panic! At The Disco", "Green Day", "Foo Fighters", "Red Hot Chili Peppers", "Radiohead",
            "Muse", "Bring Me The Horizon", "The Killers", "OneRepublic", "Paramore"
        )

        // 13. K-Pop (e.g. BTS, BLACKPINK...)
        val kpopCluster = listOf(
            "BTS", "BLACKPINK", "Stray Kids", "NewJeans", "TWICE", "SEVENTEEN",
            "LE SSERAFIM", "ENHYPEN", "TOMORROW X TOGETHER", "Jung Kook", "Jimin", "aespa", "ITZY", "IVE"
        )

        // 14. Latin & Reggaeton (e.g. Bad Bunny, Rauw Alejandro...)
        val latinCluster = listOf(
            "Bad Bunny", "Rauw Alejandro", "J Balvin", "Maluma", "Ozuna", "Daddy Yankee",
            "Karol G", "Rosalía", "Feid", "Myke Towers", "Anuel AA", "Bizarrap"
        )

        // First check static matching with full country & scene awareness
        val matchedPool = when {
            // Indian Electronic / EDM (Lost Stories, Nucleya, Zaeden, Ritviz, KSHMR, Anyasa...)
            listOf("lost stories", "nucleya", "zaeden", "ritviz", "kshmr", "sickflip", "anyasa", "dualist", "chetas", "mojojojo", "sartek", "zephyrtone", "anish sood", "progressive brothers").any { lower.contains(it) } -> indianEdmCluster

            // Indian Indie / Pop (Prateek Kuhad, Anuv Jain, Jasleen Royal...)
            listOf("prateek kuhad", "anuv jain", "anuv", "jasleen royal", "jasleen", "local train", "when chai", "taba chake", "sanam", "osho jain", "dream note", "twin strings", "aditya a").any { lower.contains(it) } -> indianIndieCluster

            // Bollywood / Hindi Film (Arijit Singh, Atif Aslam, Mohit Chauhan...)
            listOf("arijit", "atif", "mohit", "kk", "shreya", "jubin", "armaan", "vishal mishra", "darshan", "sonu nigam", "pritam", "sachin-jigar", "shankar mahadevan", "sunidhi", "javed ali", "akhil sachdeva", "amit trivedi", "neeti mohan").any { lower.contains(it) } -> bollywoodCluster

            // Desi Hip Hop / Indian Rap (DIVINE, Seedhe Maut, KR$NA...)
            listOf("divine", "seedhe maut", "seedhe", "kr\$na", "krsna", "mc stan", "stan", "raftaar", "badshah", "emiway", "ikka", "bella", "paradox", "fotty seven", "epr", "dino james", "raga", "karma", "young stunners", "talha anjum").any { lower.contains(it) } -> desiHipHopCluster

            // Punjabi (AP Dhillon, Diljit Dosanjh, Karan Aujla...)
            listOf("dhillon", "diljit", "karan aujla", "aujla", "sidhu moose", "moose", "shubh", "talwiinder", "gurinder gill", "amrit maan", "jassie gill", "harrdy sandhu", "prem dhillon", "prophec").any { lower.contains(it) } -> punjabiCluster

            // South Indian
            listOf("anirudh", "sid sriram", "yuvan", "harris jayaraj", "santhosh narayanan", "devi sri", "thaman", "sushin", "gv prakash", "hesham").any { lower.contains(it) } -> southIndianCluster

            // K-Pop
            listOf("bts", "blackpink", "stray kids", "newjeans", "twice", "seventeen", "le sserafim", "enhypen", "txt", "jung kook", "jimin", "aespa", "itzy", "ive").any { lower.contains(it) } -> kpopCluster

            // Latin
            listOf("bad bunny", "rauw", "j balvin", "maluma", "ozuna", "daddy yankee", "karol g", "rosalia", "feid", "myke towers").any { lower.contains(it) } -> latinCluster

            // Western Hip-Hop / Rap
            listOf("kendrick", "drake", "j. cole", "cole", "travis", "21 savage", "savage", "future", "metro", "gunna", "baby", "rocky", "carti", "uzi", "tyler", "cudi", "kanye", "don toliver", "central cee", "dave").any { lower.contains(it) } -> westernHipHopCluster

            // Global R&B / Soul
            listOf("sza", "frank ocean", "caesar", "summer walker", "jhené", "jhene", "faiyaz", "giveon", "h.e.r.", "steve lacy", "lacy", "kali uchis", "uchis", "kehlani", "bryson", "6lack", "partynextdoor", "snoh", "jorja").any { lower.contains(it) } -> globalRnbCluster

            // Global Indie
            listOf("arctic", "neighbourhood", "lana", "cigarettes", "hozier", "demarco", "phoebe", "clairo", "rex orange", "wallows", "girl in red", "1975").any { lower.contains(it) } -> globalIndieCluster

            // Global EDM
            listOf("garrix", "avicii", "calvin harris", "guetta", "chainsmokers", "alan walker", "marshmello", "kygo", "dj snake", "snake", "tiesto", "skrillex", "fred again", "zedd", "galantis", "alesso", "hardwell").any { lower.contains(it) } -> globalEdmCluster

            // Global Rock
            listOf("imagine dragons", "linkin", "coldplay", "twenty one", "green day", "foo fighters", "chili peppers", "radiohead", "muse", "killers", "onerepublic", "paramore").any { lower.contains(it) } -> globalRockCluster

            // Global Pop
            listOf("weeknd", "bruno", "dua lipa", "post malone", "styles", "laroi", "puth", "mendes", "olivia", "billie", "sabrina", "taylor", "ariana", "bieber", "sheeran").any { lower.contains(it) } -> globalPopCluster

            else -> null
        }

        // Also fetch live similar artists from metadata API
        val liveSimilar = try {
            innerTubeClient.getSimilarArtistNames(topArtistName)
        } catch (_: Exception) {
            emptyList()
        }

        val combinedPool = mutableListOf<String>()
        if (matchedPool != null) {
            combinedPool.addAll(matchedPool)
        }
        if (liveSimilar.isNotEmpty()) {
            combinedPool.addAll(liveSimilar)
        }
        if (combinedPool.isEmpty()) {
            // Default to Indian Indie / EDM or Global Pop based on origin check
            combinedPool.addAll(if (lower.contains(" ") || lower.length > 5) indianEdmCluster else globalPopCluster)
        }

        // Clean, deduplicate and remove the source artist
        val candidatePool = combinedPool.distinctBy { it.trim().lowercase() }
            .filterNot { it.equals(topArtistName, ignoreCase = true) || lower.contains(it.lowercase()) }

        return candidatePool.shuffled().take(6)
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
