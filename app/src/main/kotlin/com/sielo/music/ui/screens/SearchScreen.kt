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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.UrbanistFontFamily
import com.sielo.music.viewmodel.SearchViewModel

data class GenreCard(
    val title: String,
    val query: String,
    val imageUrl: String,
    val gradientColors: List<Color>
)

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

    var isDedicatedSearchOpen by remember { mutableStateOf(false) }

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
        GenreCard(
            title = "Pop & Hits",
            query = "Pop Hits 2026",
            imageUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop",
            gradientColors = listOf(Color(0xFF8338EC).copy(alpha = 0.55f), Color(0xFF0F172A).copy(alpha = 0.95f))
        ),
        GenreCard(
            title = "Hip-Hop & R&B",
            query = "Hip Hop R&B Hits",
            imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?q=80&w=600&auto=format&fit=crop",
            gradientColors = listOf(Color(0xFFD4A373).copy(alpha = 0.55f), Color(0xFF1A120B).copy(alpha = 0.95f))
        ),
        GenreCard(
            title = "Chill & Lo-Fi",
            query = "Chill Lo-Fi Study Beats",
            imageUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?q=80&w=600&auto=format&fit=crop",
            gradientColors = listOf(Color(0xFF2A9D8F).copy(alpha = 0.55f), Color(0xFF0D2522).copy(alpha = 0.95f))
        ),
        GenreCard(
            title = "Indie & Alt",
            query = "Indie Alternative Hits",
            imageUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?q=80&w=600&auto=format&fit=crop",
            gradientColors = listOf(Color(0xFFE76F51).copy(alpha = 0.55f), Color(0xFF22110D).copy(alpha = 0.95f))
        ),
        GenreCard(
            title = "Electronic & Dance",
            query = "Electronic Dance EDM",
            imageUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop",
            gradientColors = listOf(Color(0xFF00B4D8).copy(alpha = 0.55f), Color(0xFF081B29).copy(alpha = 0.95f))
        ),
        GenreCard(
            title = "Bollywood & Sufi",
            query = "Arijit Singh Bollywood Hits",
            imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop",
            gradientColors = listOf(Color(0xFFE63946).copy(alpha = 0.55f), Color(0xFF1E0A0D).copy(alpha = 0.95f))
        ),
        GenreCard(
            title = "Rock & Classic",
            query = "Classic Modern Rock Hits",
            imageUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?q=80&w=600&auto=format&fit=crop",
            gradientColors = listOf(Color(0xFF6C1D45).copy(alpha = 0.55f), Color(0xFF14070E).copy(alpha = 0.95f))
        ),
        GenreCard(
            title = "Workout & Energy",
            query = "Workout Energy Motivation Beats",
            imageUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=600&auto=format&fit=crop",
            gradientColors = listOf(Color(0xFFF77F00).copy(alpha = 0.55f), Color(0xFF220C00).copy(alpha = 0.95f))
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        if (isDedicatedSearchOpen || searchQuery.isNotBlank()) {
            // ==========================================
            // DEDICATED SEARCH SCREEN VIEW
            // ==========================================
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(isDedicatedSearchOpen) {
                if (isDedicatedSearchOpen && searchQuery.isBlank()) {
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }

            BackHandler {
                isDedicatedSearchOpen = false
                viewModel.clearSearch()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                // Top Search Bar with Back Navigation Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isDedicatedSearchOpen = false
                            viewModel.clearSearch()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PaletteSand,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Songs, artists, albums...", color = TextSecondary, fontFamily = UrbanistFontFamily, fontSize = 14.sp) },
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.submitSearch(searchQuery)
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
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
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                                fontFamily = UrbanistFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (searchQuery.isBlank()) {
                    // Full Dropdown / List of ALL Past Searches
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        if (recentSearches.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "All Past Searches",
                                        color = TextSecondary,
                                        fontFamily = UrbanistFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Clear all",
                                        color = PaletteSageGreen,
                                        fontFamily = UrbanistFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable { viewModel.clearAllSearches() }
                                    )
                                }
                            }

                            items(recentSearches) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.submitSearch(item.query) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = PaletteSlateBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = item.query,
                                            color = PaletteCream,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteSearchQuery(item.query) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Remove",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Type to search songs, artists, or albums...",
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PaletteSand)
                    }
                } else {
                    // Live Search Results (Ranked by Relevance)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        // 1. Artists Section
                        if (filterCategory == "Artists" || (filterCategory == "All" && artistResults.isNotEmpty())) {
                            item {
                                Text(
                                    text = "Artists",
                                    color = PaletteSand,
                                    fontFamily = SoraFontFamily,
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

                        // 2. Songs Section
                        if (filterCategory != "Artists" && searchResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Songs",
                                    color = PaletteSand,
                                    fontFamily = SoraFontFamily,
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
        } else {
            // ==========================================
            // MAIN SEARCH LANDING SCREEN (IDLE)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp)
            ) {
                // Header
                Text(
                    text = "Search",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tappable Search Bar (Opens dedicated search screen on tap)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PaletteOxfordBlue)
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                        .clickable { isDedicatedSearchOpen = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = PaletteSand,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Songs, artists, genres...",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    // Recent Searches Chips (Keep ONLY past 3 searches in the scroll)
                    if (recentSearches.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Searches",
                                        color = PaletteCream,
                                        fontFamily = SoraFontFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Clear all",
                                        color = PaletteSageGreen,
                                        fontFamily = UrbanistFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable { viewModel.clearAllSearches() }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(recentSearches.take(3)) { item ->
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(PaletteOxfordBlue)
                                                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                                                .clickable {
                                                    isDedicatedSearchOpen = true
                                                    viewModel.submitSearch(item.query)
                                                }
                                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = PaletteSlateBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = item.query,
                                                color = PaletteCream,
                                                fontFamily = UrbanistFontFamily,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { viewModel.deleteSearchQuery(item.query) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Delete",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Previously Searched & Played Songs
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
                                    fontFamily = SoraFontFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Quick replay from your recent searches",
                                    color = TextSecondary,
                                    fontFamily = UrbanistFontFamily,
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

                    // Browse Categories Section with Themed Backgrounds
                    item {
                        Text(
                            text = "Browse Categories",
                            color = PaletteCream,
                            fontFamily = SoraFontFamily,
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                                .height(96.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                                                .clickable {
                                                    isDedicatedSearchOpen = true
                                                    viewModel.submitSearch(genre.query)
                                                }
                                        ) {
                                            // Thematic Image
                                            AsyncImage(
                                                model = genre.imageUrl,
                                                contentDescription = genre.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Atmospheric Genre Color Gradient
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = genre.gradientColors,
                                                            start = Offset(0f, 0f),
                                                            end = Offset(300f, 300f)
                                                        )
                                                    )
                                            )

                                            // Text Readability Ambient Fade
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.35f),
                                                                Color.Black.copy(alpha = 0.80f)
                                                            )
                                                        )
                                                    )
                                            )

                                            // Title
                                            Text(
                                                text = genre.title,
                                                color = PaletteCream,
                                                fontFamily = SoraFontFamily,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
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
            fontFamily = UrbanistFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = event.artistName,
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
            fontFamily = UrbanistFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "Artist",
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
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
                        fontFamily = UrbanistFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Artist • Tap to view profile & albums",
                        color = PaletteSageGreen,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
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
                        fontFamily = UrbanistFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${track.artist} • ${track.album ?: "Single"}",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
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
