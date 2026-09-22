package com.sielo.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sielo.music.core.audio.PlayerManager
import com.sielo.music.core.audio.model.PlaybackState
import com.sielo.music.core.database.dao.FavoriteTrackDao
import com.sielo.music.core.database.dao.ListeningHistoryDao
import com.sielo.music.core.database.dao.SongStat
import com.sielo.music.core.database.entity.FavoriteTrackEntity
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.innertube.YouTubeArtistImageResolver
import com.sielo.music.core.network.models.ArtistDetails
import com.sielo.music.core.network.models.SieloAlbum
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentlyPlayedAlbum(
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val songId: String,
    val type: String = "Album"
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val favoriteTrackDao: FavoriteTrackDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    val userManager: com.sielo.music.core.auth.UserManager,
    private val playerManager: PlayerManager,
    private val innerTubeClient: InnerTubeClient
) : ViewModel() {

    val currentUser = userManager.currentUser
    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    val favorites: StateFlow<List<FavoriteTrackEntity>> = favoriteTrackDao.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritesCount: StateFlow<List<FavoriteTrackEntity>> = favorites

    val recentHistory: StateFlow<List<ListeningEventEntity>> = listeningHistoryDao.getRecentHistory(40)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val oneYearAgoMs = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000L
    val onHeavyRepeatSongs: StateFlow<List<SongStat>> = listeningHistoryDao.getTopSongs(oneYearAgoMs, 10)
        .flatMapLatest { list ->
            if (list.isEmpty()) listeningHistoryDao.getTopSongs(0L, 10) else flowOf(list)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived recently played albums/collections from history
    val recentlyPlayedAlbums: StateFlow<List<RecentlyPlayedAlbum>> = recentHistory.map { events ->
        val seen = mutableSetOf<String>()
        val result = mutableListOf<RecentlyPlayedAlbum>()
        for (event in events) {
            val albumName = event.albumName?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                ?: event.songTitle
            val key = "$albumName|${event.artistName}".lowercase()
            if (key !in seen) {
                seen.add(key)
                result.add(
                    RecentlyPlayedAlbum(
                        title = albumName,
                        artist = event.artistName,
                        thumbnailUrl = event.thumbnailUrl,
                        songId = event.songId,
                        type = if (event.albumName.isNullOrBlank()) "Single" else "Album"
                    )
                )
            }
            if (result.size >= 12) break
        }
        if (result.isEmpty()) {
            defaultRecentAlbums()
        } else {
            result
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultRecentAlbums())

    // Followed & favorite artists
    private val _favoriteArtists = MutableStateFlow<List<SieloArtist>>(emptyList())
    val favoriteArtists: StateFlow<List<SieloArtist>> = _favoriteArtists.asStateFlow()

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                val followed = user?.favoriteArtists ?: emptyList()
                val artistList = if (followed.isNotEmpty()) {
                    followed.map { name ->
                        val cached = YouTubeArtistImageResolver.getCachedArtistImageUrl(name)
                        SieloArtist(
                            id = name,
                            name = name,
                            imageUrl = cached,
                            role = "Artist"
                        )
                    }
                } else {
                    defaultFavoriteArtists()
                }
                _favoriteArtists.value = artistList

                // Resolve missing artwork in background
                launch(Dispatchers.IO) {
                    val updated = artistList.map { artist ->
                        if (artist.imageUrl.isNullOrBlank()) {
                            val resolved = YouTubeArtistImageResolver.resolveArtistImageUrl(artist.name)
                            artist.copy(imageUrl = resolved)
                        } else artist
                    }
                    _favoriteArtists.value = updated
                }
            }
        }
    }

    // Artist profile viewing
    private val _selectedArtist = MutableStateFlow<ArtistDetails?>(null)
    val selectedArtist: StateFlow<ArtistDetails?> = _selectedArtist.asStateFlow()

    private val _isArtistLoading = MutableStateFlow(false)
    val isArtistLoading: StateFlow<Boolean> = _isArtistLoading.asStateFlow()

    fun openArtist(artist: SieloArtist) {
        val saavnId = if (artist.id.all { it.isDigit() }) artist.id else null
        openArtist(artist.name, artist.imageUrl, saavnId)
    }

    fun openArtist(artistName: String, imageUrl: String? = null, artistId: String? = null) {
        viewModelScope.launch {
            _isArtistLoading.value = true
            _selectedArtist.value = ArtistDetails(
                id = artistId ?: artistName,
                name = artistName,
                imageUrl = imageUrl
            )
            val full = innerTubeClient.getArtistDetails(artistName, imageUrl, artistId)
            if (full != null) {
                _selectedArtist.value = full
            }
            _isArtistLoading.value = false
        }
    }

    fun closeArtist() {
        _selectedArtist.value = null
    }

    suspend fun getAlbumSongs(album: SieloAlbum): List<SieloTrack> {
        if (album.tracks.isNotEmpty()) return album.tracks
        return innerTubeClient.getAlbumSongs(album.id, album.title, album.artist)
    }

    fun playAlbum(track: SieloTrack, queue: List<SieloTrack>) {
        playerManager.playAlbum(track, queue)
    }

    fun playArtistRadio(track: SieloTrack, queue: List<SieloTrack>, artistName: String) {
        playerManager.playArtistRadio(track, queue, artistName)
    }

    // Sonic Capsule & Sound DNA
    val sonicArchetype: StateFlow<String> = recentHistory.map { events ->
        if (events.isEmpty()) {
            "Midnight Explorer"
        } else {
            val genresOrArtists = events.map { it.artistName.lowercase() }
            when {
                genresOrArtists.any { it.contains("arijit") || it.contains("pritam") || it.contains("shreya") } -> "Melodic Acoustic Soul"
                genresOrArtists.any { it.contains("weeknd") || it.contains("dua") || it.contains("taylor") } -> "Midnight Synth Wave"
                genresOrArtists.any { it.contains("diljit") || it.contains("dhillon") || it.contains("karan") } -> "High-Pulse Wave Rider"
                genresOrArtists.any { it.contains("anuv") || it.contains("prateek") || it.contains("kuhad") } -> "Ethereal Indie Nomad"
                else -> "Audiophile Sound Architect"
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Audiophile Sound Architect")

    fun playSonicCapsuleMixtape() {
        val favs = favorites.value
        val hist = recentHistory.value
        val tracks = mutableListOf<SieloTrack>()
        for (f in favs.take(10)) {
            tracks.add(
                SieloTrack(
                    id = f.id,
                    title = f.title,
                    artist = f.artist,
                    thumbnailUrl = f.thumbnailUrl,
                    durationText = "3:30"
                )
            )
        }
        for (h in hist.take(10)) {
            if (tracks.none { it.id == h.songId }) {
                tracks.add(
                    SieloTrack(
                        id = h.songId,
                        title = h.songTitle,
                        artist = h.artistName,
                        album = h.albumName,
                        thumbnailUrl = h.thumbnailUrl,
                        durationText = "3:30"
                    )
                )
            }
        }
        if (tracks.isNotEmpty()) {
            playerManager.playTrack(tracks.first(), tracks)
        } else {
            playRecentAlbum(defaultRecentAlbums().first())
        }
    }

    fun playHeavyRepeatTrack(stat: SongStat) {
        val allStats = onHeavyRepeatSongs.value
        val queue = allStats.map { s ->
            SieloTrack(
                id = s.songId,
                title = s.songTitle,
                artist = s.artistName,
                thumbnailUrl = s.thumbnailUrl,
                durationText = "3:30"
            )
        }
        val target = queue.find { it.id == stat.songId } ?: SieloTrack(
            id = stat.songId,
            title = stat.songTitle,
            artist = stat.artistName,
            thumbnailUrl = stat.thumbnailUrl,
            durationText = "3:30"
        )
        playerManager.playTrack(target, queue.ifEmpty { listOf(target) })
    }

    fun playAllHeavyRepeat() {
        val songs = onHeavyRepeatSongs.value
        if (songs.isNotEmpty()) {
            playHeavyRepeatTrack(songs.first())
        } else {
            playLikedSongs()
        }
    }

    // Preferences states
    private val _streamQuality = MutableStateFlow("Master (320kbps Opus / FLAC)")
    val streamQuality: StateFlow<String> = _streamQuality.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(true)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _gaplessPlayback = MutableStateFlow(true)
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _dynamicColorTheme = MutableStateFlow(true)
    val dynamicColorTheme: StateFlow<Boolean> = _dynamicColorTheme.asStateFlow()

    private val _notificationsNewReleases = MutableStateFlow(true)
    val notificationsNewReleases: StateFlow<Boolean> = _notificationsNewReleases.asStateFlow()

    private val _notificationsPlayback = MutableStateFlow(true)
    val notificationsPlayback: StateFlow<Boolean> = _notificationsPlayback.asStateFlow()

    private val _cacheSize = MutableStateFlow("142 MB")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    fun setStreamQuality(quality: String) {
        _streamQuality.value = quality
    }

    fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
    }

    fun toggleGapless() {
        _gaplessPlayback.value = !_gaplessPlayback.value
    }

    fun toggleDarkTheme() {
        _darkTheme.value = !_darkTheme.value
    }

    fun toggleDynamicColorTheme() {
        _dynamicColorTheme.value = !_dynamicColorTheme.value
    }

    fun toggleNotificationsNewReleases() {
        _notificationsNewReleases.value = !_notificationsNewReleases.value
    }

    fun toggleNotificationsPlayback() {
        _notificationsPlayback.value = !_notificationsPlayback.value
    }

    fun updateProfile(name: String, username: String?, bio: String?) {
        userManager.updateProfile(name, username, bio)
    }

    fun playTrack(track: SieloTrack, queue: List<SieloTrack>) {
        playerManager.playTrack(track, queue)
    }

    fun playLikedSongs() {
        val favs = favorites.value
        if (favs.isNotEmpty()) {
            val tracks = favs.map {
                SieloTrack(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    thumbnailUrl = it.thumbnailUrl,
                    durationText = "3:30"
                )
            }
            playerManager.playTrack(tracks.first(), tracks)
        }
    }

    fun playRecentAlbum(album: RecentlyPlayedAlbum) {
        viewModelScope.launch {
            val tracks = innerTubeClient.search("${album.title} ${album.artist}")
            if (tracks.isNotEmpty()) {
                playerManager.playAlbum(tracks.first(), tracks)
            } else {
                val fallbackTrack = SieloTrack(
                    id = album.songId,
                    title = album.title,
                    artist = album.artist,
                    thumbnailUrl = album.thumbnailUrl,
                    durationText = "3:30"
                )
                playerManager.playTrack(fallbackTrack, listOf(fallbackTrack))
            }
        }
    }

    fun playHistoryItem(event: ListeningEventEntity) {
        val track = SieloTrack(
            id = event.songId,
            title = event.songTitle,
            artist = event.artistName,
            album = event.albumName,
            thumbnailUrl = event.thumbnailUrl,
            durationText = "3:30"
        )
        playerManager.playTrack(track, listOf(track))
    }

    fun toggleFavorite(track: SieloTrack) {
        viewModelScope.launch {
            val favs = favorites.value
            val isFav = favs.any { it.id == track.id }
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
        }
    }

    fun toggleFollowArtist(artistName: String): Boolean {
        return userManager.toggleFollowArtist(artistName)
    }

    fun isArtistFollowed(artistName: String): Boolean {
        return userManager.isArtistFollowed(artistName)
    }

    fun openAuthDialog() {
        userManager.openAuthDialog()
    }

    fun openOnboarding() {
        userManager.openOnboarding()
    }

    fun signOut() {
        userManager.signOut()
    }

    fun clearCache() {
        viewModelScope.launch {
            _cacheSize.value = "0 MB"
        }
    }

    fun deleteAccount(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            userManager.deleteAccount()
            _cacheSize.value = "0 MB"
            onComplete()
        }
    }

    private fun defaultRecentAlbums(): List<RecentlyPlayedAlbum> = listOf(
        RecentlyPlayedAlbum("After Hours", "The Weeknd", "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg", "fW-Mxsnu"),
        RecentlyPlayedAlbum("Starboy", "The Weeknd", "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg", "TcDP-KUl"),
        RecentlyPlayedAlbum("Future Nostalgia", "Dua Lipa", "https://c.saavncdn.com/665/Future-Nostalgia-English-2020-20260306223201-500x500.jpg", "3IoDK8qI"),
        RecentlyPlayedAlbum("Brahmastra", "Pritam & Arijit Singh", "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", "BddP6PYo2gs")
    )

    private fun defaultFavoriteArtists(): List<SieloArtist> = listOf(
        SieloArtist("The Weeknd", "The Weeknd", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop"),
        SieloArtist("Arijit Singh", "Arijit Singh", "https://c.saavncdn.com/artists/Arijit_Singh_002_20240417064843_500x500.jpg"),
        SieloArtist("Dua Lipa", "Dua Lipa", "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop"),
        SieloArtist("Kishore Kumar", "Kishore Kumar", "https://c.saavncdn.com/artists/Kishore_Kumar_500x500.jpg"),
        SieloArtist("Anuv Jain", "Anuv Jain", "https://c.saavncdn.com/artists/Anuv_Jain_000_20230628084833_500x500.jpg")
    )
}
