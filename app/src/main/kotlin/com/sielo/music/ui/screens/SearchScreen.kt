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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.viewmodel.SearchViewModel

data class GenreCard(val title: String, val query: String, val bgColor: Color)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterCategory by viewModel.filterCategory.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val artistResults by viewModel.artistResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val isArtistLoading by viewModel.isArtistLoading.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val previousPlayedSongs by viewModel.previousPlayedSongs.collectAsState()

    var isSearchFocused by remember { mutableStateOf(false) }

    // Handle device system back gesture when artist is open or search has text/focus
    BackHandler(enabled = selectedArtist != null || searchQuery.isNotEmpty() || isSearchFocused) {
        when {
            selectedArtist != null -> viewModel.closeArtist()
            isSearchFocused -> isSearchFocused = false
            searchQuery.isNotEmpty() -> viewModel.clearSearch()
        }
    }

    // Render Artist Profile Screen if an artist is selected
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

    val categories = listOf("All", "Songs", "Artists")

    val genres = listOf(
        GenreCard("Pop & Hits", "Pop Hits 2026", PaletteOxfordBlue),
        GenreCard("Hip-Hop & R&B", "Hip Hop R&B Hits", Color(0xFF1B263B)),
        GenreCard("Chill & Lo-Fi", "Chill Lo-Fi Study Beats", Color(0xFF23352A)),
        GenreCard("Indie & Alt", "Indie Alternative Hits", Color(0xFF332D22)),
        GenreCard("Electronic & Dance", "Electronic Dance EDM", Color(0xFF18283E)),
        GenreCard("Bollywood & Sufi", "Arijit Singh Bollywood Hits", Color(0xFF273642))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp)
        ) {
            // Header
            Text(
                text = "Search",
                color = PaletteCream,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar with Focus Tracking
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Songs, artists, genres...", color = TextSecondary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = PaletteSand)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .onFocusChanged { isSearchFocused = it.isFocused },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PaletteOxfordBlue,
                    unfocusedContainerColor = PaletteOxfordBlue,
                    focusedBorderColor = PaletteSand,
                    unfocusedBorderColor = BorderGlass,
                    focusedTextColor = PaletteCream,
                    unfocusedTextColor = PaletteCream
                ),
                singleLine = true
            )

            // Previous Searches Dropdown Box (appears when focused & searches exist)
            if (isSearchFocused && recentSearches.isNotEmpty() && searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PaletteOxfordBlue)
                        .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Clear all",
                            color = PaletteSageGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { viewModel.clearAllSearches() }
                        )
                    }

                    recentSearches.take(5).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isSearchFocused = false
                                    viewModel.submitSearch(item.query)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Recent",
                                    tint = PaletteSlateBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = item.query,
                                    color = PaletteCream,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteSearchQuery(item.query) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Remove",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == filterCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PaletteSand else PaletteOxfordBlue)
                            .border(1.dp, if (isSelected) PaletteSand else BorderGlass, RoundedCornerShape(12.dp))
                            .clickable { viewModel.setCategory(category) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) PaletteDarkNavy else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PaletteSand)
                }
            } else if (searchQuery.isBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    // Previously Searched & Played Songs (Above Browse Categories)
                    if (previousPlayedSongs.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    text = "Recently Played & Searched",
                                    color = PaletteCream,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Quick replay from your recent searches",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(bottom = 24.dp)
                            ) {
                                items(previousPlayedSongs) { event ->
                                    PreviousPlayedTrackCard(
                                        event = event,
                                        onPlay = { viewModel.playSearchPlayEvent(event) }
                                    )
                                }
                            }
                        }
                    }

                    // Browse Categories Section
                    item {
                        Text(
                            text = "Browse Categories",
                            color = PaletteCream,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (rowChunk in genres.chunked(2)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    for (genre in rowChunk) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(84.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(genre.bgColor)
                                                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                                                .clickable {
                                                    isSearchFocused = false
                                                    viewModel.submitSearch(genre.query)
                                                }
                                                .padding(14.dp),
                                            contentAlignment = Alignment.BottomStart
                                        ) {
                                            Text(
                                                text = genre.title,
                                                color = PaletteCream,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Search Results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    // 1. Artists Section (when "All" or "Artists" category)
                    if (filterCategory == "Artists" || (filterCategory == "All" && artistResults.isNotEmpty())) {
                        item {
                            Text(
                                text = "Artists",
                                color = PaletteSand,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }

                        if (filterCategory == "Artists") {
                            items(artistResults) { artist ->
                                SearchArtistFullRow(
                                    artist = artist,
                                    onClick = { viewModel.openArtist(artist) }
                                )
                            }
                        } else {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    items(artistResults) { artist ->
                                        SearchArtistCard(
                                            artist = artist,
                                            onClick = { viewModel.openArtist(artist) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }

                    // 2. Songs Section (when "All" or "Songs" category)
                    if (filterCategory != "Artists" && searchResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Songs",
                                color = PaletteSand,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }

                        items(searchResults) { track ->
                            SearchResultTrackRow(
                                track = track,
                                onPlay = { viewModel.playTrack(track, searchResults) },
                                onFavorite = { viewModel.toggleFavorite(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviousPlayedTrackCard(
    event: com.sielo.music.core.database.entity.SearchPlayHistoryEntity,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onPlay() }
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            com.sielo.music.ui.components.SieloSongArtwork(
                thumbnailUrl = event.thumbnailUrl,
                title = event.songTitle,
                artist = event.artistName,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp)
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
            text = event.songTitle,
            color = PaletteCream,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = event.artistName,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SearchArtistCard(
    artist: SieloArtist,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clickable { onClick() }
    ) {
        com.sielo.music.ui.components.SieloArtistPhoto(
            imageUrl = artist.imageUrl,
            name = artist.name,
            modifier = Modifier.size(86.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            color = PaletteCream,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "Artist",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun SearchArtistFullRow(
    artist: SieloArtist,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.sielo.music.ui.components.SieloArtistPhoto(
                    imageUrl = artist.imageUrl,
                    name = artist.name,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = artist.name,
                        color = PaletteCream,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Artist • Tap to view profile & albums",
                        color = PaletteSageGreen,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "View Profile",
                tint = PaletteSlateBlue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SearchResultTrackRow(
    track: SieloTrack,
    onPlay: () -> Unit,
    onFavorite: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.size(48.dp),
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${track.artist} • ${track.album ?: "Single"}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = PaletteSlateBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
