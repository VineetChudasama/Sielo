package com.sielo.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily
import com.sielo.music.viewmodel.SongActionsViewModel

/**
 * Universal, production-ready SongActionsSheet bottom sheet component.
 * Reusable across every screen in Sielo (Home, Search, Album, Artist, Playlist, Queue, History, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    track: SieloTrack,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SongActionsViewModel = hiltViewModel(),
    onNavigateToArtist: ((artistName: String) -> Unit)? = null,
    onNavigateToAlbum: ((albumTitle: String, artistName: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLiked by viewModel.isFavorite(track.id).collectAsState(initial = false)
    val userPlaylists by viewModel.playlists.collectAsState()

    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(PaletteSand.copy(alpha = 0.5f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // 1. SHEET HEADER: Artwork, Title, Artist, Album
            // ─────────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SieloSongArtwork(
                    thumbnailUrl = track.thumbnailUrl,
                    title = track.title,
                    artist = track.artist,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = PaletteSand,
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!track.album.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.album!!,
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                color = BorderGlass,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 10.dp)
            )

            // Scrollable actions list if height is constrained
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ─────────────────────────────────────────────────────────
                // GROUP 1: PLAYBACK
                // ─────────────────────────────────────────────────────────
                item {
                    ActionGroupHeader(title = "PLAYBACK")
                }

                item {
                    SongActionRow(
                        icon = Icons.Default.PlayArrow,
                        title = "Play Now",
                        subtitle = "Start listening immediately",
                        onClick = {
                            viewModel.playNow(track)
                            onDismiss()
                        }
                    )
                }

                item {
                    SongActionRow(
                        icon = Icons.Default.SkipNext,
                        title = "Play Next",
                        subtitle = "Insert next in current queue",
                        onClick = {
                            viewModel.playNext(track)
                            onDismiss()
                        }
                    )
                }

                item {
                    SongActionRow(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        title = "Add to Queue",
                        subtitle = "Append to end of queue",
                        onClick = {
                            viewModel.addToQueue(track)
                            onDismiss()
                        }
                    )
                }

                // ─────────────────────────────────────────────────────────
                // GROUP 2: LIBRARY
                // ─────────────────────────────────────────────────────────
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionGroupHeader(title = "LIBRARY")
                }

                item {
                    SongActionRow(
                        icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        iconTint = if (isLiked) Color(0xFFE63946) else PaletteCream,
                        title = if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                        subtitle = if (isLiked) "Saved in your favorites vault" else "Save to your favorites vault",
                        isHeart = true,
                        onClick = {
                            viewModel.toggleFavorite(track)
                        }
                    )
                }

                item {
                    SongActionRow(
                        icon = Icons.Default.PlaylistAdd,
                        title = "Add to Playlist",
                        subtitle = "Save to your custom playlists",
                        onClick = {
                            showPlaylistPicker = true
                        }
                    )
                }



                // ─────────────────────────────────────────────────────────
                // GROUP 3: DISCOVER
                // ─────────────────────────────────────────────────────────
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionGroupHeader(title = "DISCOVER")
                }

                if (track.artist.isNotBlank() && onNavigateToArtist != null) {
                    item {
                        SongActionRow(
                            icon = Icons.Default.Mic,
                            title = "Go to Artist",
                            subtitle = track.artist,
                            onClick = {
                                onDismiss()
                                onNavigateToArtist(track.artist)
                            }
                        )
                    }
                }

                if (!track.album.isNullOrBlank() && onNavigateToAlbum != null) {
                    item {
                        SongActionRow(
                            icon = Icons.Default.Album,
                            title = "Go to Album",
                            subtitle = track.album!!,
                            onClick = {
                                onDismiss()
                                onNavigateToAlbum(track.album!!, track.artist)
                            }
                        )
                    }
                }

                item {
                    SongActionRow(
                        icon = Icons.Default.Share,
                        title = "Share",
                        subtitle = "Share song link & preview",
                        onClick = {
                            viewModel.shareTrack(track)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECONDARY BOTTOM SHEET: Add to Playlist
    // ─────────────────────────────────────────────────────────────────────────
    if (showPlaylistPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = PaletteDarkNavy,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add to Playlist",
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Button(
                        onClick = {
                            showCreatePlaylistDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PaletteSand),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = PaletteDarkNavy,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New",
                            color = PaletteDarkNavy,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (userPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No custom playlists yet.\nTap 'New' to create one.",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(userPlaylists) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PaletteOxfordBlue)
                                    .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.addToPlaylist(pl.id, track, pl.title)
                                        showPlaylistPicker = false
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SieloSongArtwork(
                                    thumbnailUrl = pl.coverUrl ?: track.thumbnailUrl,
                                    title = pl.title,
                                    artist = "Playlist",
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.title,
                                        color = PaletteCream,
                                        fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pl.tracks.size} tracks",
                                        color = TextSecondary,
                                        fontFamily = UrbanistFontFamily,
                                        fontSize = 12.sp
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = PaletteSand,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE PLAYLIST DIALOG
    // ─────────────────────────────────────────────────────────────────────────
    if (showCreatePlaylistDialog) {
        var playlistNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = PaletteOxfordBlue,
            title = {
                Text(
                    text = "Create Playlist",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a name for your new playlist.",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        label = { Text("Playlist Name") },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = playlistNameInput.trim()
                        if (name.isNotBlank()) {
                            viewModel.createPlaylistAndAdd(name, track)
                            showCreatePlaylistDialog = false
                            showPlaylistPicker = false
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaletteSand),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Create & Add",
                        color = PaletteDarkNavy,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 13.sp
                    )
                }
            }
        )
    }
}

@Composable
private fun ActionGroupHeader(title: String) {
    Text(
        text = title,
        color = PaletteSageGreen,
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun SongActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconTint: Color = PaletteCream,
    enabled: Boolean = true,
    isHeart: Boolean = false,
    onClick: () -> Unit
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isHeart && iconTint != PaletteCream) 1.25f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "iconScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (enabled) PaletteDarkNavy.copy(alpha = 0.7f) else PaletteDarkNavy.copy(alpha = 0.3f))
                .border(1.dp, BorderGlass, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) PaletteCream else TextMuted,
                fontFamily = UrbanistFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                color = if (enabled) TextSecondary else TextMuted.copy(alpha = 0.7f),
                fontFamily = UrbanistFontFamily,
                fontSize = 11.5.sp
            )
        }
    }
}

