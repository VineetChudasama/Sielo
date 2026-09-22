package com.sielo.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.sielo.music.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.MacondoFontFamily
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import androidx.compose.foundation.basicMarquee
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.UrbanistFontFamily
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.viewmodel.ArtistProfile
import com.sielo.music.viewmodel.GenreItem
import com.sielo.music.viewmodel.HomeViewModel
import com.sielo.music.viewmodel.PlaylistCardItem
import com.sielo.music.ui.components.SongActionsSheet
import androidx.compose.foundation.combinedClickable
import androidx.hilt.navigation.compose.hiltViewModel
import com.sielo.music.viewmodel.PlaylistImportViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    importViewModel: PlaylistImportViewModel? = null,
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val recentPlayedSongs by viewModel.recentPlayedSongs.collectAsState()
    val rediscoveredFavorites by viewModel.rediscoveredFavorites.collectAsState()
    val topArtistStat by viewModel.topArtistStat.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val playlistTracks by viewModel.playlistTracks.collectAsState()
    val isPlaylistLoading by viewModel.isPlaylistLoading.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val isArtistLoading by viewModel.isArtistLoading.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val genreTracks by viewModel.genreTracks.collectAsState()
    val isGenreLoading by viewModel.isGenreLoading.collectAsState()
    val songsForYouTracks by viewModel.songsForYouTracks.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    val customPlaylists = viewModel.customPlaylists
    val trendingGenres = viewModel.trendingGenres
    var selectedSongActionsTrack by remember { mutableStateOf<SieloTrack?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    // Dynamic Top Artist & Similar Artists (synchronized with HomeViewModel refresh)
    val topArtistName by viewModel.currentSimilarToArtist.collectAsState()
    val dynamicSimilarArtists by viewModel.dynamicSimilarArtists.collectAsState()

    // Fallback tracks pool with guaranteed distinct high-quality tracks and official square album art
    val fallbackTracks = listOf(
        SieloTrack("fW-Mxsnu", "Blinding Lights", "The Weeknd", durationText = "3:20", thumbnailUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg"),
        SieloTrack("TcDP-KUl", "Starboy", "The Weeknd ft. Daft Punk", durationText = "3:50", thumbnailUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg"),
        SieloTrack("9q41tYDn", "Die For You", "The Weeknd", durationText = "4:20", thumbnailUrl = "https://c.saavncdn.com/133/Die-For-You-English-2023-20230227063244-500x500.jpg"),
        SieloTrack("tvxo4Jm0", "Save Your Tears", "The Weeknd", durationText = "3:35", thumbnailUrl = "https://c.saavncdn.com/396/The-Highlights-English-2021-20240207045714-500x500.jpg"),
        SieloTrack("NIVEQweX", "After Hours", "The Weeknd", durationText = "6:01", thumbnailUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg"),
        SieloTrack("3IoDK8qI", "Levitating", "Dua Lipa", durationText = "3:23", thumbnailUrl = "https://c.saavncdn.com/665/Future-Nostalgia-English-2020-20260306223201-500x500.jpg"),
        SieloTrack("wwSCc15h", "Shape of You", "Ed Sheeran", durationText = "3:53", thumbnailUrl = "https://c.saavncdn.com/286/WMG_190295851286-English-2017-500x500.jpg"),
        SieloTrack("EWoDxjbu", "New Rules", "Dua Lipa", durationText = "3:29", thumbnailUrl = "https://c.saavncdn.com/343/New-Rules-English-2017-20250327204128-500x500.jpg"),
        SieloTrack("wLxoOff5", "Uptown Funk", "Mark Ronson ft. Bruno Mars", durationText = "4:30", thumbnailUrl = "https://c.saavncdn.com/049/Uptown-Funk-English-2014-500x500.jpg"),
        SieloTrack("kd8JSDbB", "Stay", "The Kid LAROI & Justin Bieber", durationText = "2:21", thumbnailUrl = "https://c.saavncdn.com/895/Stay-English-2021-20210706223809-500x500.jpg")
    )

    val activeTracks = (if (trendingTracks.isNotEmpty()) trendingTracks else fallbackTracks)
        .distinctBy { it.id }
        .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }

    // Handle device system back gesture when artist, playlist, or genre is open
    BackHandler(enabled = selectedArtist != null || selectedPlaylist != null || selectedGenre != null) {
        when {
            selectedArtist != null -> viewModel.closeArtist()
            selectedPlaylist != null -> viewModel.closePlaylist()
            selectedGenre != null -> viewModel.closeGenre()
        }
    }

    // Display Artist Profile Screen if an artist is selected
    if (selectedArtist != null) {
        val artist = selectedArtist!!
        ArtistProfileScreen(
            artist = artist,
            isLoading = isArtistLoading,
            onBack = { viewModel.closeArtist() },
            onPlayTrack = { track, q -> viewModel.playTrack(track, q) },
            onPlayAlbum = { track, q -> viewModel.playAlbum(track, q) },
            onPlayRadio = { track, q, artistName -> viewModel.playArtistRadio(track, q, artistName) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onOpenArtist = { a -> viewModel.openArtist(a) },
            isFollowed = viewModel.isArtistFollowed(artist.name),
            onToggleFollow = { name -> viewModel.toggleFollowArtist(name) },
            currentTrackId = playbackState.currentTrack?.id,
            isPlaying = playbackState.isPlaying,
            onLoadAlbumTracks = { album -> viewModel.getAlbumSongs(album) },
            modifier = modifier
        )
        return
    }

    // Display Playlist Detail Screen if a playlist is selected
    if (selectedPlaylist != null) {
        PlaylistDetailScreen(
            playlist = selectedPlaylist!!,
            tracks = playlistTracks,
            isLoading = isPlaylistLoading,
            onBack = { viewModel.closePlaylist() },
            onPlayTrack = { track, q -> viewModel.playTrack(track, q) },
            modifier = modifier
        )
        return
    }

    // Display Genre Detail Screen if a genre is selected
    if (selectedGenre != null) {
        GenreDetailScreen(
            genre = selectedGenre!!,
            tracks = genreTracks,
            isLoading = isGenreLoading,
            onBack = { viewModel.closeGenre() },
            onPlayTrack = { track, q -> viewModel.playTrack(track, q) },
            onMoreTrack = { selectedSongActionsTrack = it },
            modifier = modifier
        )
        return
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshHome() },
        state = pullState,
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy),
        indicator = {
            // Branded Sielo refresh indicator — only visible during a real top overscroll pull or refresh.
            // Taps and horizontal card swipes never produce overscroll, so they can never show this.
            val progress = pullState.distanceFraction.coerceIn(0f, 1.5f)
            if (progress > 0.02f || isRefreshing) {
                val isTriggerReached = progress >= 1f || isRefreshing
                val glowAlpha by animateFloatAsState(
                    targetValue = if (isTriggerReached) 0.65f else (progress * 0.35f).coerceIn(0f, 0.35f),
                    animationSpec = tween(durationMillis = 200),
                    label = "glowAlpha"
                )
                val glowScale by animateFloatAsState(
                    targetValue = if (isTriggerReached) 1.15f else 0.9f,
                    animationSpec = tween(durationMillis = 200),
                    label = "glowScale"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .graphicsLayer {
                                alpha = glowAlpha
                                scaleX = glowScale
                                scaleY = glowScale
                            }
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        PaletteSand.copy(alpha = 0.4f),
                                        PaletteSand.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = PaletteSand,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(46.dp)
                        )
                    } else if (progress > 0.05f) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0.05f, 1f) },
                            color = PaletteSand,
                            trackColor = PaletteSand.copy(alpha = 0.15f),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                    Image(
                        painter = painterResource(id = R.drawable.ic_refresh_logo),
                        contentDescription = "Sielo Refresh Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .graphicsLayer {
                                rotationZ = if (isRefreshing) spinRotation else (progress * 360f)
                            }
                    )
                }
            }
        }
    ) {
        if (isLoading && activeTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PaletteSand)
            }
        } else {
            // Keep Listening list: strictly user-played tracks, never backfilled with random tracks
            val keepListeningList = remember(recentPlayedSongs) {
                recentPlayedSongs.map { event ->
                    val durSec = (event.songDurationMs / 1000).coerceAtLeast(0L)
                    val durText = if (durSec > 0) {
                        val m = durSec / 60
                        val s = durSec % 60
                        "$m:${s.toString().padStart(2, '0')}"
                    } else null
                    SieloTrack(
                        id = event.songId,
                        title = event.songTitle,
                        artist = event.artistName,
                        album = event.albumName,
                        durationText = durText,
                        durationSeconds = durSec,
                        thumbnailUrl = event.thumbnailUrl
                    )
                }.distinctBy { it.id }.take(5)
            }

            // Rediscover Your Favorites: strictly tracks played > 48h ago that haven't been played in the last 48 hours
            val usedIds = keepListeningList.map { it.id }.toSet()
            val rediscoverList = remember(rediscoveredFavorites, usedIds) {
                rediscoveredFavorites.map { event ->
                    val durSec = (event.songDurationMs / 1000).coerceAtLeast(0L)
                    val durText = if (durSec > 0) {
                        val m = durSec / 60
                        val s = durSec % 60
                        "$m:${s.toString().padStart(2, '0')}"
                    } else null
                    SieloTrack(
                        id = event.songId,
                        title = event.songTitle,
                        artist = event.artistName,
                        album = event.albumName,
                        durationText = durText,
                        durationSeconds = durSec,
                        thumbnailUrl = event.thumbnailUrl
                    )
                }.filterNot { it.id in usedIds }.distinctBy { it.id }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 210.dp)
            ) {
                // Top App Bar: Brand name with increased font size and functional search & refresh button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sielo",
                            color = PaletteCream,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = MacondoFontFamily,
                            letterSpacing = 1.sp
                        )

                        IconButton(onClick = onNavigateToSearch) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = PaletteCream,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Section 1: Keep listening (Shown strictly ONLY if user has listened to at least one song)
                if (recentPlayedSongs.isNotEmpty() && keepListeningList.isNotEmpty()) {
                    item {
                        Text(
                            text = "Keep listening",
                            color = PaletteSand,
                            fontFamily = SoraFontFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            items(keepListeningList, key = { it.id }) { track ->
                                KeepListeningCard(
                                    track = track,
                                    onPlay = { viewModel.playTrack(track, keepListeningList) },
                                    onMore = { selectedSongActionsTrack = track }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // Section: "Songs for you" (Top songs from the user's selected artists)
                if (songsForYouTracks.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "CURATED FOR YOU",
                                color = PaletteSageGreen,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Songs for you",
                                color = PaletteSand,
                                fontFamily = SoraFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            items(songsForYouTracks, key = { it.id }) { track ->
                                KeepListeningCard(
                                    track = track,
                                    onPlay = { viewModel.playTrack(track, songsForYouTracks) },
                                    onMore = { selectedSongActionsTrack = track }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // Section 2: Similar to [Artist] (Dynamic Artist Name & Similar Artists Carousel)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Similar to",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = topArtistName,
                                color = PaletteSand,
                                fontFamily = SoraFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        items(dynamicSimilarArtists.filter { it.imageUrl.isNotBlank() }, key = { it.name }) { artist ->
                            ArtistCircleCard(
                                artist = artist,
                                onClick = { viewModel.openArtist(artist.name, artist.imageUrl) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Section 3: Your Playlists & Mixes
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Your playlists",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Curated For You",
                                color = PaletteSand,
                                fontFamily = SoraFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        item {
                            ImportPlaylistBentoCard(
                                onClick = { showImportDialog = true }
                            )
                        }

                        items(customPlaylists, key = { it.title }) { playlist ->
                            PlaylistBentoCard(
                                playlist = playlist,
                                onClick = { viewModel.openPlaylist(playlist) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Section 4: Rediscover Your Favorites (Only if user has songs played > 48h ago not in last 48h)
                if (rediscoverList.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Rediscover Your Favorites",
                                color = PaletteSand,
                                fontFamily = SoraFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Songs you loved a few days ago, ready for replay",
                                color = TextSecondary,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rediscoverList.forEach { track ->
                                RediscoverFavoriteRow(
                                    track = track,
                                    onPlay = { viewModel.playTrack(track, rediscoverList) },
                                    onMoreClick = { selectedSongActionsTrack = track }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // Section 5: Explore Trending Music Genres (10 Trending Genres with Song Drilldown)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "DISCOVER",
                            color = PaletteSageGreen,
                            fontFamily = SoraFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Trending Music Genres",
                            color = PaletteSand,
                            fontFamily = SoraFontFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 5 Rows x 2 Columns Non-Scrollable Vertical Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (chunk in trendingGenres.chunked(2)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (genre in chunk) {
                                    TrendingGenreCard(
                                        genre = genre,
                                        onClick = { viewModel.openGenre(genre) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (chunk.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // Song Actions Sheet
    selectedSongActionsTrack?.let { track ->
        SongActionsSheet(
            track = track,
            onDismiss = { selectedSongActionsTrack = null }
        )
    }

    val activeImportVm = importViewModel ?: hiltViewModel<PlaylistImportViewModel>()
    if (showImportDialog) {
        ImportPlaylistDialog(
            viewModel = activeImportVm,
            onDismiss = { showImportDialog = false }
        )
    }
}

@Composable
private fun ImportPlaylistBentoCard(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PaletteOxfordBlue,
                        Color(0xFF1E2D44),
                        PaletteDarkNavy
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        PaletteSand.copy(alpha = 0.6f),
                        BorderGlass
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PaletteSand.copy(alpha = 0.15f))
                    .border(1.dp, PaletteSand.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Import Playlist",
                    tint = PaletteSand,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Import",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "YouTube & Spotify",
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
fun TrendingGenreCard(
    genre: GenreItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(genre.accentColor)
    Box(
        modifier = modifier
            .height(116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.32f),
                        accent.copy(alpha = 0.12f),
                        PaletteOxfordBlue.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.50f),
                        BorderGlass
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Emoji badge + Explore indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.30f))
                        .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = genre.icon,
                        fontSize = 17.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(PaletteDarkNavy.copy(alpha = 0.65f))
                        .border(1.dp, BorderGlass, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Explore",
                        tint = PaletteSand,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Genre Name & Description (clean, no broken marquee)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = genre.name,
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = genre.description,
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GenreDetailScreen(
    genre: GenreItem,
    tracks: List<SieloTrack>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (SieloTrack, List<SieloTrack>) -> Unit,
    onMoreTrack: (SieloTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 210.dp)
        ) {
            // Header Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(genre.accentColor).copy(alpha = 0.5f),
                                    PaletteOxfordBlue,
                                    PaletteDarkNavy
                                )
                            )
                        )
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(top = 20.dp, start = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PaletteDarkNavy.copy(alpha = 0.75f))
                            .border(1.dp, BorderGlass, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PaletteCream
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "${genre.icon} TRENDING GENRE",
                            color = PaletteSageGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = genre.name,
                            color = PaletteCream,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = genre.description,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Play All Action Button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(PaletteSand)
                            .clickable {
                                if (tracks.isNotEmpty()) {
                                    onPlayTrack(tracks.first(), tracks)
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play All",
                                tint = PaletteDarkNavy,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "PLAY TOP SONGS",
                                color = PaletteDarkNavy,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Genre Tracks List
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PaletteSand)
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading trending songs for ${genre.name}...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                itemsIndexed(tracks) { index, track ->
                    PlaylistTrackRow(
                        index = index + 1,
                        track = track,
                        onPlay = { onPlayTrack(track, tracks) },
                        onMoreClick = { onMoreTrack(track) }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun KeepListeningCard(
    track: SieloTrack,
    onPlay: () -> Unit,
    onMore: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onMore
            )
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            com.sielo.music.ui.components.SieloSongArtwork(
                thumbnailUrl = track.thumbnailUrl,
                title = track.title,
                artist = track.artist,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp)
            )

            // Frosted Play Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PaletteDarkNavy.copy(alpha = 0.65f))
                    .border(1.dp, PaletteSand.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteSand,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            color = PaletteCream,
            fontFamily = UrbanistFontFamily,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (track.formattedDuration.isNotBlank()) "${track.artist} • ${track.formattedDuration}" else track.artist,
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistCircleCard(
    artist: ArtistProfile,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clickable { onClick() }
    ) {
        com.sielo.music.ui.components.SieloArtistPhoto(
            imageUrl = artist.imageUrl,
            name = artist.name,
            modifier = Modifier.size(86.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            color = PaletteCream,
            fontFamily = UrbanistFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlaylistBentoCard(
    playlist: PlaylistCardItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        if (playlist.isLiked) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaletteSand)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = PaletteDarkNavy,
                    modifier = Modifier.size(54.dp)
                )
            }
        } else {
            com.sielo.music.ui.components.SieloSongArtwork(
                thumbnailUrl = playlist.coverUrl,
                title = playlist.title,
                artist = playlist.genre,
                modifier = Modifier
                    .size(140.dp)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.title,
            color = PaletteCream,
            fontFamily = UrbanistFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = playlist.subtitle,
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RediscoverFavoriteRow(
    track: SieloTrack,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PaletteOxfordBlue)
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onMoreClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.sielo.music.ui.components.SieloSongArtwork(
                thumbnailUrl = track.thumbnailUrl,
                title = track.title,
                artist = track.artist,
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = track.title,
                    color = PaletteCream,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = PaletteSand,
                modifier = Modifier.size(22.dp)
            )
            IconButton(
                onClick = onMoreClick,
                modifier = androidx.compose.ui.Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = PaletteSand.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
