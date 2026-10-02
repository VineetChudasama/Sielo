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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
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
 * Universal Profile Tabs: Music, About, Albums, Similar Artists.
 */
enum class ArtistProfileTab(val label: String) {
    MUSIC("Music"),
    ABOUT("About"),
    ALBUMS("Albums"),
    SIMILAR_ARTISTS("Similar Artists")
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

    // Reset scroll to the top whenever a new artist is selected
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

    var selectedTab by remember(artist.id) { mutableStateOf(ArtistProfileTab.MUSIC) }
    var isFollowingState by remember(isFollowed, artist.name) { mutableStateOf(isFollowed) }
    var showMoreDropdown by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var isGeneratingCard by remember { mutableStateOf(false) }
    var selectedSongActionsTrack by remember { mutableStateOf<SieloTrack?>(null) }
    var isPopularExpanded by remember { mutableStateOf(false) }
    val songActionsVm: SongActionsViewModel = hiltViewModel()
    val favoriteEntities by songActionsVm.favorites.collectAsState(initial = emptyList())
    val favoriteIds = remember(favoriteEntities) { favoriteEntities.map { it.id }.toSet() }
    val coroutineScope = rememberCoroutineScope()

    // Authentic Studio albums and Singles/EPs calculation
    val studioAlbums = remember(artist) {
        val list = if (artist.musicBrainzId != null) {
            artist.originalAlbums
        } else {
            (artist.originalAlbums + artist.pastAlbums)
                .filter { !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) && !it.type.equals("Single", ignoreCase = true) && !it.type.equals("EP", ignoreCase = true) && (it.songCount > 1 || it.tracks.size > 1) && it.songCount != 1 }
                .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, artist.name) }
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }
        list.distinctBy { it.musicBrainzId ?: it.id.ifBlank { it.title.trim().lowercase() } }
            .sortedByDescending { extractYear(it.year ?: it.releaseDate) }
    }

    val singlesAndEPs = remember(artist) {
        val list = if (artist.musicBrainzId != null) {
            artist.singles + artist.pastAlbums.filter { it.type.equals("EP", ignoreCase = true) }
        } else {
            (artist.singles + (artist.originalAlbums + artist.pastAlbums).filter { it.type.equals("Single", ignoreCase = true) || it.type.equals("EP", ignoreCase = true) || it.songCount == 1 || (it.tracks.size == 1 && it.songCount <= 1) })
                .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, artist.name) }
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }
        list.distinctBy { it.musicBrainzId ?: it.id.ifBlank { it.title.trim().lowercase() } }
            .sortedByDescending { extractYear(it.year ?: it.releaseDate) }
    }

    val soundtracks = remember(artist) {
        val list = if (artist.musicBrainzId != null) {
            artist.featuredAlbums
        } else {
            (artist.featuredAlbums + (artist.originalAlbums + artist.pastAlbums).filter { TrackMatchValidator.isSoundtrackRelease(it.title, it.type) })
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }
        list.distinctBy { it.musicBrainzId ?: it.id.ifBlank { it.title.trim().lowercase() } }
            .sortedByDescending { extractYear(it.year ?: it.releaseDate) }
    }

    val recentReleases = remember(artist) {
        val allReleases = (artist.originalAlbums + artist.singles + artist.pastAlbums + listOfNotNull(artist.latestAlbum))
            .distinctBy { it.id }
            .sortedByDescending { extractYear(it.year ?: it.releaseDate) }
        allReleases.take(2)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        if (isLoading) {
            ArtistProfileSkeleton(onBack = onBack)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 210.dp)
            ) {
                // 1. CINEMATIC HERO SECTION
                item {
                    ArtistHeroHeader(
                        artist = artist,
                        studioAlbumsCount = studioAlbums.size,
                        onBack = onBack,
                        onMore = { showMoreDropdown = true }
                    )
                }

                // 2. PRIMARY ACTION ROW (Play, Shuffle, Follow, Share)
                item {
                    ArtistPrimaryActionRow(
                        artist = artist,
                        isFollowing = isFollowingState,
                        onPlayAll = {
                            if (artist.topSongs.isNotEmpty()) {
                                onPlayTrack(artist.topSongs.first(), artist.topSongs)
                            }
                        },
                        onShuffle = {
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

                // 3. TAB NAVIGATION BAR (Music | About | Albums | Similar Artists)
                item {
                    ArtistTabNavigation(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                // 4. DYNAMIC TAB CONTENT RENDERING
                when (selectedTab) {
                    ArtistProfileTab.MUSIC -> {
                        // A. Popular Tracks Section
                        if (artist.topSongs.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Popular",
                                        color = PaletteCream,
                                        fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )

                                    if (artist.topSongs.size > 5) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { isPopularExpanded = !isPopularExpanded }
                                        ) {
                                            Text(
                                                text = if (isPopularExpanded) "Show less" else "See all",
                                                color = TextSecondary,
                                                fontFamily = UrbanistFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            val displayedTracks = if (isPopularExpanded) artist.topSongs else artist.topSongs.take(5)
                            itemsIndexed(displayedTracks) { index, track ->
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

                        // B. Latest Release Section
                        if (recentReleases.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(22.dp))
                                Text(
                                    text = "Latest Release",
                                    color = PaletteCream,
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    items(recentReleases) { release ->
                                        ArtistLatestReleaseCard(
                                            album = release,
                                            artistName = artist.name,
                                            onOpenAlbum = { viewingAlbum = release },
                                            onPlayRelease = {
                                                coroutineScope.launch {
                                                    val tracks = onLoadAlbumTracks(release)
                                                    if (tracks.isNotEmpty()) {
                                                        if (onPlayAlbum != null) {
                                                            onPlayAlbum(tracks.first(), tracks)
                                                        } else {
                                                            onPlayTrack(tracks.first(), tracks)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // C. Studio Albums Section (Carousel with "See all >")
                        if (studioAlbums.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(22.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Albums",
                                        color = PaletteCream,
                                        fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { selectedTab = ArtistProfileTab.ALBUMS }
                                    ) {
                                        Text(
                                            text = "See all",
                                            color = TextSecondary,
                                            fontFamily = UrbanistFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    items(studioAlbums) { album ->
                                        ArtistAlbumCard(
                                            album = album,
                                            artistName = artist.name,
                                            onOpenAlbum = { viewingAlbum = album }
                                        )
                                    }
                                }
                            }
                        }

                        // D. Singles & EPs Section (Carousel with "See all >")
                        if (singlesAndEPs.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(22.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Singles & EPs",
                                        color = PaletteCream,
                                        fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { selectedTab = ArtistProfileTab.ALBUMS }
                                    ) {
                                        Text(
                                            text = "See all",
                                            color = TextSecondary,
                                            fontFamily = UrbanistFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    items(singlesAndEPs) { album ->
                                        ArtistAlbumCard(
                                            album = album,
                                            artistName = artist.name,
                                            onOpenAlbum = { viewingAlbum = album }
                                        )
                                    }
                                }
                            }
                        }

                        // Soundtracks & Features Section (Carousel with "See all >")
                        if (soundtracks.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(22.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Soundtracks & Features",
                                        color = PaletteCream,
                                        fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { selectedTab = ArtistProfileTab.ALBUMS }
                                    ) {
                                        Text(
                                            text = "See all",
                                            color = TextSecondary,
                                            fontFamily = UrbanistFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    items(soundtracks) { album ->
                                        ArtistAlbumCard(
                                            album = album,
                                            artistName = artist.name,
                                            onOpenAlbum = { viewingAlbum = album }
                                        )
                                    }
                                }
                            }
                        }

                        // D. Similar Artists Section (Carousel with "See all >")
                        if (artist.similarArtists.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(22.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Similar Artists",
                                        color = PaletteCream,
                                        fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { selectedTab = ArtistProfileTab.SIMILAR_ARTISTS }
                                    ) {
                                        Text(
                                            text = "See all",
                                            color = TextSecondary,
                                            fontFamily = UrbanistFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

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
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            ArtistDetailedAboutSection(
                                artist = artist,
                                onOpenArtist = onOpenArtist,
                                onOpenWiki = {
                                    val targetUrl = if (!artist.wikiUrl.isNullOrBlank()) {
                                        artist.wikiUrl
                                    } else {
                                        "https://en.wikipedia.org/wiki/Special:Search?search=" + Uri.encode(artist.name)
                                    }
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }

                    ArtistProfileTab.ALBUMS -> {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            ArtistAllAlbumsTabContent(
                                artist = artist,
                                onOpenAlbum = { viewingAlbum = it }
                            )
                        }
                    }

                    ArtistProfileTab.SIMILAR_ARTISTS -> {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            ArtistSimilarArtistsTabContent(
                                similarArtists = artist.similarArtists,
                                onOpenArtist = onOpenArtist
                            )
                        }
                    }
                }
            }

            // 5. STICKY COLLAPSING TOP BAR
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

    // 6. INTERACTIVE ARTIST SHARE SHEET
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
 * Cinematic Hero Header matching reference design:
 * - Immersive background hero image with deep gradient fade
 * - Top back / more buttons
 * - Verified badge + "ARTIST"
 * - Large artist name
 * - Short biography
 * - Genre chips
 * - Horizontal stats card: Monthly Listeners | Songs | Albums | Followers
 */
@Composable
private fun ArtistHeroHeader(
    artist: ArtistDetails,
    studioAlbumsCount: Int,
    onBack: () -> Unit,
    onMore: () -> Unit
) {
    val context = LocalContext.current
    val heroImg = artist.heroImageUrl ?: artist.imageUrl ?: artist.topSongs.firstOrNull()?.thumbnailUrl ?: artist.latestAlbum?.thumbnailUrl

    val cleanFollowers = artist.followerCount?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("0", ignoreCase = true) }
    val cleanListeners = artist.monthlyListeners?.takeIf { !it.equals("null", ignoreCase = true) }
    val bio = artist.bio?.takeIf { !it.equals("null", ignoreCase = true) }

    // Dynamic genre list from metadata
    val genresList = remember(artist.genres, artist.dominantType, artist.dominantLanguage) {
        if (artist.genres.isNotEmpty()) {
            artist.genres
        } else {
            val list = mutableListOf<String>()
            val type = artist.dominantType?.trim()
            if (!type.isNullOrBlank() && !type.equals("null", ignoreCase = true) && !type.equals("artist", ignoreCase = true) && !type.equals("undefined", ignoreCase = true)) {
                type.split(",", "/", "&", "•").map { it.trim() }.filter { it.isNotBlank() }.forEach { list.add(it) }
            }
            val lang = artist.dominantLanguage?.trim()
            if (!lang.isNullOrBlank() && !lang.equals("null", ignoreCase = true) && !lang.equals("undefined", ignoreCase = true)) {
                list.add(lang)
            }
            list.distinct()
        }
    }

    var isBioExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // High-Resolution Atmospheric Backdrop Image (matches parent size seamlessly)
        if (!heroImg.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(heroImg)
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }

        // Multi-stop Vertical Gradient overlay fading seamlessly into PaletteDarkNavy
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PaletteDarkNavy.copy(alpha = 0.30f),
                            PaletteDarkNavy.copy(alpha = 0.55f),
                            PaletteDarkNavy.copy(alpha = 0.88f),
                            PaletteDarkNavy
                        )
                    )
                )
        )

        // Top Action Bar Overlaid on the Hero (Back & More)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PaletteDarkNavy.copy(alpha = 0.65f))
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

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PaletteDarkNavy.copy(alpha = 0.65f))
                    .border(1.dp, BorderGlass, CircleShape)
                    .clickable { onMore() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = PaletteCream,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Hero Foreground Identity Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 70.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)
        ) {
            // "✓ ARTIST" Badge
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (artist.isVerified) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = PaletteSageGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = "ARTIST",
                    color = PaletteSageGreen,
                    fontFamily = SoraFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Large Artist Name (The Weeknd / Arijit Singh / etc.)
            Text(
                text = artist.name.ifBlank { "Artist" },
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Short Biography Text (if available)
            if (!bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = bio,
                    color = PaletteCream.copy(alpha = 0.85f),
                    fontFamily = UrbanistFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = if (isBioExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isBioExpanded = !isBioExpanded }
                )
            }

            // Genre Chips: [ R&B ] [ Pop ] [ Alternative ] [ Electronic ]
            if (genresList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(genresList) { genre ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PaletteOxfordBlue.copy(alpha = 0.75f))
                                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = genre,
                                color = PaletteCream,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats Pill Card: [ 89.2M Monthly Listeners | 102 Songs | 8 Albums | Followed by 3 friends > ]
            val songCount = if (artist.topSongs.isNotEmpty()) artist.topSongs.size else 0
            val albumCount = if (studioAlbumsCount > 0) studioAlbumsCount else (artist.originalAlbums.size + artist.pastAlbums.size)

            val hasAnyStats = cleanListeners != null || songCount > 0 || albumCount > 0 || cleanFollowers != null
            if (hasAnyStats) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PaletteOxfordBlue.copy(alpha = 0.70f))
                        .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        if (!cleanListeners.isNullOrBlank()) {
                            ArtistStatColumn(
                                value = cleanListeners,
                                label = "Monthly Listeners"
                            )
                            StatDivider()
                        }

                        if (songCount > 0) {
                            ArtistStatColumn(
                                value = "$songCount+",
                                label = "Songs"
                            )
                            if (albumCount > 0 || cleanFollowers != null) {
                                StatDivider()
                            }
                        }

                        if (albumCount > 0) {
                            ArtistStatColumn(
                                value = "$albumCount",
                                label = "Albums"
                            )
                            if (cleanFollowers != null) {
                                StatDivider()
                            }
                        }

                        if (!cleanFollowers.isNullOrBlank()) {
                            ArtistStatColumn(
                                value = cleanFollowers,
                                label = "Followers"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = PaletteCream,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(PaletteSlateBlue.copy(alpha = 0.35f))
    )
}

/**
 * Primary Action Row:
 * [ ▶ Play ] [ ⇄ Shuffle ] [ ♡ ] [ ⍐ ]
 */
@Composable
private fun ArtistPrimaryActionRow(
    artist: ArtistDetails,
    isFollowing: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFollow: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // PLAY BUTTON (Cream #F4F1DE container with Dark Navy #0D1B2A text/icon)
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
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier
                .weight(1.3f)
                .height(46.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteDarkNavy,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Play",
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // SHUFFLE BUTTON (Dark translucent surface with AnimatedShuffleIcon)
        Box(
            modifier = Modifier
                .weight(1.3f)
                .height(46.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                .clickable(enabled = artist.topSongs.isNotEmpty()) { onShuffle() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedShuffleIcon(
                    tint = if (artist.topSongs.isNotEmpty()) PaletteCream else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Shuffle",
                    color = if (artist.topSongs.isNotEmpty()) PaletteCream else TextMuted,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // FOLLOW / FAVORITE BUTTON (Circle)
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, CircleShape)
                .clickable { onToggleFollow() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFollowing) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFollowing) "Following" else "Follow",
                tint = if (isFollowing) Color(0xFFE63946) else PaletteCream,
                modifier = Modifier.size(20.dp)
            )
        }

        // SHARE BUTTON (Circle)
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, CircleShape)
                .clickable { onShare() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = PaletteCream,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Tab Navigation Bar: Music | About | Albums | Similar Artists
 * with active Cream underline indicator.
 */
@Composable
private fun ArtistTabNavigation(
    selectedTab: ArtistProfileTab,
    onTabSelected: (ArtistProfileTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtistProfileTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) PaletteCream else TextSecondary,
                    fontFamily = SoraFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(if (isSelected) 36.dp else 0.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) PaletteCream else Color.Transparent)
                )
            }
        }
    }
}

/**
 * Ranked Popular Track Row (1, 2, 3...) matching reference design:
 * [Rank] [Artwork] Title & Plays/Subtitle [⋮]
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) SurfaceElevated else Color.Transparent)
            .combinedClickable(
                onClick = onPlay,
                onLongClick = { onMoreClick?.invoke() }
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
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
                // Rank Number (1, 2, 3...)
                Text(
                    text = "$rank",
                    color = if (rank <= 3) PaletteCream else TextSecondary,
                    fontFamily = SoraFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(26.dp)
                )

                // Track Artwork
                SieloSongArtwork(
                    thumbnailUrl = track.thumbnailUrl,
                    title = track.title,
                    artist = track.artist,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isPlaying) {
                    AnimatedEqualizerBars(
                        isPlaying = true,
                        barColor = PaletteSand,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                if (onMoreClick != null) {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Latest Release Card matching reference design:
 * [ Artwork ] Title, Subtitle, Chevron > & Circle Play Button ▶
 */
@Composable
private fun ArtistLatestReleaseCard(
    album: SieloAlbum,
    artistName: String,
    onOpenAlbum: () -> Unit,
    onPlayRelease: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .clickable { onOpenAlbum() }
            .padding(12.dp)
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
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = album.title,
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val trackCount = if (album.tracks.isNotEmpty()) album.tracks.size else album.songCount
                val typeStr = album.type?.takeIf { it.isNotBlank() } ?: "Album"
                val yearStr = album.year ?: ""
                val metaSubtitle = listOfNotNull(
                    typeStr,
                    yearStr.takeIf { it.isNotBlank() },
                    if (trackCount > 0) "$trackCount ${if (trackCount == 1) "song" else "songs"}" else null
                ).joinToString(" • ")

                Text(
                    text = metaSubtitle,
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Small Play Circle Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(PaletteCream)
                        .clickable { onPlayRelease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Release",
                        tint = PaletteDarkNavy,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Album Card for horizontal carousel:
 * [Square Artwork] Title and Release Year below.
 */
@Composable
private fun ArtistAlbumCard(
    album: SieloAlbum,
    artistName: String,
    onOpenAlbum: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable { onOpenAlbum() }
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            color = PaletteCream,
            fontFamily = UrbanistFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val yearStr = album.year ?: ""
        if (yearStr.isNotBlank()) {
            Text(
                text = yearStr,
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Similar Artist Circular Item for horizontal carousel:
 * [Circular Image] Artist Name below.
 */
@Composable
private fun ArtistSimilarItem(
    artist: SieloArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PaletteOxfordBlue)
                .border(1.5.dp, BorderGlass, CircleShape),
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
    }
}

/**
 * Full Albums Tab Content displaying distinct sections for authentic Studio Albums, Singles & EPs, and Soundtracks.
 */
@Composable
private fun ArtistAllAlbumsTabContent(
    artist: ArtistDetails,
    onOpenAlbum: (SieloAlbum) -> Unit
) {
    val studioAlbums = remember(artist) {
        val list = if (artist.musicBrainzId != null) {
            artist.originalAlbums
        } else {
            (artist.originalAlbums + artist.pastAlbums)
                .filter { !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) && !it.type.equals("Single", ignoreCase = true) && !it.type.equals("EP", ignoreCase = true) && (it.songCount > 1 || it.tracks.size > 1) && it.songCount != 1 }
                .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, artist.name) }
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }
        list.distinctBy { it.musicBrainzId ?: it.id.ifBlank { it.title.trim().lowercase() } }
            .sortedByDescending { extractYear(it.year ?: it.releaseDate) }
    }

    val singlesAndEPs = remember(artist) {
        val list = if (artist.musicBrainzId != null) {
            artist.singles + artist.pastAlbums.filter { it.type.equals("EP", ignoreCase = true) }
        } else {
            (artist.singles + (artist.originalAlbums + artist.pastAlbums).filter { it.type.equals("Single", ignoreCase = true) || it.type.equals("EP", ignoreCase = true) || it.songCount == 1 || (it.tracks.size == 1 && it.songCount <= 1) })
                .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, artist.name) }
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }
        list.distinctBy { it.musicBrainzId ?: it.id.ifBlank { it.title.trim().lowercase() } }
            .sortedByDescending { extractYear(it.year ?: it.releaseDate) }
    }

    val soundtracks = remember(artist) {
        val list = if (artist.musicBrainzId != null) {
            artist.featuredAlbums
        } else {
            (artist.featuredAlbums + (artist.originalAlbums + artist.pastAlbums).filter { TrackMatchValidator.isSoundtrackRelease(it.title, it.type) })
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }
        list.distinctBy { it.musicBrainzId ?: it.id.ifBlank { it.title.trim().lowercase() } }
            .sortedByDescending { extractYear(it.year ?: it.releaseDate) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Studio Albums Section
        if (studioAlbums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Studio Albums (${studioAlbums.size})",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                studioAlbums.forEach { album ->
                    ArtistAlbumListRow(
                        album = album,
                        artistName = artist.name,
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }
        }

        // 2. Singles & EPs Section
        if (singlesAndEPs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Singles & EPs (${singlesAndEPs.size})",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                singlesAndEPs.forEach { album ->
                    ArtistAlbumListRow(
                        album = album,
                        artistName = artist.name,
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }
        }

        // 3. Soundtracks & Features Section
        if (soundtracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Soundtracks & Features (${soundtracks.size})",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                soundtracks.forEach { album ->
                    ArtistAlbumListRow(
                        album = album,
                        artistName = artist.name,
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistAlbumListRow(
    album: SieloAlbum,
    artistName: String,
    onOpenAlbum: (SieloAlbum) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .clickable { onOpenAlbum(album) }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SieloSongArtwork(
                thumbnailUrl = album.thumbnailUrl,
                title = album.title,
                artist = artistName,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val trackCount = if (album.tracks.isNotEmpty()) album.tracks.size else album.songCount
                val type = album.type?.takeIf { it.isNotBlank() } ?: if (trackCount == 1) "Single" else "Album"
                val subtitle = listOfNotNull(
                    type,
                    album.year?.takeIf { it.isNotBlank() },
                    if (trackCount > 0) "$trackCount tracks" else null
                ).joinToString(" • ")

                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Full Similar Artists Tab Content.
 */
@Composable
private fun ArtistSimilarArtistsTabContent(
    similarArtists: List<SieloArtist>,
    onOpenArtist: (SieloArtist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Similar Artists & Influences",
            color = PaletteCream,
            fontFamily = SoraFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp)
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

/**
 * Improvised About Section displaying artist biography, audience metrics, musical identity, catalog breakdown, and Wikipedia encyclopedia links.
 */
@Composable
private fun ArtistDetailedAboutSection(
    artist: ArtistDetails,
    onOpenArtist: (SieloArtist) -> Unit,
    onOpenWiki: () -> Unit
) {
    var isBioExpanded by remember { mutableStateOf(false) }

    val bio = artist.bio?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
    val desc = artist.description?.takeIf { it.isNotBlank() }
    val origin = artist.origin?.takeIf { it.isNotBlank() }
    val activeYears = artist.activeYears?.takeIf { it.isNotBlank() }
    val recordLabel = artist.recordLabel?.takeIf { it.isNotBlank() }
    val dominantLanguage = artist.dominantLanguage?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true) && it.isNotBlank() }
    val dominantType = artist.dominantType?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("artist", ignoreCase = true) && !it.equals("undefined", ignoreCase = true) && it.isNotBlank() }
    val monthlyListeners = artist.monthlyListeners?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }
    val followerCount = artist.followerCount?.takeIf { !it.equals("null", ignoreCase = true) && !it.equals("0", ignoreCase = true) && it.isNotBlank() }

    val studioAlbumCount = remember(artist) {
        val list = if (artist.musicBrainzId != null) {
            artist.originalAlbums
        } else {
            (artist.originalAlbums + artist.pastAlbums)
                .filter { !TrackMatchValidator.isSoundtrackRelease(it.title, it.type) && !it.type.equals("Single", ignoreCase = true) && !it.type.equals("EP", ignoreCase = true) && (it.songCount > 1 || it.tracks.size > 1) && it.songCount != 1 }
                .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, artist.name) }
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }
        list.distinctBy { it.musicBrainzId ?: it.id.ifBlank { it.title.trim().lowercase() } }
            .size
    }

    val singlesCount = remember(artist) {
        val list = if (artist.musicBrainzId != null) {
            artist.singles + artist.pastAlbums.filter { it.type.equals("EP", ignoreCase = true) }
        } else {
            (artist.singles + (artist.originalAlbums + artist.pastAlbums).filter { it.type.equals("Single", ignoreCase = true) || it.type.equals("EP", ignoreCase = true) || it.songCount == 1 || (it.tracks.size == 1 && it.songCount <= 1) })
                .filter { TrackMatchValidator.isAlbumMadeByArtist(it.title, it.artist, artist.name) }
                .filterNot { TrackMatchValidator.isCompilationAlbum(it.title, artist.name) }
        }
        list.distinctBy { it.musicBrainzId ?: it.id.ifBlank { it.title.trim().lowercase() } }
            .size
    }

    val totalCatalogSongs = remember(artist) {
        val songs = (artist.topSongs + artist.originalAlbums.flatMap { it.tracks } + artist.pastAlbums.flatMap { it.tracks } + artist.singles.flatMap { it.tracks })
            .distinctBy { it.id }
        songs.size
    }

    val genresList = remember(artist.genres, artist.dominantType, artist.dominantLanguage) {
        if (artist.genres.isNotEmpty()) {
            artist.genres
        } else {
            val list = mutableListOf<String>()
            val type = artist.dominantType?.trim()
            if (!type.isNullOrBlank() && !type.equals("null", ignoreCase = true) && !type.equals("artist", ignoreCase = true) && !type.equals("undefined", ignoreCase = true)) {
                type.split(",", "/", "&", "•").map { it.trim() }.filter { it.isNotBlank() }.forEach { list.add(it) }
            }
            val lang = artist.dominantLanguage?.trim()
            if (!lang.isNullOrBlank() && !lang.equals("null", ignoreCase = true) && !lang.equals("undefined", ignoreCase = true)) {
                list.add(lang)
            }
            list.distinct()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Biography Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BIOGRAPHY",
                        color = PaletteSageGreen,
                        fontFamily = SoraFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    if (artist.isVerified) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = PaletteSageGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Verified Artist",
                                color = PaletteSageGreen,
                                fontFamily = UrbanistFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                if (desc != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = desc,
                        color = PaletteSand,
                        fontFamily = SoraFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val displayedBio = bio ?: "${artist.name} is a celebrated musical artist featured on Sielo, renowned for their distinctive sound, compelling compositions, and globally streamed catalog."

                Text(
                    text = displayedBio,
                    color = PaletteCream,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    maxLines = if (isBioExpanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis
                )

                if (displayedBio.length > 200) {
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

        // 2. Audience & Streaming Insights Card
        if (monthlyListeners != null || followerCount != null || artist.topSongs.isNotEmpty() || artist.latestAlbum != null) {
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
                        text = "AUDIENCE & HIGHLIGHTS",
                        color = PaletteSageGreen,
                        fontFamily = SoraFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (monthlyListeners != null) {
                        ArtistDetailItem(
                            label = "Monthly Listeners",
                            value = monthlyListeners
                        )
                    }

                    if (followerCount != null) {
                        ArtistDetailItem(
                            label = "Global Followers",
                            value = followerCount
                        )
                    }

                    if (artist.topSongs.isNotEmpty()) {
                        ArtistDetailItem(
                            label = "Top Signature Track",
                            value = artist.topSongs.first().title
                        )
                    }

                    artist.latestAlbum?.let { latest ->
                        val latestTitle = latest.title
                        val latestYear = latest.year?.takeIf { it.isNotBlank() }
                        ArtistDetailItem(
                            label = "Latest Release",
                            value = if (latestYear != null) "$latestTitle ($latestYear)" else latestTitle
                        )
                    }
                }
            }
        }

        // 3. Musical Identity & Discography Metadata Box
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
                    text = "ARTIST DETAILS & CATALOG",
                    color = PaletteSageGreen,
                    fontFamily = SoraFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (origin != null) {
                    ArtistDetailItem(
                        label = "Origin & Hometown",
                        value = origin
                    )
                }

                if (activeYears != null) {
                    ArtistDetailItem(
                        label = "Active Years",
                        value = activeYears
                    )
                }

                if (recordLabel != null) {
                    ArtistDetailItem(
                        label = "Record Label",
                        value = recordLabel
                    )
                }

                if (!dominantLanguage.isNullOrBlank()) {
                    ArtistDetailItem(
                        label = "Primary Language",
                        value = dominantLanguage
                    )
                }

                if (!dominantType.isNullOrBlank()) {
                    ArtistDetailItem(
                        label = "Role & Artistry",
                        value = dominantType
                    )
                }

                if (studioAlbumCount > 0) {
                    ArtistDetailItem(
                        label = "Studio Albums",
                        value = "$studioAlbumCount Albums"
                    )
                }

                if (singlesCount > 0) {
                    ArtistDetailItem(
                        label = "Singles & EPs",
                        value = "$singlesCount Releases"
                    )
                }

                if (totalCatalogSongs > 0) {
                    ArtistDetailItem(
                        label = "Catalog Footprint",
                        value = "$totalCatalogSongs Tracks in Sielo"
                    )
                }

                ArtistDetailItem(
                    label = "Audio Streaming Quality",
                    value = "Hi-Res Lossless & 320kbps Direct"
                )

                ArtistDetailItem(
                    label = "Platform Status",
                    value = if (artist.isVerified) "Verified Official Artist" else "Standard Artist"
                )
            }
        }

        // 4. Wikipedia & Encyclopedia Button
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
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
                            .background(PaletteCream)
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

                // More Action Button with Dropdown Menu
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                            .border(1.dp, BorderGlass, CircleShape)
                            .clickable { onMoreClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Actions",
                            tint = PaletteCream,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreDropdown,
                        onDismissRequest = onDismissDropdown,
                        modifier = Modifier
                            .background(PaletteOxfordBlue)
                            .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share Artist", color = PaletteCream, fontFamily = UrbanistFontFamily) },
                            onClick = {
                                onDismissDropdown()
                                onShareArtist()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, tint = PaletteSand, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Start Artist Radio", color = PaletteCream, fontFamily = UrbanistFontFamily) },
                            onClick = {
                                onDismissDropdown()
                                onRadioArtist()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Radio, contentDescription = null, tint = PaletteSand, modifier = Modifier.size(18.dp))
                            }
                        )
                        if (!wikiUrl.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Explore Wikipedia", color = PaletteCream, fontFamily = UrbanistFontFamily) },
                                onClick = {
                                    onDismissDropdown()
                                    onOpenWiki()
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = PaletteSand, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Album Detail View opened whenever an album or release is tapped.
 * Displays universal AlbumHero and the list of tracks in the album.
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
                val context = LocalContext.current
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
                            Toast.makeText(context, "Loading album tracks before saving...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMore = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, effectiveAlbum.title)
                            putExtra(Intent.EXTRA_TEXT, "Listen to \"${effectiveAlbum.title}\" by ${effectiveAlbum.artist} on Sielo")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share ${effectiveAlbum.title}").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
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

        // Hero Shimmer Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha * 0.7f))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Title Shimmer Bar
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .width(220.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Chips Shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha * 0.7f))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
            Box(
                modifier = Modifier
                    .weight(1.3f)
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
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tracks Shimmer Rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha * 0.6f))
                )
            }
        }
    }
}

/**
 * Interactive Artist Share Bottom Sheet.
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
                                            text = "${index + 1}",
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
                    containerColor = PaletteCream,
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
 * Background Card Image Generator.
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
                    AndroidColor.rgb(11, 20, 38),
                    AndroidColor.rgb(18, 30, 49),
                    AndroidColor.rgb(7, 13, 26)
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
            canvas.drawText("${index + 1}", 120f, trackY, rankPaint)
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
