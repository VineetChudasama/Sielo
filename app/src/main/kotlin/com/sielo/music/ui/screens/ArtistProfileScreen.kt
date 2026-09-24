package com.sielo.music.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.hilt.navigation.compose.hiltViewModel
import com.sielo.music.ui.components.SongActionsSheet
import com.sielo.music.viewmodel.SongActionsViewModel
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sielo.music.core.network.innertube.TrackMatchValidator
import com.sielo.music.core.network.models.ArtistDetails
import com.sielo.music.core.network.models.SieloAlbum
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.ui.components.AlbumHero
import com.sielo.music.ui.components.AnimatedEqualizerBars
import com.sielo.music.ui.components.AnimatedShuffleIcon
import com.sielo.music.ui.components.SieloArtistPhoto
import com.sielo.music.ui.components.SieloSongArtwork
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderHighlight
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily

/**
 * Universal Profile Tabs: Music and About (discography is removed overall as requested).
 */
enum class ArtistProfileTab(val label: String) {
    MUSIC("MUSIC"),
    ABOUT("ABOUT"),
    RELATED("RELATED")
}

/**
 * Sielo Universal Artist Profile Screen.
 * Highly polished, cinematic, mobile-first profile dynamically rendering for EVERY artist.
 */
@Composable
fun ArtistProfileScreen(
    artist: ArtistDetails,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (SieloTrack, List<SieloTrack>) -> Unit,
    onPlayAlbum: ((SieloTrack, List<SieloTrack>) -> Unit)? = null,
    onPlayRadio: ((SieloTrack, List<SieloTrack>, String) -> Unit)? = null,
    onToggleFavorite: (SieloTrack) -> Unit = {},
    onOpenArtist: (SieloArtist) -> Unit = {},
    isFollowed: Boolean = false,
    onToggleFollow: (String) -> Unit = {},
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onLoadAlbumTracks: suspend (SieloAlbum) -> List<SieloTrack> = { it.tracks },
    modifier: Modifier = Modifier
) {
    // Album detail view state: when an album is tapped, open and list its songs
    var viewingAlbum by remember { mutableStateOf<SieloAlbum?>(null) }

    // Intercept back gesture: if viewing an album, return to the artist profile
    BackHandler {
        if (viewingAlbum != null) {
            viewingAlbum = null
        } else {
            onBack()
        }
    }

    // Render Album Detail View if an album is tapped
    if (viewingAlbum != null) {
        ArtistAlbumDetailView(
            album = viewingAlbum!!,
            artistName = artist.name,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { viewingAlbum = null },
            onPlayTrack = onPlayTrack,
            onPlayAlbum = onPlayAlbum,
            onToggleFavorite = onToggleFavorite,
            onLoadAlbumTracks = onLoadAlbumTracks,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Reset scroll to the top whenever a new artist is selected (fixes opening page from below)
    LaunchedEffect(artist.id, artist.name) {
        listState.scrollToItem(0, 0)
    }

    // Collapse detection for sticky top bar
    val headerAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 260f).coerceIn(0f, 1f)
        }
    }

    val isCollapsed by remember {
        derivedStateOf { headerAlpha > 0.65f }
    }

    val availableTabs = remember(artist.similarArtists) {
        if (artist.similarArtists.isNotEmpty()) {
            listOf(ArtistProfileTab.MUSIC, ArtistProfileTab.ABOUT, ArtistProfileTab.RELATED)
        } else {
            listOf(ArtistProfileTab.MUSIC, ArtistProfileTab.ABOUT)
        }
    }
    var selectedTab by remember(artist.id) { mutableStateOf(ArtistProfileTab.MUSIC) }
    var isFollowingState by remember(isFollowed, artist.name) { mutableStateOf(isFollowed) }
    var showMoreDropdown by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var isGeneratingCard by remember { mutableStateOf(false) }
    var selectedSongActionsTrack by remember { mutableStateOf<SieloTrack?>(null) }
    val songActionsVm: SongActionsViewModel = hiltViewModel()
    val favoriteEntities by songActionsVm.favorites.collectAsState(initial = emptyList())
    val favoriteIds = remember(favoriteEntities) { favoriteEntities.map { it.id }.toSet() }
    val coroutineScope = rememberCoroutineScope()

    // Sanitize display fields so no "null" or "Null" strings are shown
    val cleanFollowers = artist.followerCount?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("0", ignoreCase = true) }
    val cleanListeners = artist.monthlyListeners?.takeIf { !it.equals("null", ignoreCase = true) }
    val cleanLanguage = artist.dominantLanguage?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        val latest = remember(artist) {
            val candidate = artist.latestAlbum
            val allAlbums = (artist.originalAlbums + artist.singles + artist.pastAlbums + listOfNotNull(candidate))
                .distinctBy { it.id }
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
            allAlbums.maxByOrNull { extractYear(it.year ?: it.releaseDate) }
                ?: candidate?.takeIf { !TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }

        if (isLoading) {
            ArtistProfileSkeleton(onBack = onBack)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 210.dp)
            ) {
                // 1. HERO HEADER WITH CINEMATIC PARALLAX & IDENTITY
                item {
                    ArtistHeroHeader(artist = artist)
                }

                // 2. PRIMARY ACTION ROW (Play, Radio with artist-only queue, Follow, Share)
                item {
                    ArtistPrimaryActionRow(
                        artist = artist,
                        isFollowing = isFollowingState,
                        onPlayAll = {
                            if (artist.topSongs.isNotEmpty()) {
                                onPlayTrack(artist.topSongs.first(), artist.topSongs)
                            }
                        },
                        onRadio = {
                            if (artist.topSongs.isNotEmpty()) {
                                if (onPlayRadio != null) {
                                    onPlayRadio(artist.topSongs.first(), artist.topSongs, artist.name)
                                } else {
                                    val shuffled = artist.topSongs.shuffled()
                                    onPlayTrack(shuffled.first(), shuffled)
                                }
                            }
                        },
                        onToggleFollow = {
                            isFollowingState = !isFollowingState
                            onToggleFollow(artist.name)
                        },
                        onShare = {
                            showShareSheet = true
                        }
                    )
                }

                // 3. DYNAMIC STATS ROW (Real metrics only, discography removed)
                // Count only actual studio albums. Featured/soundtrack releases and
                // singles/EPs must not inflate the studio-album metric.
                val studioAlbums = (artist.originalAlbums + artist.pastAlbums.filter {
                    it.type != "Single" && it.type != "EP" && it.type != "Soundtrack"
                })
                    .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
                    .distinctBy { it.title.trim().lowercase() }
                val totalAlbumsCount = studioAlbums.size
                val hasAnyStats = cleanFollowers != null || cleanListeners != null ||
                        artist.topSongs.isNotEmpty() || totalAlbumsCount > 0 ||
                        cleanLanguage != null

                if (hasAnyStats) {
                    item {
                        ArtistStatsMetricRow(
                            followerCount = cleanFollowers,
                            monthlyListeners = cleanListeners,
                            topSongsCount = artist.topSongs.size,
                            albumsCount = totalAlbumsCount,
                            dominantLanguage = cleanLanguage
                        )
                    }
                }

                // 4. SEGMENTED TABS: MUSIC, ABOUT, RELATED
                item {
                    ArtistSegmentedTabs(
                        selectedTab = selectedTab,
                        availableTabs = availableTabs,
                        onTabSelected = { selectedTab = it }
                    )
                }

                // 5. TAB CONTENT RENDERING
                when (selectedTab) {
                    ArtistProfileTab.MUSIC -> {
                        // Popular Tracks Header
                        if (artist.topSongs.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "POPULAR TRACKS",
                                            color = PaletteCream,
                                            fontFamily = SoraFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "Top chart releases & high fidelity streams",
                                            color = TextSecondary,
                                            fontFamily = UrbanistFontFamily,
                                            fontSize = 11.5.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(PaletteOxfordBlue)
                                            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${artist.topSongs.size} SONGS",
                                            color = PaletteSand,
                                            fontFamily = SoraFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Popular Tracks List (Top 10 - deduplicated, no duplicate remixes)
                            itemsIndexed(artist.topSongs) { index, track ->
                                val isTrackPlaying = isPlaying && track.id == currentTrackId
                                val isTrackLiked = favoriteIds.contains(track.id)
                                ArtistTrackRow(
                                    rank = index + 1,
                                    track = track,
                                    isPlaying = isTrackPlaying,
                                    isFavorite = isTrackLiked,
                                    onPlay = { onPlayTrack(track, artist.topSongs) },
                                    onFavorite = { onToggleFavorite(track) },
                                    onMoreClick = { selectedSongActionsTrack = track }
                                )
                            }
                        }

                        // Latest Release Spotlight (Paced by the most recent release year)
                        if (latest != null) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                ArtistLatestReleaseCard(
                                    album = latest,
                                    artistName = artist.name,
                                    onOpenAlbum = { viewingAlbum = latest }
                                )
                            }
                        }

                                                // 1. ARTIST SOLO ALBUM (Solo studio albums created by the artist)
                        val originalList = artist.originalAlbums.filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
                        if (originalList.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "ARTIST SOLO ALBUM",
                                    color = PaletteCream,
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    items(originalList) { album ->
                                        ArtistAlbumMiniCard(
                                            album = album,
                                            artistName = artist.name,
                                            onOpenAlbum = { viewingAlbum = album }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. ALBUMS ARTIST HAS BEEN IN (Movie Albums & Soundtracks the artist has been in)
                        val featuredList = artist.featuredAlbums.filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
                        if (featuredList.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "ALBUMS ARTIST HAS BEEN IN",
                                    color = PaletteCream,
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    items(featuredList) { album ->
                                        ArtistAlbumMiniCard(
                                            album = album,
                                            artistName = artist.name,
                                            onOpenAlbum = { viewingAlbum = album }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. SINGLES AND EPS (Singles and EPs)
                        val singlesList = artist.singles.filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
                        if (singlesList.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "SINGLES AND EPS",
                                    color = PaletteCream,
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    items(singlesList) { singleAlbum ->
                                        ArtistAlbumMiniCard(
                                            album = singleAlbum,
                                            artistName = artist.name,
                                            onOpenAlbum = { viewingAlbum = singleAlbum }
                                        )
                                    }
                                }
                            }
                        }

                        // Related Artists / Fans Also Like Carousel (Tapping scrolls to top)
                        if (artist.similarArtists.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "FANS ALSO LIKE",
                                    color = PaletteCream,
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    items(artist.similarArtists) { similar ->
                                        ArtistSimilarItem(
                                            artist = similar,
                                            onClick = { onOpenArtist(similar) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ArtistProfileTab.ABOUT -> {
                        // Rich Detailed Artist About Section
                        item {
                            ArtistDetailedAboutSection(
                                artist = artist,
                                onOpenArtist = onOpenArtist,
                                onOpenWiki = {
                                    if (!artist.wikiUrl.isNullOrBlank()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(artist.wikiUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                    }

                    ArtistProfileTab.RELATED -> {
                        // Dedicated Related Artists & Influences View
                        if (artist.similarArtists.isNotEmpty()) {
                            item {
                                ArtistRelatedTabContent(
                                    similarArtists = artist.similarArtists,
                                    onOpenArtist = onOpenArtist
                                )
                            }
                        }
                    }
                }
            }

            // 6. FLOATING DYNAMIC STICKY TOP APP BAR
            // 6. FLOATING DYNAMIC STICKY TOP APP BAR (Dropdown directly anchored on the right side)
            ArtistCollapsingTopBar(
                artistName = artist.name,
                isVerified = artist.isVerified,
                headerAlpha = headerAlpha,
                isCollapsed = isCollapsed,
                hasTopTracks = artist.topSongs.isNotEmpty(),
                showMoreDropdown = showMoreDropdown,
                wikiUrl = artist.wikiUrl,
                onBack = onBack,
                onPlay = {
                    if (artist.topSongs.isNotEmpty()) {
                        onPlayTrack(artist.topSongs.first(), artist.topSongs)
                    }
                },
                onMoreClick = { showMoreDropdown = true },
                onDismissDropdown = { showMoreDropdown = false },
                onShareArtist = { showShareSheet = true },
                onRadioArtist = {
                    val allArtistSongs = (artist.topSongs + artist.originalAlbums.flatMap { it.tracks } + artist.featuredAlbums.flatMap { it.tracks } + artist.pastAlbums.flatMap { it.tracks } + artist.singles.flatMap { it.tracks })
                        .filter { it.artist.contains(artist.name, ignoreCase = true) || artist.name.contains(it.artist, ignoreCase = true) }
                        .distinctBy { it.id }
                        .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                    val radioQueue = if (allArtistSongs.isNotEmpty()) allArtistSongs.shuffled() else artist.topSongs.shuffled()
                    if (radioQueue.isNotEmpty()) {
                        if (onPlayRadio != null) {
                            onPlayRadio(radioQueue.first(), radioQueue, artist.name)
                        } else {
                            onPlayTrack(radioQueue.first(), radioQueue)
                        }
                    }
                },
                onOpenWiki = {
                    if (!artist.wikiUrl.isNullOrBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(artist.wikiUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
            )
        }
    }

    // 7. INTERACTIVE ARTIST SHARE SHEET (Card preview, visual PNG card share, rich links, clipboard)
    if (showShareSheet) {
        ArtistShareBottomSheet(
            artist = artist,
            onDismiss = { showShareSheet = false },
            onShareImageCard = {
                if (!isGeneratingCard) {
                    isGeneratingCard = true
                    coroutineScope.launch {
                        shareArtistCardImage(context, artist) {
                            isGeneratingCard = false
                            showShareSheet = false
                        }
                    }
                }
            },
            onShareText = {
                shareArtistText(context, artist)
                showShareSheet = false
            },
            onCopyLink = {
                copyArtistLinkToClipboard(context, artist)
            },
            isGeneratingCard = isGeneratingCard
        )
    }

    selectedSongActionsTrack?.let { tr ->
        SongActionsSheet(
            track = tr,
            onDismiss = { selectedSongActionsTrack = null },
            onNavigateToArtist = { aName ->
                selectedSongActionsTrack = null
                if (!aName.equals(artist.name, ignoreCase = true)) {
                    onOpenArtist(SieloArtist(id = aName, name = aName))
                }
            },
            onNavigateToAlbum = { albTitle, _ ->
                selectedSongActionsTrack = null
                val match = (artist.originalAlbums + artist.featuredAlbums + artist.pastAlbums + artist.singles)
                    .firstOrNull { it.title.equals(albTitle, ignoreCase = true) }
                if (match != null) {
                    viewingAlbum = match
                }
            }
        )
    }
}

/**
 * Dedicated Album Detail View opened whenever an album or latest release is tapped.
 * Displays album artwork, metadata, and the list of tracks in the album.
 */
@Composable
private fun ArtistAlbumDetailView(
    album: SieloAlbum,
    artistName: String,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (SieloTrack, List<SieloTrack>) -> Unit,
    onPlayAlbum: ((SieloTrack, List<SieloTrack>) -> Unit)? = null,
    onToggleFavorite: (SieloTrack) -> Unit,
    onLoadAlbumTracks: suspend (SieloAlbum) -> List<SieloTrack> = { it.tracks },
    modifier: Modifier = Modifier
) {
    var tracks by remember(album.id) { mutableStateOf(album.tracks) }
    var isLoadingTracks by remember(album.id) { mutableStateOf(album.tracks.isEmpty()) }
    var selectedAlbumSongActionsTrack by remember { mutableStateOf<SieloTrack?>(null) }
    val songActionsVm: SongActionsViewModel = hiltViewModel()
    val favoriteEntities by songActionsVm.favorites.collectAsState(initial = emptyList())
    val favoriteIds = remember(favoriteEntities) { favoriteEntities.map { it.id }.toSet() }

    LaunchedEffect(album.id) {
        if (tracks.isEmpty()) {
            isLoadingTracks = true
            val fetched = onLoadAlbumTracks(album)
            tracks = fetched
            isLoadingTracks = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 210.dp)
        ) {
            // 1. Universal Sielo Album Hero
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val effectiveAlbum = remember(album, artistName, tracks) {
                    val base = if (album.artist.isNotBlank()) album else album.copy(artist = artistName)
                    base.copy(tracks = tracks, songCount = if (tracks.isNotEmpty()) tracks.size else base.songCount)
                }

                AlbumHero(
                    album = effectiveAlbum,
                    onBack = onBack,
                    onPlay = {
                        val currentTracks = tracks
                        if (currentTracks.isNotEmpty()) {
                            if (onPlayAlbum != null) {
                                onPlayAlbum(currentTracks.first(), currentTracks)
                            } else {
                                onPlayTrack(currentTracks.first(), currentTracks)
                            }
                        } else {
                            coroutineScope.launch {
                                val fetched = onLoadAlbumTracks(album)
                                if (fetched.isNotEmpty()) {
                                    tracks = fetched
                                    if (onPlayAlbum != null) {
                                        onPlayAlbum(fetched.first(), fetched)
                                    } else {
                                        onPlayTrack(fetched.first(), fetched)
                                    }
                                }
                            }
                        }
                    },
                    onShuffle = {
                        val currentTracks = tracks
                        if (currentTracks.isNotEmpty()) {
                            val shuffled = currentTracks.shuffled()
                            if (onPlayAlbum != null) {
                                onPlayAlbum(shuffled.first(), shuffled)
                            } else {
                                onPlayTrack(shuffled.first(), shuffled)
                            }
                        } else {
                            coroutineScope.launch {
                                val fetched = onLoadAlbumTracks(album)
                                if (fetched.isNotEmpty()) {
                                    tracks = fetched
                                    val shuffled = fetched.shuffled()
                                    if (onPlayAlbum != null) {
                                        onPlayAlbum(shuffled.first(), shuffled)
                                    } else {
                                        onPlayTrack(shuffled.first(), shuffled)
                                    }
                                }
                            }
                        }
                    },
                    onFavorite = null,
                    onSaveAsPlaylist = {
                        if (tracks.isNotEmpty()) {
                            songActionsVm.saveAlbumAsPlaylist(effectiveAlbum, tracks)
                        } else {
                            android.widget.Toast.makeText(context, "Loading album tracks before saving...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMore = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, effectiveAlbum.title)
                            putExtra(android.content.Intent.EXTRA_TEXT, "Listen to \"${effectiveAlbum.title}\" by ${effectiveAlbum.artist} on Sielo")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${effectiveAlbum.title}").apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "TRACKS",
                    color = PaletteSageGreen,
                    fontFamily = SoraFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // List of songs in the album
            if (tracks.isNotEmpty()) {
                itemsIndexed(tracks) { idx, track ->
                    val isTrackPlaying = isPlaying && track.id == currentTrackId
                    ArtistTrackRow(
                        rank = idx + 1,
                        track = track,
                        isPlaying = isTrackPlaying,
                        isFavorite = favoriteIds.contains(track.id),
                        onPlay = {
                            if (onPlayAlbum != null) {
                                onPlayAlbum(track, tracks)
                            } else {
                                onPlayTrack(track, tracks)
                            }
                        },
                        onFavorite = { onToggleFavorite(track) },
                        onMoreClick = { selectedAlbumSongActionsTrack = track }
                    )
                }
            } else if (isLoadingTracks) {
                items(5) {
                    ArtistTrackRowShimmerItem()
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tracks available for this release",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    selectedAlbumSongActionsTrack?.let { tr ->
        SongActionsSheet(
            track = tr,
            onDismiss = { selectedAlbumSongActionsTrack = null },
            onNavigateToArtist = { aName ->
                selectedAlbumSongActionsTrack = null
                onBack()
            }
        )
    }
}

@Composable
private fun ArtistTrackRowShimmerItem() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PaletteOxfordBlue.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PaletteOxfordBlue.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PaletteOxfordBlue.copy(alpha = alpha))
            )
        }
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PaletteOxfordBlue.copy(alpha = alpha))
        )
    }
}

/**
 * Collapsing Sticky Top Bar with smooth background transition & quick actions.
 */
@Composable
private fun ArtistCollapsingTopBar(
    artistName: String,
    isVerified: Boolean,
    headerAlpha: Float,
    isCollapsed: Boolean,
    hasTopTracks: Boolean,
    showMoreDropdown: Boolean,
    wikiUrl: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
    onDismissDropdown: () -> Unit,
    onShareArtist: () -> Unit,
    onRadioArtist: () -> Unit,
    onOpenWiki: () -> Unit
) {
    val barColor = PaletteDarkNavy.copy(alpha = (headerAlpha * 0.96f).coerceIn(0f, 0.96f))
    val borderColor = BorderGlass.copy(alpha = headerAlpha)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(barColor)
            .border(width = if (isCollapsed) 1.dp else 0.dp, color = borderColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Circular Glass Back Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                    .border(1.dp, BorderGlass, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PaletteCream,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Collapsing Artist Name & Verified Badge
            AnimatedVisibility(
                visible = isCollapsed,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = artistName,
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = PaletteSageGreen,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            if (!isCollapsed) {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Right Quick Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mini Play button when scrolled past hero
                if (isCollapsed && hasTopTracks) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PaletteSand)
                            .clickable { onPlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = PaletteDarkNavy,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Share Action Button on the right top
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable { onShareArtist() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Artist",
                        tint = PaletteCream,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Hero Header with parallax background scrim, circular portrait, and real metadata.
 */
@Composable
private fun ArtistHeroHeader(artist: ArtistDetails) {
    val context = LocalContext.current
    val heroImg = artist.heroImageUrl ?: artist.imageUrl

    val cleanFollowers = artist.followerCount?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("0", ignoreCase = true) }
    val cleanListeners = artist.monthlyListeners?.takeIf { !it.equals("null", ignoreCase = true) }
    val cleanLanguage = artist.dominantLanguage?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true) }
    val cleanType = artist.dominantType?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("artist", ignoreCase = true) && !it.equals("undefined", ignoreCase = true) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
    ) {
        // High-Resolution Atmospheric Backdrop
        if (!heroImg.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(heroImg)
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Deep Gradient Fade into PaletteDarkNavy canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PaletteDarkNavy.copy(alpha = 0.45f),
                            PaletteDarkNavy.copy(alpha = 0.75f),
                            PaletteDarkNavy
                        )
                    )
                )
        )

        // Hero Identity Elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 58.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Circular Portrait Avatar with Ambient Accent Ring
            Box(
                modifier = Modifier
                    .size(126.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(PaletteSlateBlue.copy(alpha = 0.6f), PaletteDarkNavy)
                        )
                    )
                    .border(2.dp, PaletteSand.copy(alpha = 0.85f), CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                SieloArtistPhoto(
                    imageUrl = artist.imageUrl,
                    name = artist.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Artist Name
            Text(
                text = artist.name.ifBlank { "Artist" },
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata Row: Verified + Real Followers / Language (never outputs "null")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (artist.isVerified) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified Artist",
                        tint = PaletteSageGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Verified",
                        color = PaletteSageGreen,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val metricText = when {
                    cleanFollowers != null -> "• $cleanFollowers Followers"
                    cleanListeners != null -> "• $cleanListeners Listeners"
                    cleanLanguage != null -> "• $cleanLanguage"
                    else -> null
                }

                if (metricText != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = metricText,
                        color = PaletteSand,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (cleanType != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• $cleanType",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Primary Action Row:
 * Row 1: [ ▶ PLAY ] and [ 🔀 SHUFFLE ] - 50/50 full-width prominent music actions (never truncated).
 * Row 2: [ ❤ FOLLOW ] and [ ↗ SHARE ] - secondary social engagement actions.
 */
@Composable
private fun ArtistPrimaryActionRow(
    artist: ArtistDetails,
    isFollowing: Boolean,
    onPlayAll: () -> Unit,
    onRadio: () -> Unit,
    onToggleFollow: () -> Unit,
    onShare: () -> Unit
) {
    val followBg by animateColorAsState(
        targetValue = if (isFollowing) PaletteOxfordBlue else PaletteOxfordBlue.copy(alpha = 0.6f),
        label = "followBg"
    )
    val followBorder by animateColorAsState(
        targetValue = if (isFollowing) BorderHighlight else BorderGlass,
        label = "followBorder"
    )
    val followText = if (isFollowing) "FOLLOWING" else "FOLLOW"
    val followHeartTint = if (isFollowing) PaletteSand else PaletteSlateBlue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ROW 1: PLAY ALL & RADIO (50/50 split, comfortable breathing room, no truncation)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PLAY ALL BUTTON (Premium Cream container with Dark Navy text/icon)
            Button(
                onClick = onPlayAll,
                enabled = artist.topSongs.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaletteCream,
                    contentColor = PaletteDarkNavy,
                    disabledContainerColor = PaletteSlateBlue.copy(alpha = 0.3f),
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = PaletteDarkNavy,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PLAY",
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // SHUFFLE BUTTON (Dark translucent surface with AnimatedShuffleIcon)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaletteOxfordBlue)
                    .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                    .clickable(enabled = artist.topSongs.isNotEmpty()) { onRadio() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AnimatedShuffleIcon(
                        tint = if (artist.topSongs.isNotEmpty()) PaletteCream else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHUFFLE",
                        color = if (artist.topSongs.isNotEmpty()) PaletteCream else TextMuted,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // ROW 2: FOLLOW TOGGLE & SHARE BUTTON (symmetrical, sleek social action row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FOLLOW TOGGLE BUTTON
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(followBg)
                    .border(1.dp, followBorder, RoundedCornerShape(22.dp))
                    .clickable { onToggleFollow() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = followText,
                        tint = followHeartTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = followText,
                        color = if (isFollowing) PaletteSand else PaletteCream,
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // SHARE ARTIST BUTTON
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(PaletteOxfordBlue.copy(alpha = 0.65f))
                    .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
                    .clickable { onShare() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = PaletteCream,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SHARE",
                        color = PaletteCream,
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Stats Row: Displays real follower counts, top track metrics, studio albums.
 * Discography overall is removed.
 */
@Composable
private fun ArtistStatsMetricRow(
    followerCount: String?,
    monthlyListeners: String?,
    topSongsCount: Int,
    albumsCount: Int,
    dominantLanguage: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaletteOxfordBlue.copy(alpha = 0.65f))
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!followerCount.isNullOrBlank()) {
                ArtistMetricItem(value = followerCount, label = "FOLLOWERS")
            } else if (!monthlyListeners.isNullOrBlank()) {
                ArtistMetricItem(value = monthlyListeners, label = "LISTENERS")
            }

            if (topSongsCount > 0) {
                ArtistMetricItem(value = "$topSongsCount+", label = "POPULAR TRACKS")
            }

            if (albumsCount > 0) {
                ArtistMetricItem(value = "$albumsCount", label = "STUDIO ALBUMS")
            } else if (!dominantLanguage.isNullOrBlank()) {
                ArtistMetricItem(value = dominantLanguage, label = "LANGUAGE")
            }
        }
    }
}

@Composable
private fun ArtistMetricItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = PaletteCream,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = PaletteSageGreen,
            fontFamily = UrbanistFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Segmented Tab Indicator for Switching Views: MUSIC, ABOUT, and RELATED.
 */
@Composable
private fun ArtistSegmentedTabs(
    selectedTab: ArtistProfileTab,
    availableTabs: List<ArtistProfileTab>,
    onTabSelected: (ArtistProfileTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        availableTabs.forEach { tab ->
            val isSelected = tab == selectedTab
            val bg = if (isSelected) PaletteSand else Color.Transparent
            val textColor = if (isSelected) PaletteDarkNavy else PaletteCream.copy(alpha = 0.7f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    color = textColor,
                    fontFamily = SoraFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Ranked Popular Track Row (01 to 10) with High-Res Art, Duration, Active Playing Waves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistTrackRow(
    rank: Int,
    track: SieloTrack,
    isPlaying: Boolean,
    isFavorite: Boolean = false,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    val rankColor = if (rank <= 3) PaletteSand else PaletteSlateBlue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPlaying) SurfaceElevated else PaletteOxfordBlue)
            .border(1.dp, if (isPlaying) BorderHighlight else BorderGlass, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onPlay,
                onLongClick = { onMoreClick?.invoke() }
            )
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
                // Rank Number (Two-digit formatted: 01, 02...)
                Text(
                    text = String.format("%02d", rank),
                    color = rankColor,
                    fontFamily = SoraFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp)
                )

                // Track Artwork
                SieloSongArtwork(
                    thumbnailUrl = track.thumbnailUrl,
                    title = track.title,
                    artist = track.artist,
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = if (isPlaying) PaletteSand else PaletteCream,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${track.album ?: "Single"} • ${track.formattedDuration}",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right side indicators: Equalizer bars if active + Favorite toggle + More options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isPlaying) {
                    AnimatedEqualizerBars(
                        isPlaying = true,
                        barColor = PaletteSand,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                val isLiked = isFavorite
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Remove from Favorites" else "Add to Favorites",
                        tint = if (isLiked) PaletteSand else PaletteSlateBlue.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onMoreClick != null) {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = PaletteSand.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Spotlight Card for the Artist's Latest Release.
 * Tapping on the card opens the album and lists all its songs.
 */
@Composable
private fun ArtistLatestReleaseCard(
    album: SieloAlbum,
    artistName: String,
    onOpenAlbum: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "LATEST RELEASE",
            color = PaletteSageGreen,
            fontFamily = SoraFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .clickable { onOpenAlbum() }
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SieloSongArtwork(
                    thumbnailUrl = album.thumbnailUrl,
                    title = album.title,
                    artist = artistName,
                    modifier = Modifier
                        .size(76.dp)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val trackCount = if (album.tracks.isNotEmpty()) album.tracks.size else album.songCount
                    val typeStr = album.type?.takeIf { it.isNotBlank() } ?: "Album"
                    val yearType = when {
                        !album.year.isNullOrBlank() -> "${album.year} • $typeStr"
                        else -> typeStr
                    }
                    Text(
                        text = yearType,
                        color = PaletteSand,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val trackInfo = when {
                        trackCount > 0 -> "$artistName • $trackCount tracks"
                        else -> artistName
                    }
                    Text(
                        text = trackInfo,
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Open Album Chevron Indicator
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PaletteSand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Open Album",
                        tint = PaletteDarkNavy,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact Album Card for the Horizontal Carousel in Music tab.
 * Tapping opens the album.
 */
@Composable
private fun ArtistAlbumMiniCard(
    album: SieloAlbum,
    artistName: String,
    onOpenAlbum: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(134.dp)
            .clickable { onOpenAlbum() }
    ) {
        Box(
            modifier = Modifier
                .size(134.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
        ) {
            SieloSongArtwork(
                thumbnailUrl = album.thumbnailUrl,
                title = album.title,
                artist = artistName,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp)
            )

            // Open / View Overlay Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PaletteDarkNavy.copy(alpha = 0.85f))
                    .border(1.dp, PaletteSand.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = PaletteSand,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            color = PaletteCream,
            fontFamily = UrbanistFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val trackCount = if (album.tracks.isNotEmpty()) album.tracks.size else album.songCount
        val typeStr = album.type?.takeIf { it.isNotBlank() } ?: "Album"
        val metaSubtitle = when {
            !album.year.isNullOrBlank() && trackCount > 0 -> "${album.year} • $trackCount songs"
            !album.year.isNullOrBlank() -> "${album.year} • $typeStr"
            trackCount > 0 -> "$trackCount songs • $typeStr"
            else -> typeStr
        }
        Text(
            text = metaSubtitle,
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Similar Artist Circular Portrait Item for "Fans Also Like" Row.
 */
@Composable
private fun ArtistSimilarItem(
    artist: SieloArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(PaletteOxfordBlue)
                .border(1.5.dp, BorderGlass, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            SieloArtistPhoto(
                imageUrl = artist.imageUrl,
                name = artist.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            color = PaletteCream,
            fontFamily = UrbanistFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = artist.role ?: "Artist",
            color = PaletteSageGreen,
            fontFamily = UrbanistFontFamily,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Comprehensive About & Detailed Information Section.
 * Contains detailed biography, catalog insights, audio specifications, and related artists.
 */
@Composable
private fun ArtistDetailedAboutSection(
    artist: ArtistDetails,
    onOpenArtist: (SieloArtist) -> Unit,
    onOpenWiki: () -> Unit
) {
    var isBioExpanded by remember { mutableStateOf(false) }

    val bio = artist.bio?.takeIf { !it.equals("null", ignoreCase = true) }
    val dominantLanguage = artist.dominantLanguage?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true) }
    val dominantType = artist.dominantType?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("artist", ignoreCase = true) && !it.equals("undefined", ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Biography Card
        if (!bio.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PaletteOxfordBlue)
                    .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        text = "BIOGRAPHY",
                        color = PaletteSageGreen,
                        fontFamily = SoraFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = bio,
                        color = PaletteCream,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (bio.length > 200) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBioExpanded) "Read less" else "Read more",
                            color = PaletteSand,
                            fontFamily = UrbanistFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { isBioExpanded = !isBioExpanded }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Artist Metadata Details Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "ARTIST INFORMATION",
                    color = PaletteSageGreen,
                    fontFamily = SoraFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (!dominantLanguage.isNullOrBlank()) {
                    ArtistDetailItem(
                        label = "Dominant Language",
                        value = dominantLanguage
                    )
                }

                if (!dominantType.isNullOrBlank()) {
                    ArtistDetailItem(
                        label = "Role & Artistry",
                        value = dominantType
                    )
                }

                if (artist.topSongs.isNotEmpty()) {
                    ArtistDetailItem(
                        label = "Popular Hit",
                        value = artist.topSongs.first().title
                    )
                }

                if (artist.pastAlbums.isNotEmpty()) {
                    ArtistDetailItem(
                        label = "Catalog Releases",
                        value = "${(artist.originalAlbums + artist.pastAlbums)
                            .filter { it.type == "Album" && !TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
                            .distinctBy { it.title.trim().lowercase() }
                            .size} Studio Albums"
                    )
                }

                if (artist.isVerified) {
                    ArtistDetailItem(
                        label = "Verification",
                        value = "Official Sielo Verified Artist"
                    )
                }
            }
        }

        // Wikipedia Button
        if (!artist.wikiUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenWiki,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaletteOxfordBlue,
                    contentColor = PaletteSand
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, BorderHighlight, RoundedCornerShape(14.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = PaletteSand,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXPLORE WIKIPEDIA PROFILE",
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Similar Artists Grid
        if (artist.similarArtists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "RELATED ARTISTS",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(artist.similarArtists) { similar ->
                    ArtistSimilarItem(
                        artist = similar,
                        onClick = { onOpenArtist(similar) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = PaletteCream,
            fontFamily = UrbanistFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

/**
 * Atmospheric Dark Skeleton Shimmer State for loading transition.
 */
@Composable
private fun ArtistProfileSkeleton(onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 140.dp)
    ) {
        // Top Back Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                    .border(1.dp, BorderGlass, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PaletteCream,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Hero Portrait Shimmer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(126.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
                    .border(2.dp, BorderGlass, CircleShape)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Title Shimmer Bar
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle Shimmer Bar
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha * 0.7f))
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Action Buttons Shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Tracks Shimmer Rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha * 0.8f))
                        .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                )
            }
        }
    }
}

/**
 * Interactive Artist Share Bottom Sheet:
 * - Live Visual Card Preview
 * - Share Visual Music Card (PNG)
 * - Share App Invite & Tracks (rich text with sielo:// and web links)
 * - Copy Sielo Deep Link
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistShareBottomSheet(
    artist: ArtistDetails,
    onDismiss: () -> Unit,
    onShareImageCard: () -> Unit,
    onShareText: () -> Unit,
    onCopyLink: () -> Unit,
    isGeneratingCard: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteDarkNavy,
        contentColor = PaletteCream,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(PaletteSand.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Share Artist",
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PaletteCream
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Mini Card Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(PaletteOxfordBlue, PaletteDarkNavy)
                        )
                    )
                    .border(1.dp, BorderHighlight, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Small Avatar
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, PaletteSand, CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SieloArtistPhoto(
                            imageUrl = artist.imageUrl,
                            name = artist.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = artist.name,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PaletteCream,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val metaString = listOfNotNull(
                        if (artist.isVerified) "Verified" else null,
                        artist.followerCount?.takeIf { !it.equals("null", true) && !it.equals("0", true) }?.let { "$it Followers" },
                        artist.dominantType?.takeIf { !it.equals("null", true) && !it.equals("artist", true) }
                    ).joinToString(" • ")

                    if (metaString.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = metaString,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 12.sp,
                            color = PaletteSand,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (artist.topSongs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PaletteDarkNavy.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                artist.topSongs.take(3).forEachIndexed { index, track ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = String.format("%02d", index + 1),
                                            color = PaletteSand,
                                            fontFamily = SoraFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.width(22.dp)
                                        )
                                        Text(
                                            text = track.title,
                                            color = PaletteCream,
                                            fontFamily = UrbanistFontFamily,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (track.formattedDuration.isNotBlank()) {
                                            Text(
                                                text = track.formattedDuration,
                                                color = TextMuted,
                                                fontFamily = UrbanistFontFamily,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "SIELO MUSIC • STREAM IN PURE SOUND",
                        fontFamily = SoraFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaletteSand.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action 1: Share Story Card Image
            Button(
                onClick = onShareImageCard,
                enabled = !isGeneratingCard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaletteSand,
                    contentColor = PaletteDarkNavy
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = PaletteDarkNavy,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGeneratingCard) "GENERATING CARD..." else "SHARE VISUAL MUSIC CARD",
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action 2: Share App Link & Summary
            Button(
                onClick = onShareText,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaletteOxfordBlue,
                    contentColor = PaletteCream
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = PaletteCream,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHARE INVITE & TRACKS",
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action 3: Copy Sielo Link
            Button(
                onClick = onCopyLink,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaletteOxfordBlue.copy(alpha = 0.6f),
                    contentColor = PaletteSand
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = PaletteSand,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COPY SIELO LINK (sielo://artist)",
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Background Card Image Generator:
 * Generates an ultra-crisp 1080x1440 branded artist card image with ambient glow,
 * typography, top hits, and Sielo branding for sharing to WhatsApp / Instagram / Messages.
 */
private object ArtistShareCardGenerator {
    fun generateCardBitmap(
        artist: ArtistDetails,
        avatarBitmap: Bitmap? = null
    ): Bitmap {
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background dark navy gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    AndroidColor.rgb(11, 20, 38),   // Dark Navy #0B1426
                    AndroidColor.rgb(18, 30, 49),   // Oxford Blue #121E31
                    AndroidColor.rgb(7, 13, 26)     // Obsidian Navy
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle accent border
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = AndroidColor.argb(80, 230, 205, 160)
            isAntiAlias = true
        }
        canvas.drawRoundRect(28f, 28f, width - 28f, height - 28f, 44f, 44f, borderPaint)

        // Ambient Glow Behind Avatar
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                width / 2f, 360f, 260f,
                AndroidColor.argb(85, 201, 169, 110),
                AndroidColor.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width / 2f, 360f, 260f, glowPaint)

        // Avatar
        val avatarCenter = width / 2f
        val avatarY = 360f
        val avatarRadius = 170f
        if (avatarBitmap != null) {
            val scaledAvatar = Bitmap.createScaledBitmap(avatarBitmap, (avatarRadius * 2).toInt(), (avatarRadius * 2).toInt(), true)
            val circularAvatar = getCircularBitmap(scaledAvatar)
            canvas.drawBitmap(circularAvatar, avatarCenter - avatarRadius, avatarY - avatarRadius, null)
        } else {
            val circlePaint = Paint().apply {
                color = AndroidColor.rgb(24, 38, 64)
                isAntiAlias = true
            }
            canvas.drawCircle(avatarCenter, avatarY, avatarRadius, circlePaint)
            val initialPaint = Paint().apply {
                color = AndroidColor.rgb(230, 205, 160)
                textSize = 130f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val initial = artist.name.firstOrNull()?.uppercase() ?: "S"
            canvas.drawText(initial, avatarCenter, avatarY + 45f, initialPaint)
        }

        // Gold Ring around Avatar
        val ringPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = AndroidColor.rgb(230, 205, 160)
            isAntiAlias = true
        }
        canvas.drawCircle(avatarCenter, avatarY, avatarRadius, ringPaint)

        // Artist Name
        val namePaint = Paint().apply {
            color = AndroidColor.rgb(245, 243, 238)
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val maxTextWidth = width - 160f
        var nameText = artist.name
        if (namePaint.measureText(nameText) > maxTextWidth) {
            while (namePaint.measureText("$nameText...") > maxTextWidth && nameText.length > 3) {
                nameText = nameText.dropLast(1)
            }
            nameText = "$nameText..."
        }
        canvas.drawText(nameText, avatarCenter, 610f, namePaint)

        // Subtitle / Followers
        val subPaint = Paint().apply {
            color = AndroidColor.rgb(201, 169, 110)
            textSize = 32f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val subParts = mutableListOf<String>()
        if (artist.isVerified) subParts.add("✓ Verified")
        artist.followerCount?.takeIf { !it.equals("null", true) && !it.equals("0", true) }?.let {
            subParts.add("$it Followers")
        }
        artist.dominantType?.takeIf { !it.equals("null", true) && !it.equals("artist", true) }?.let {
            subParts.add(it)
        }
        val subText = if (subParts.isNotEmpty()) subParts.joinToString("  •  ") else "Sielo Artist Profile"
        canvas.drawText(subText, avatarCenter, 670f, subPaint)

        // Divider
        val divPaint = Paint().apply {
            color = AndroidColor.argb(35, 255, 255, 255)
            strokeWidth = 2f
        }
        canvas.drawLine(120f, 720f, width - 120f, 720f, divPaint)

        // Top Tracks Header
        val tracksHeaderPaint = Paint().apply {
            color = AndroidColor.rgb(170, 185, 210)
            textSize = 28f
            letterSpacing = 0.15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP TRACKS ON SIELO", 120f, 780f, tracksHeaderPaint)

        // Top Tracks List
        val topTracks = artist.topSongs.take(4)
        var trackY = 850f
        val rankPaint = Paint().apply {
            color = AndroidColor.rgb(201, 169, 110)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val songTitlePaint = Paint().apply {
            color = AndroidColor.rgb(240, 240, 240)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val durationPaint = Paint().apply {
            color = AndroidColor.rgb(130, 145, 170)
            textSize = 28f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        topTracks.forEachIndexed { index, track ->
            canvas.drawText(String.format("%02d", index + 1), 120f, trackY, rankPaint)
            var trackTitle = track.title
            val maxTrackWidth = width - 420f
            if (songTitlePaint.measureText(trackTitle) > maxTrackWidth) {
                while (songTitlePaint.measureText("$trackTitle...") > maxTrackWidth && trackTitle.length > 3) {
                    trackTitle = trackTitle.dropLast(1)
                }
                trackTitle = "$trackTitle..."
            }
            canvas.drawText(trackTitle, 190f, trackY, songTitlePaint)
            if (track.formattedDuration.isNotBlank()) {
                canvas.drawText(track.formattedDuration, width - 120f, trackY, durationPaint)
            }
            trackY += 76f
        }

        // Bottom Footer Card with Sielo Branding
        val footerBgPaint = Paint().apply {
            color = AndroidColor.argb(160, 14, 23, 40)
        }
        canvas.drawRoundRect(100f, 1240f, width - 100f, 1350f, 26f, 26f, footerBgPaint)

        val footerLogoPaint = Paint().apply {
            color = AndroidColor.rgb(230, 205, 160)
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
            isAntiAlias = true
        }
        canvas.drawText("SIELO MUSIC", 140f, 1305f, footerLogoPaint)

        val footerTaglinePaint = Paint().apply {
            color = AndroidColor.rgb(160, 175, 195)
            textSize = 26f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("Stream without compromises", width - 140f, 1305f, footerTaglinePaint)

        return bitmap
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isAntiAlias = true }
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, bitmap.width / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }
}

private suspend fun shareArtistCardImage(
    context: Context,
    artist: ArtistDetails,
    onComplete: () -> Unit
) = withContext(Dispatchers.IO) {
    val shareText = buildShareText(artist)
    try {
        val sharedDir = java.io.File(context.cacheDir, "shared_cards").apply { if (!exists()) mkdirs() }
        val cardFile = java.io.File(sharedDir, "sielo_artist_${artist.id.hashCode()}.png")

        val loader = Coil.imageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(artist.imageUrl)
            .allowHardware(false)
            .build()
        val drawable = loader.execute(request).drawable
        val avatarBitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap

        val cardBitmap = ArtistShareCardGenerator.generateCardBitmap(artist, avatarBitmap)
        java.io.FileOutputStream(cardFile).use { out ->
            cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cardFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Check out ${artist.name} on Sielo")
            putExtra(Intent.EXTRA_TEXT, shareText)
            clipData = ClipData.newRawUri("Sielo Artist Card", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        withContext(Dispatchers.Main) {
            val chooser = Intent.createChooser(shareIntent, "Share ${artist.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    } catch (_: Exception) {
        withContext(Dispatchers.Main) {
            shareArtistText(context, artist)
        }
    } finally {
        withContext(Dispatchers.Main) {
            onComplete()
        }
    }
}

private fun shareArtistText(context: Context, artist: ArtistDetails) {
    val shareText = buildShareText(artist)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, artist.name)
        putExtra(Intent.EXTRA_TEXT, shareText)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share ${artist.name}").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun buildShareText(artist: ArtistDetails): String {
    val sb = StringBuilder()
    sb.append("🎵 Listen to ${artist.name} on Sielo Music!\n\n")
    if (artist.isVerified) {
        sb.append("✨ Sielo Verified Artist\n")
    }
    artist.followerCount?.takeIf { !it.equals("null", true) && !it.equals("0", true) }?.let {
        sb.append("👥 $it Followers\n")
    }
    if (artist.topSongs.isNotEmpty()) {
        sb.append("\n🔥 Popular Hits:\n")
        artist.topSongs.take(3).forEachIndexed { i, track ->
            sb.append("${i + 1}. ${track.title}\n")
        }
    }
    val encoded = Uri.encode(artist.name)
    sb.append("\n📲 Open directly in Sielo App:\n")
    sb.append("sielo://artist?name=$encoded\n")
    if (!artist.wikiUrl.isNullOrBlank()) {
        sb.append("\n🌐 Artist Web Profile:\n")
        sb.append("${artist.wikiUrl}\n")
    }
    return sb.toString()
}

private fun extractYear(yearStr: String?): Int {
    if (yearStr.isNullOrBlank()) return 0
    val match = Regex("""\b(19\d{2}|20\d{2})\b""").find(yearStr)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: yearStr.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
}

private fun copyArtistLinkToClipboard(context: Context, artist: ArtistDetails) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Sielo Artist Link", "sielo://artist?name=${Uri.encode(artist.name)}")
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Sielo link copied to clipboard!", Toast.LENGTH_SHORT).show()
}




/**
 * Dedicated Related Artists & Influences tab content.
 */
@Composable
private fun ArtistRelatedTabContent(
    similarArtists: List<SieloArtist>,
    onOpenArtist: (SieloArtist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "RELATED ARTISTS & INFLUENCES",
            color = PaletteSageGreen,
            fontFamily = SoraFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            similarArtists.forEach { similar ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PaletteOxfordBlue)
                        .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                        .clickable { onOpenArtist(similar) }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(PaletteDarkNavy)
                                .border(1.dp, BorderGlass, CircleShape)
                        ) {
                            SieloArtistPhoto(
                                imageUrl = similar.imageUrl,
                                name = similar.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = similar.name,
                                color = PaletteCream,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = similar.role ?: "Artist",
                                color = PaletteSageGreen,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PaletteOxfordBlue.copy(alpha = 0.8f))
                                .border(1.dp, BorderGlass, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open Profile",
                                tint = PaletteSand,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
