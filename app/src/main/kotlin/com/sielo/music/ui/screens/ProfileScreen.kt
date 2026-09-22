package com.sielo.music.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.sielo.music.core.database.dao.SongStat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sielo.music.BuildConfig
import com.sielo.music.core.auth.model.AuthProvider
import com.sielo.music.core.auth.model.UserProfile
import com.sielo.music.core.database.entity.FavoriteTrackEntity
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.core.network.models.SieloArtist
import com.sielo.music.core.network.models.SieloTrack
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
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily
import com.sielo.music.viewmodel.ProfileViewModel
import com.sielo.music.viewmodel.RecentlyPlayedAlbum
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.MoreVert
import com.sielo.music.ui.components.SongActionsSheet
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val userProfile by viewModel.currentUser.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.recentHistory.collectAsState()
    val favoriteArtists by viewModel.favoriteArtists.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val isArtistLoading by viewModel.isArtistLoading.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val sonicArchetype by viewModel.sonicArchetype.collectAsState()
    val onHeavyRepeatSongs by viewModel.onHeavyRepeatSongs.collectAsState()

    // System Back Gesture handling if Artist profile is open
    BackHandler(enabled = selectedArtist != null) {
        viewModel.closeArtist()
    }

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

    // Modals / Sheets state
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showLikedSongsSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showOfflineSheet by remember { mutableStateOf(false) }
    var selectedSongActionsTrack by remember { mutableStateOf<SieloTrack?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 210.dp)
        ) {
            // 1. TOP BAR WITH SETTINGS SHORTCUT
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PROFILE",
                            color = PaletteCream,
                            fontFamily = SoraFontFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Your identity, taste & music vault",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 12.sp
                        )
                    }

                    // Settings quick action icon -> opens real Settings Screen
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(PaletteOxfordBlue)
                            .border(1.dp, BorderGlass, CircleShape)
                            .clickable { onOpenSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = PaletteSand,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 2. PROFILE HEADER
            item {
                ProfileHeaderSection(
                    profile = userProfile,
                    favoritesCount = favorites.size,
                    onEditProfile = { showEditProfileSheet = true },
                    onOpenAuth = { viewModel.openAuthDialog() },
                    onOpenOnboarding = { viewModel.openOnboarding() }
                )
            }

            // 3. YOUR MUSIC (Liked Songs, Playlists, History, Downloads)
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionTitleRow(
                    title = "YOUR MUSIC",
                    subtitle = "Personal library, playlists & downloads"
                )
                Spacer(modifier = Modifier.height(10.dp))

                YourMusicBentoGrid(
                    favoritesCount = favorites.size,
                    historyCount = history.size,
                    onOpenLikedSongs = { showLikedSongsSheet = true },
                    onPlayLikedSongs = { viewModel.playLikedSongs() },
                    onOpenPlaylists = onOpenPlaylists,
                    onOpenHistory = { showHistorySheet = true },
                    onOpenOffline = { showOfflineSheet = true }
                )
            }

            // 4. FAVORITE ARTISTS
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitleRow(
                    title = "FAVORITE ARTISTS",
                    subtitle = "Artists you follow & listen to most"
                )
                Spacer(modifier = Modifier.height(10.dp))

                FavoriteArtistsHorizontalList(
                    artists = favoriteArtists,
                    onArtistClick = { viewModel.openArtist(it) },
                    onAddArtists = { viewModel.openOnboarding() }
                )
            }

            // 5. ON HEAVY REPEAT (Most Replayed Anthems)
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitleRow(
                    title = "ON HEAVY REPEAT",
                    subtitle = "Your obsessively streamed anthems & replay champions"
                )
                Spacer(modifier = Modifier.height(12.dp))

                OnHeavyRepeatSection(
                    songs = onHeavyRepeatSongs,
                    historyCount = history.size,
                    favoritesCount = favorites.size,
                    onPlaySong = { viewModel.playHeavyRepeatTrack(it) },
                    onPlayAll = { viewModel.playAllHeavyRepeat() },
                    onMoreSong = { song ->
                        selectedSongActionsTrack = SieloTrack(
                            id = song.songId,
                            title = song.songTitle,
                            artist = song.artistName,
                            thumbnailUrl = song.thumbnailUrl
                        )
                    }
                )
            }
        }
    }

    // ──────────────────────────────
    // BOTTOM SHEETS & MODALS
    // ──────────────────────────────

    // Edit Profile Modal
    if (showEditProfileSheet) {
        EditProfileBottomSheet(
            profile = userProfile,
            onDismiss = { showEditProfileSheet = false },
            onSave = { name, username, bio ->
                viewModel.updateProfile(name, username, bio)
                showEditProfileSheet = false
                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Liked Songs Bottom Sheet
    if (showLikedSongsSheet) {
        LikedSongsBottomSheet(
            favorites = favorites,
            onDismiss = { showLikedSongsSheet = false },
            onPlayTrack = { track, queue ->
                viewModel.playTrack(track, queue)
            },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onMoreTrack = { selectedSongActionsTrack = it }
        )
    }

    // Listening History Bottom Sheet
    if (showHistorySheet) {
        HistoryBottomSheet(
            history = history,
            onDismiss = { showHistorySheet = false },
            onPlayEvent = { viewModel.playHistoryItem(it) },
            onMoreHistoryTrack = { item ->
                selectedSongActionsTrack = SieloTrack(
                    id = item.songId,
                    title = item.songTitle,
                    artist = item.artistName,
                    thumbnailUrl = item.thumbnailUrl
                )
            }
        )
    }

    // Offline / Downloads Bottom Sheet
    if (showOfflineSheet) {
        OfflineBottomSheet(
            cacheSize = "Hi-Fi Vault",
            onDismiss = { showOfflineSheet = false },
            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) }
        )
    }

    // Song Actions Sheet
    selectedSongActionsTrack?.let { track ->
        SongActionsSheet(
            track = track,
            onDismiss = { selectedSongActionsTrack = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. PROFILE HEADER SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeaderSection(
    profile: UserProfile?,
    favoritesCount: Int,
    onEditProfile: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    val displayName = profile?.name?.takeIf { it.isNotBlank() } ?: "Sielo Listener"
    val username = profile?.username?.takeIf { it.isNotBlank() }
        ?: profile?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: "sielo_user"
    val bio = profile?.bio?.takeIf { it.isNotBlank() }
        ?: "Audiophile • Exploring soundscapes & timeless originals in 320kbps Hi-Fi"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with glowing ring & badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(PaletteDarkNavy)
                            .border(2.dp, PaletteSand, CircleShape)
                            .shadow(8.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profile?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile?.photoUrl,
                                contentDescription = "Profile Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val initial = displayName.firstOrNull()?.uppercase() ?: "S"
                            Text(
                                text = initial,
                                color = PaletteSand,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            )
                        }
                    }

                    // Edit badge overlay
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PaletteSand)
                            .clickable { onEditProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Avatar",
                            tint = PaletteDarkNavy,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Identity info: Name, Username, Account badge
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            color = PaletteCream,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = PaletteSageGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "@$username",
                        color = PaletteSand,
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = if (profile != null) {
                            if (profile.provider == AuthProvider.GOOGLE) "Google Account" else "Sielo Account"
                        } else {
                            "Guest Listener"
                        },
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bio
            Text(
                text = bio,
                color = PaletteCream.copy(alpha = 0.85f),
                fontFamily = UrbanistFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Edit Profile & Account/Taste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Edit Profile Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteDarkNavy)
                        .border(1.dp, PaletteSand.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { onEditProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = PaletteSand,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "EDIT PROFILE",
                            color = PaletteSand,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Account / Taste Action Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (profile == null) PaletteSand else PaletteDarkNavy)
                        .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                        .clickable {
                            if (profile == null) onOpenAuth() else onOpenOnboarding()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (profile == null) "SIGN IN" else "EDIT TASTE",
                        color = if (profile == null) PaletteDarkNavy else PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. YOUR MUSIC (Liked Songs, Playlists, History, Downloads)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun YourMusicBentoGrid(
    favoritesCount: Int,
    historyCount: Int,
    onOpenLikedSongs: () -> Unit,
    onPlayLikedSongs: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOffline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Liked Songs & Playlists
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Liked Songs Card
            BentoMusicCard(
                modifier = Modifier.weight(1f),
                title = "Liked Songs",
                subtitle = "$favoritesCount tracks",
                icon = Icons.Default.Favorite,
                iconColor = Color(0xFFE63946),
                iconBg = Color(0xFFE63946).copy(alpha = 0.15f),
                onClick = onOpenLikedSongs,
                onPlay = onPlayLikedSongs
            )

            // Playlists Card
            BentoMusicCard(
                modifier = Modifier.weight(1f),
                title = "Playlists",
                subtitle = "Custom mixes & vaults",
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                iconColor = PaletteSand,
                iconBg = PaletteSand.copy(alpha = 0.15f),
                onClick = onOpenPlaylists
            )
        }

        // Row 2: Listening History & Downloads / Offline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Listening History Card
            BentoMusicCard(
                modifier = Modifier.weight(1f),
                title = "History",
                subtitle = "$historyCount recent plays",
                icon = Icons.Default.History,
                iconColor = PaletteSageGreen,
                iconBg = PaletteSageGreen.copy(alpha = 0.15f),
                onClick = onOpenHistory
            )

            // Downloads / Offline Card
            BentoMusicCard(
                modifier = Modifier.weight(1f),
                title = "Downloads",
                subtitle = "Hi-Fi offline vault",
                icon = Icons.Default.CloudDone,
                iconColor = Color(0xFF4EA8DE),
                iconBg = Color(0xFF4EA8DE).copy(alpha = 0.15f),
                onClick = onOpenOffline
            )
        }
    }
}

@Composable
private fun BentoMusicCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (onPlay != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PaletteSand)
                            .clickable { onPlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = PaletteDarkNavy,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. FAVORITE ARTISTS HORIZONTAL LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FavoriteArtistsHorizontalList(
    artists: List<SieloArtist>,
    onArtistClick: (SieloArtist) -> Unit,
    onAddArtists: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(artists) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clickable { onArtistClick(artist) }
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(PaletteOxfordBlue)
                        .border(1.5.dp, BorderGlass, CircleShape)
                        .shadow(4.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artist.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.imageUrl,
                            contentDescription = artist.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val initial = artist.name.firstOrNull()?.uppercase() ?: "A"
                        Text(
                            text = initial,
                            color = PaletteSand,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = artist.name,
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PaletteOxfordBlue)
                        .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Following",
                        color = PaletteSageGreen,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Add / Explore Artists Card
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clickable { onAddArtists() }
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(PaletteOxfordBlue.copy(alpha = 0.5f))
                        .border(1.5.dp, PaletteSand.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Artist",
                        tint = PaletteSand,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Add Artists",
                    color = PaletteSand,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. RECENTLY PLAYED HORIZONTAL LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentlyPlayedHorizontalList(
    albums: List<RecentlyPlayedAlbum>,
    onPlayAlbum: (RecentlyPlayedAlbum) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(albums) { album ->
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PaletteOxfordBlue)
                    .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                    .clickable { onPlayAlbum(album) }
                    .padding(10.dp)
            ) {
                Column {
                    // Artwork with play button
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaletteDarkNavy),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        if (!album.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = album.thumbnailUrl,
                                contentDescription = album.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = PaletteSand,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Play badge
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PaletteSand),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = PaletteDarkNavy,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = album.title,
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = album.artist,
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. ON HEAVY REPEAT SECTION (Live Room DB Replay Tracking)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnHeavyRepeatSection(
    songs: List<SongStat>,
    historyCount: Int,
    favoritesCount: Int,
    onPlaySong: (SongStat) -> Unit,
    onPlayAll: () -> Unit,
    onMoreSong: (SongStat) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Card for Heavy Repeat
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            PaletteOxfordBlue,
                            Color(0xFF233246),
                            PaletteOxfordBlue
                        )
                    )
                )
                .border(1.dp, PaletteSand.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(PaletteSageGreen)
                        )
                        Text(
                            text = "OBSESSIVELY STREAMED",
                            color = PaletteSand,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp
                        )
                    }
                    // Removed Total Plays box per request
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (songs.isNotEmpty()) "Your Heavy Repeat Mix" else "Start Exploring Anthems",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (songs.isNotEmpty())
                        "Tracks you keep returning to, ranked by real play counts and streamed minutes."
                    else
                        "Play songs across Sielo to generate your personalized Heavy Repeat rotation.",
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayAll,
                        colors = ButtonDefaults.buttonColors(containerColor = PaletteSand),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = PaletteDarkNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PLAY HEAVY ROTATION",
                            color = PaletteDarkNavy,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }

                    Text(
                        text = "${songs.size} Top Tracks",
                        color = PaletteSand,
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // List of Top Replayed Tracks (real Room DB data) - display all top 10 songs of the year
        if (songs.isNotEmpty()) {
            songs.take(10).forEachIndexed { index, song ->
                HeavyRepeatTrackRow(
                    rank = index + 1,
                    song = song,
                    onClick = { onPlaySong(song) },
                    onMoreClick = { onMoreSong(song) }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HeavyRepeatTrackRow(
    rank: Int,
    song: SongStat,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PaletteOxfordBlue.copy(alpha = 0.7f))
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Rank Badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when (rank) {
                        1 -> PaletteSand.copy(alpha = 0.25f)
                        2 -> PaletteSageGreen.copy(alpha = 0.25f)
                        else -> PaletteSlateBlue.copy(alpha = 0.35f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rank",
                color = when (rank) {
                    1 -> PaletteSand
                    2 -> PaletteSageGreen
                    else -> PaletteCream
                },
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        // Thumbnail
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PaletteDarkNavy)
        ) {
            if (!song.thumbnailUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.songTitle,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }
        }

        // Title + Artist + Replay count
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.songTitle,
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = song.artistName,
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "•",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "${song.playCount} replays",
                    color = PaletteSand,
                    fontFamily = UrbanistFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp
                )
            }
        }

        // Instant Play Button + More Options
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PaletteSand.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteSand,
                    modifier = Modifier.size(18.dp)
                )
            }
            androidx.compose.material3.IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = PaletteSand.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitleRow(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            color = PaletteCream,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.5.sp
        )
        Text(
            text = subtitle,
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 11.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODAL BOTTOM SHEETS
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileBottomSheet(
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onSave: (name: String, username: String?, bio: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameInput by remember { mutableStateOf(profile?.name ?: "") }
    var usernameInput by remember { mutableStateOf(profile?.username ?: (profile?.email?.substringBefore("@") ?: "")) }
    var bioInput by remember { mutableStateOf(profile?.bio ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Edit Profile",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "Update your public display identity & music bio",
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Display Name Field
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSand,
                    unfocusedBorderColor = PaletteSlateBlue,
                    focusedLabelColor = PaletteSand,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = PaletteCream,
                    unfocusedTextColor = PaletteCream
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Username Field
            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it },
                label = { Text("Username Handle (@)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSand,
                    unfocusedBorderColor = PaletteSlateBlue,
                    focusedLabelColor = PaletteSand,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = PaletteCream,
                    unfocusedTextColor = PaletteCream
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bio Field
            OutlinedTextField(
                value = bioInput,
                onValueChange = { if (it.length <= 160) bioInput = it },
                label = { Text("Bio (up to 160 characters)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSand,
                    unfocusedBorderColor = PaletteSlateBlue,
                    focusedLabelColor = PaletteSand,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = PaletteCream,
                    unfocusedTextColor = PaletteCream
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Save Button
            Button(
                onClick = { onSave(nameInput, usernameInput, bioInput) },
                colors = ButtonDefaults.buttonColors(containerColor = PaletteSand),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "SAVE CHANGES",
                    color = PaletteDarkNavy,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LikedSongsBottomSheet(
    favorites: List<FavoriteTrackEntity>,
    onDismiss: () -> Unit,
    onPlayTrack: (SieloTrack, List<SieloTrack>) -> Unit,
    onToggleFavorite: (SieloTrack) -> Unit,
    onMoreTrack: (SieloTrack) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tracks = remember(favorites) {
        favorites.map {
            SieloTrack(
                id = it.id,
                title = it.title,
                artist = it.artist,
                thumbnailUrl = it.thumbnailUrl,
                durationText = "3:30"
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Liked Songs Vault",
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "${favorites.size} saved favorites",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 13.sp
                    )
                }

                if (tracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PaletteSand)
                            .clickable { onPlayTrack(tracks.first(), tracks) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play All",
                            tint = PaletteDarkNavy,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (tracks.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tracks) { idx, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PaletteDarkNavy.copy(alpha = 0.5f))
                                .combinedClickable(
                                    onClick = { onPlayTrack(track, tracks) },
                                    onLongClick = { onMoreTrack(track) }
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format("%02d", idx + 1),
                                color = PaletteSand,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.width(26.dp)
                            )
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = PaletteCream,
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = TextSecondary,
                                    fontFamily = UrbanistFontFamily,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Liked",
                                    tint = Color(0xFFE63946),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onToggleFavorite(track) }
                                )
                                androidx.compose.material3.IconButton(
                                    onClick = { onMoreTrack(track) },
                                    modifier = Modifier.size(32.dp)
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
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No liked songs yet.\nTap heart on any song to save here.",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistsBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Your Playlists",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "Custom playlists & curated mixtapes",
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            val starterPlaylists = listOf(
                "Late Night Session" to "Ambient & Lofi Beats",
                "Workout Anthems" to "High energy EDM & Hip-Hop",
                "Bollywood Melodies" to "Timeless acoustic & classical hits"
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                starterPlaylists.forEach { (name, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaletteDarkNavy.copy(alpha = 0.5f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PaletteOxfordBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = PaletteSand,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(text = name, color = PaletteCream, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = desc, color = TextSecondary, fontFamily = UrbanistFontFamily, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryBottomSheet(
    history: List<ListeningEventEntity>,
    onDismiss: () -> Unit,
    onPlayEvent: (ListeningEventEntity) -> Unit,
    onMoreHistoryTrack: (ListeningEventEntity) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Listening History",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "Chronological stream history from your sessions",
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (history.isNotEmpty()) {
                val groupedHistory = remember(history) {
                    val result = mutableListOf<Pair<com.sielo.music.core.database.entity.ListeningEventEntity, Int>>()
                    var currentItem: com.sielo.music.core.database.entity.ListeningEventEntity? = null
                    var count = 0
                    for (item in history) {
                        if (currentItem == null) {
                            currentItem = item
                            count = 1
                        } else if (currentItem.songId == item.songId) {
                            count++
                        } else {
                            result.add(currentItem to count)
                            currentItem = item
                            count = 1
                        }
                    }
                    if (currentItem != null) {
                        result.add(currentItem to count)
                    }
                    result
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groupedHistory) { (item, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PaletteDarkNavy.copy(alpha = 0.5f))
                                .combinedClickable(
                                    onClick = { onPlayEvent(item) },
                                    onLongClick = { onMoreHistoryTrack(item) }
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = item.songTitle,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.songTitle,
                                    color = PaletteCream,
                                    fontFamily = UrbanistFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.artistName}${if (count > 1) " - x$count" else ""}",
                                    color = TextSecondary,
                                    fontFamily = SoraFontFamily,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No history recorded yet.\nStart streaming music to see history here.",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineBottomSheet(cacheSize: String, onDismiss: () -> Unit, onPlayTrack: (com.sielo.music.core.network.models.SieloTrack, List<com.sielo.music.core.network.models.SieloTrack>) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val cachedTracks by remember { mutableStateOf(com.sielo.music.core.audio.OfflineCacheManager.getCachedTracks(context)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4EA8DE).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF4EA8DE),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Downloads (Offline Vault)",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Songs you've listened to are fully cached here for instant gapless offline replay.\nCurrent Cache: $cacheSize",
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (cachedTracks.isEmpty()) {
                Text(
                    text = "No offline songs available.",
                    color = PaletteCream.copy(alpha = 0.6f),
                    fontFamily = UrbanistFontFamily
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cachedTracks) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PaletteDarkNavy.copy(alpha = 0.5f))
                                .clickable { onPlayTrack(track, cachedTracks) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = PaletteCream,
                                    fontFamily = UrbanistFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = TextSecondary,
                                    fontFamily = SoraFontFamily,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamQualitySelectionDialog(
    currentQuality: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        "Master (320kbps Opus / FLAC)" to "Pure uncompressed studio audio",
        "Hi-Fi High (256kbps)" to "Balanced fidelity & data saver",
        "Standard (128kbps)" to "Low bandwidth data friendly"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Audio Quality",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { (quality, desc) ->
                    val isSelected = currentQuality.startsWith(quality.substringBefore(" "))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PaletteSand.copy(alpha = 0.15f) else PaletteDarkNavy)
                            .border(
                                1.dp,
                                if (isSelected) PaletteSand else BorderGlass,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(quality) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = quality,
                                color = if (isSelected) PaletteSand else PaletteCream,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = desc,
                                color = TextSecondary,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 11.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = PaletteSand,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(20.dp),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PaletteSand)
            }
        }
    )
}









