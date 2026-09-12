package com.sielo.music.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val recentPlayedSongs by viewModel.recentPlayedSongs.collectAsState()
    val rediscoveredFavorites by viewModel.rediscoveredFavorites.collectAsState()
    val topArtistStat by viewModel.topArtistStat.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val playlistTracks by viewModel.playlistTracks.collectAsState()
    val isPlaylistLoading by viewModel.isPlaylistLoading.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val isArtistLoading by viewModel.isArtistLoading.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val genreTracks by viewModel.genreTracks.collectAsState()
    val isGenreLoading by viewModel.isGenreLoading.collectAsState()

    val customPlaylists = viewModel.customPlaylists
    val trendingGenres = viewModel.trendingGenres

    // Dynamic Top Artist
    val topArtistName = topArtistStat.firstOrNull()?.artistName ?: "The Weeknd"
    val dynamicSimilarArtists by viewModel.dynamicSimilarArtists.collectAsState()

    androidx.compose.runtime.LaunchedEffect(topArtistName) {
        viewModel.loadDynamicSimilarArtists(topArtistName)
    }

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
        ArtistProfileScreen(
            artist = selectedArtist!!,
            isLoading = isArtistLoading,
            onBack = { viewModel.closeArtist() },
            onPlayTrack = { track, q -> viewModel.playTrack(track, q) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
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
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        if (isLoading && activeTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PaletteSand)
            }
        } else {
            // Keep Listening list: exactly last 5 played unique songs (replaying keeps the other 4 tracks intact)
            val keepListeningList = remember(recentPlayedSongs, activeTracks) {
                val historyList = recentPlayedSongs.map { event ->
                    SieloTrack(
                        id = event.songId,
                        title = event.songTitle,
                        artist = event.artistName,
                        album = event.albumName,
                        durationText = "3:24",
                        thumbnailUrl = event.thumbnailUrl
                    )
                }.distinctBy { it.id }

                val result = historyList.toMutableList()
                if (result.size < 5) {
                    val pool = (fallbackTracks + activeTracks).distinctBy { it.id }
                    for (t in pool) {
                        if (result.none { it.id == t.id }) {
                            result.add(t)
                        }
                        if (result.size >= 5) break
                    }
                }
                result.take(5)
            }

            // Rediscover Your Favorites: strictly tracks played > 48h ago that haven't been played in the last 48 hours
            val usedIds = keepListeningList.map { it.id }.toSet()
            val rediscoverList = remember(rediscoveredFavorites, usedIds) {
                rediscoveredFavorites.map { event ->
                    SieloTrack(
                        id = event.songId,
                        title = event.songTitle,
                        artist = event.artistName,
                        album = event.albumName,
                        durationText = "3:30",
                        thumbnailUrl = event.thumbnailUrl
                    )
                }.filterNot { it.id in usedIds }.distinctBy { it.id }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                // Top App Bar: Brand name with increased font size and functional search button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateToSearch) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = PaletteCream,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = { /* Settings */ }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = PaletteCream,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Section 1: Keep listening (Exact 5 Played Songs Carousel)
                item {
                    Text(
                        text = "Keep listening",
                        color = PaletteSand,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        items(keepListeningList) { track ->
                            KeepListeningCard(
                                track = track,
                                onPlay = { viewModel.playTrack(track, keepListeningList) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Section 2: Similar to [Most Listened Artist] (Dynamic Artist Name & Similar Artists Carousel)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Similar to",
                            color = TextSecondary,
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
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "More",
                                tint = PaletteSageGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        items(dynamicSimilarArtists) { artist ->
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
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "More",
                                tint = PaletteSageGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        items(customPlaylists) { playlist ->
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
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Songs you loved a few days ago, ready for replay",
                                color = TextSecondary,
                                fontSize = 12.sp
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
                                    onPlay = { viewModel.playTrack(track, rediscoverList) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // Section 5: Explore Trending Music Genres (10 Trending Genres with Song Drilldown)
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Trending Music Genres",
                            color = PaletteSand,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 5 Rows x 2 Columns Non-Scrollable Vertical Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
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
}


@Composable
fun TrendingGenreCard(
    genre: GenreItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(98.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(genre.accentColor).copy(alpha = 0.35f),
                        PaletteOxfordBlue
                    )
                )
            )
            .border(1.dp, Color(genre.accentColor).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = genre.icon,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = genre.name,
                    color = PaletteCream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = genre.description,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PaletteDarkNavy.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Explore",
                    tint = PaletteSand,
                    modifier = Modifier.size(16.dp)
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
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
                        onPlay = { onPlayTrack(track, tracks) }
                    )
                }
            }
        }
    }
}

@Composable
fun KeepListeningCard(
    track: SieloTrack,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onPlay() }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
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
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${track.artist} • ${track.durationText ?: "3:24"}",
            color = TextSecondary,
            fontSize = 12.sp,
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
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = playlist.subtitle,
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RediscoverFavoriteRow(
    track: SieloTrack,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PaletteOxfordBlue)
            .clickable { onPlay() }
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = PaletteSand,
            modifier = Modifier.size(22.dp)
        )
    }
}
