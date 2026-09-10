package com.sielo.music.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sielo.music.R
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
import com.sielo.music.ui.theme.SurfaceDark
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.viewmodel.ArtistProfile
import com.sielo.music.viewmodel.HomeViewModel
import com.sielo.music.viewmodel.PlaylistCardItem
import com.sielo.music.viewmodel.StationItem

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val recentHistory by viewModel.recentHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val playlistTracks by viewModel.playlistTracks.collectAsState()
    val isPlaylistLoading by viewModel.isPlaylistLoading.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val isArtistLoading by viewModel.isArtistLoading.collectAsState()

    val similarArtists = viewModel.similarArtists
    val featuredStations = viewModel.featuredStations
    val customPlaylists = viewModel.customPlaylists

    // Fallback tracks pool with guaranteed distinct high-quality tracks
    val fallbackTracks = listOf(
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

    val activeTracks = (if (trendingTracks.isNotEmpty()) trendingTracks else fallbackTracks)
        .distinctBy { it.id }
        .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }

    // Handle device system back gesture when artist or playlist is open
    BackHandler(enabled = selectedArtist != null || selectedPlaylist != null) {
        if (selectedArtist != null) {
            viewModel.closeArtist()
        } else if (selectedPlaylist != null) {
            viewModel.closePlaylist()
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
            // Deduplicated slices to guarantee ZERO repeated songs across all sections
            val keepListeningList = if (recentHistory.isNotEmpty()) {
                recentHistory
                    .distinctBy { it.songId }
                    .map { event ->
                        SieloTrack(
                            id = event.songId,
                            title = event.songTitle,
                            artist = event.artistName,
                            album = event.albumName,
                            durationText = "3:24",
                            thumbnailUrl = event.thumbnailUrl
                        )
                    }
                    .take(8)
            } else {
                activeTracks.take(6)
            }

            val usedIds = keepListeningList.map { it.id }.toMutableSet()
            val availableRemaining = activeTracks.filterNot { it.id in usedIds }

            val favoritesList = availableRemaining.take(5).ifEmpty { fallbackTracks.drop(6).take(5) }
            usedIds.addAll(favoritesList.map { it.id })

            val videoTracks = activeTracks.filterNot { it.id in usedIds }.take(4).ifEmpty { fallbackTracks.drop(10).take(4) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                // Top App Bar with App Logo
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PaletteOxfordBlue)
                                    .border(1.dp, BorderGlass, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo),
                                    contentDescription = "Sielo Logo",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sielo",
                                color = PaletteCream,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = MacondoFontFamily,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { /* Search */ }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = PaletteCream,
                                    modifier = Modifier.size(24.dp)
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

                // Section 1: Keep listening (Square Cards Carousel)
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

                // Section 2: Similar to Artist (Circular Artist Avatars Carousel)
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
                                text = "SZA",
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
                        items(similarArtists) { artist ->
                            ArtistCircleCard(
                                artist = artist,
                                onClick = { viewModel.openArtist(artist.name, artist.imageUrl) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Section 3: Your Playlists & Mixes (Bento Cards with Tap-to-Open)
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

                // Section 4: Forgotten favorites (Quick Picks List)
                item {
                    Text(
                        text = "Forgotten favorites",
                        color = PaletteSand,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        favoritesList.forEach { track ->
                            ForgottenFavoriteRow(
                                track = track,
                                onPlay = { viewModel.playTrack(track, favoritesList) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Section 5: Listen together / Stations (Wide Banner Cards)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "STATION",
                            color = PaletteSageGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Listen together",
                            color = PaletteSand,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        items(featuredStations) { station ->
                            StationBannerCard(
                                station = station,
                                onClick = { viewModel.playStation(station) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Section 6: Music videos for you
                item {
                    Text(
                        text = "Music videos for you",
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
                        items(videoTracks) { track ->
                            VideoCard(
                                track = track,
                                onPlay = { viewModel.playTrack(track, videoTracks) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
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
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderSubtle, CircleShape)
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
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaletteOxfordBlue)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
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
fun ForgottenFavoriteRow(
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
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
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
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Options",
            tint = PaletteSlateBlue,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun StationBannerCard(
    station: StationItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = station.bannerUrl,
                contentDescription = station.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PaletteDarkNavy.copy(alpha = 0.65f))
                    .border(1.dp, PaletteSand.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteSand,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = station.title,
            color = PaletteCream,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = station.subtitle,
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VideoCard(
    track: SieloTrack,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clickable { onPlay() }
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(115.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PaletteDarkNavy.copy(alpha = 0.65f))
                    .border(1.dp, PaletteSand.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteSand,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            color = PaletteCream,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
