package com.sielo.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.core.playlist.UserPlaylist
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
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
import com.sielo.music.viewmodel.UserPlaylistsViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.sielo.music.ui.components.SongActionsSheet
import com.sielo.music.viewmodel.SongActionsViewModel
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import com.sielo.music.viewmodel.PlaylistImportState
import com.sielo.music.viewmodel.PlaylistImportViewModel

@Composable
fun EditPlaylistNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Edit Playlist Name",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSand,
                    focusedLabelColor = PaletteSand,
                    unfocusedBorderColor = PaletteSand.copy(alpha = 0.5f),
                    unfocusedLabelColor = PaletteCream.copy(alpha = 0.5f),
                    focusedTextColor = PaletteCream,
                    unfocusedTextColor = PaletteCream
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PaletteSand),
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = PaletteOxfordBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PaletteCream)
            }
        }
    )
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Playlist Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaletteSand,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = PaletteCream,
                        unfocusedTextColor = PaletteCream,
                        focusedLabelColor = PaletteSand,
                        unfocusedLabelColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaletteSand,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = PaletteCream,
                        unfocusedTextColor = PaletteCream,
                        focusedLabelColor = PaletteSand,
                        unfocusedLabelColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(18.dp),
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title, desc)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
            ) {
                Text("Create", color = PaletteDarkNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PaletteSand)
            }
        }
    )
}

@Composable
fun ImportPlaylistDialog(
    viewModel: PlaylistImportViewModel,
    onDismiss: () -> Unit
) {
    var albumName by remember { mutableStateOf("") }
    var sourceLink by remember { mutableStateOf("") }
    val importState by viewModel.importState.collectAsState()

    AlertDialog(
        onDismissRequest = {
            if (importState !is PlaylistImportState.Resolving && importState !is PlaylistImportState.FetchingMetadata) {
                viewModel.resetState()
                onDismiss()
            }
        },
        containerColor = PaletteOxfordBlue,
        title = {
            Text(
                text = "Import Playlist",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (importState) {
                    is PlaylistImportState.Idle, is PlaylistImportState.Error -> {
                        if (importState is PlaylistImportState.Error) {
                            Text(
                                text = (importState as PlaylistImportState.Error).message,
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                        }

                        // Helpful instructions box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PaletteDarkNavy.copy(alpha = 0.7f))
                                .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "💡 How to Import:",
                                    color = PaletteSand,
                                    fontFamily = SoraFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• YouTube: Paste any public/unlisted playlist or video link.\n• Spotify: Paste any Spotify playlist, album, or track link (or paste copied song list text).",
                                    color = PaletteCream.copy(alpha = 0.85f),
                                    fontFamily = UrbanistFontFamily,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        OutlinedTextField(
                            value = albumName,
                            onValueChange = { albumName = it },
                            label = { Text("New Playlist Name") },
                            placeholder = { Text("e.g. My Favorites", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PaletteSand,
                                focusedLabelColor = PaletteSand,
                                unfocusedBorderColor = PaletteSand.copy(alpha = 0.5f),
                                unfocusedLabelColor = PaletteCream.copy(alpha = 0.5f),
                                focusedTextColor = PaletteCream,
                                unfocusedTextColor = PaletteCream
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = sourceLink,
                            onValueChange = { sourceLink = it },
                            label = { Text("YouTube URL or Spotify Link/Text") },
                            placeholder = { Text("e.g. https://open.spotify.com/playlist/... or YouTube playlist link", color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PaletteSand,
                                focusedLabelColor = PaletteSand,
                                unfocusedBorderColor = PaletteSand.copy(alpha = 0.5f),
                                unfocusedLabelColor = PaletteCream.copy(alpha = 0.5f),
                                focusedTextColor = PaletteCream,
                                unfocusedTextColor = PaletteCream
                            ),
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            maxLines = 5
                        )
                    }
                    is PlaylistImportState.FetchingMetadata -> {
                        Text("Fetching metadata from source...", color = PaletteCream)
                        CircularProgressIndicator(color = PaletteSand)
                    }
                    is PlaylistImportState.Resolving -> {
                        val state = importState as PlaylistImportState.Resolving
                        Text("Resolving tracks on InnerTube...", color = PaletteCream)
                        Text("Progress: ${state.progress} / ${state.total}", color = PaletteSand)
                        LinearProgressIndicator(
                            progress = { if (state.total > 0) state.progress.toFloat() / state.total else 0f },
                            color = PaletteSand,
                            trackColor = PaletteDarkNavy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is PlaylistImportState.Success -> {
                        val state = importState as PlaylistImportState.Success
                        Text("Import Successful!", color = PaletteSand, fontWeight = FontWeight.Bold)
                        Text("Imported: ${state.imported} tracks", color = PaletteCream)
                        Text("Skipped/Unresolved: ${state.skipped} tracks", color = PaletteCream)
                    }
                }
            }
        },
        confirmButton = {
            if (importState is PlaylistImportState.Idle || importState is PlaylistImportState.Error) {
                Button(
                    onClick = {
                        if (albumName.isNotBlank() && sourceLink.isNotBlank()) {
                            if (sourceLink.contains("spotify.com") || sourceLink.contains("spotify:")) {
                                viewModel.importFromPastedText(sourceLink, albumName)
                            } else if (sourceLink.contains("youtube.com") || sourceLink.contains("youtu.be") || sourceLink.contains("list=") || sourceLink.startsWith("PL") || sourceLink.startsWith("VL")) {
                                viewModel.importFromYouTube(sourceLink, albumName)
                            } else {
                                viewModel.importFromPastedText(sourceLink, albumName)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaletteSand),
                    enabled = albumName.isNotBlank() && sourceLink.isNotBlank()
                ) {
                    Text("Import", color = PaletteOxfordBlue, fontWeight = FontWeight.Bold)
                }
            } else if (importState is PlaylistImportState.Success) {
                Button(
                    onClick = {
                        viewModel.resetState()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
                ) {
                    Text("Done", color = PaletteOxfordBlue, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (importState !is PlaylistImportState.Resolving && importState !is PlaylistImportState.FetchingMetadata && importState !is PlaylistImportState.Success) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = PaletteCream)
                }
            }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun UserPlaylistsScreen(importViewModel: com.sielo.music.viewmodel.PlaylistImportViewModel,
    viewModel: UserPlaylistsViewModel,
    onBack: () -> Unit,
    onPlayTrack: (SieloTrack, List<SieloTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()

        var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var renamingPlaylistTarget by remember { mutableStateOf<UserPlaylist?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler {
        if (selectedPlaylist != null) {
            viewModel.closePlaylist()
        } else {
            onBack()
        }
    }

    if (selectedPlaylist != null) {
        UserPlaylistDetailView(
            playlist = selectedPlaylist!!,
            onBack = { viewModel.closePlaylist() },
            onPlayTrack = onPlayTrack,
            onPlayAll = { viewModel.playPlaylist(selectedPlaylist!!) },
            onRenamePlaylist = { newName ->
                viewModel.renamePlaylist(selectedPlaylist!!.id, newName)
            },
            onDeletePlaylist = {
                viewModel.deletePlaylist(selectedPlaylist!!.id)
            },
            onRemoveTrack = { trackId ->
                viewModel.removeTrack(selectedPlaylist!!.id, trackId)
            },
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header Bar
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PaletteCream,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "YOUR PLAYLISTS",
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${playlists.size} playlists saved",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Create Playlist Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteSand)
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = PaletteOxfordBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Create",
                            color = PaletteOxfordBlue,
                            fontFamily = SoraFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Import Playlist Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteDarkNavy)
                        .border(1.dp, PaletteSand.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { showImportDialog = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = PaletteSand,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Import",
                            color = PaletteSand,
                            fontFamily = SoraFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search playlists...",
                        color = TextMuted,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = PaletteSand,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSand,
                    unfocusedBorderColor = BorderGlass,
                    focusedTextColor = PaletteCream,
                    unfocusedTextColor = PaletteCream
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            val filtered = playlists.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = PaletteSlateBlue,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No playlists created yet" else "No matching playlists found",
                            color = PaletteCream,
                            fontFamily = SoraFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap NEW above to create your custom mixtape",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 210.dp)
                ) {
                    itemsIndexed(filtered) { _, pl ->
                        PlaylistRowCard(
                            playlist = pl,
                            onClick = { viewModel.openPlaylist(pl) },
                            onPlay = { viewModel.playPlaylist(pl) },
                            onRename = { renamingPlaylistTarget = pl },
                            onDelete = { viewModel.deletePlaylist(pl.id) }
                        )
                    }
                }
            }
        }
    }

    if (renamingPlaylistTarget != null) {
        EditPlaylistNameDialog(
            currentName = renamingPlaylistTarget!!.title,
            onDismiss = { renamingPlaylistTarget = null },
            onConfirm = { newName ->
                viewModel.renamePlaylist(renamingPlaylistTarget!!.id, newName)
                renamingPlaylistTarget = null
            }
        )
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            viewModel = importViewModel,
            onDismiss = { showImportDialog = false }
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, desc ->
                viewModel.createPlaylist(title, desc)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PlaylistRowCard(
    playlist: UserPlaylist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover Art
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PaletteDarkNavy),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = PaletteSand,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (playlist.description.isNotBlank()) playlist.description else "${playlist.tracks.size} tracks",
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PaletteSlateBlue.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${playlist.tracks.size} tracks",
                        color = PaletteSand,
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Play Button
        if (playlist.tracks.isNotEmpty()) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PaletteSand)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteDarkNavy,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // More Menu
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(PaletteOxfordBlue)
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Name", color = PaletteCream) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = PaletteSand
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Playlist", color = Color(0xFFE63946)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFE63946)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserPlaylistDetailView(
    playlist: UserPlaylist,
    onBack: () -> Unit,
    onPlayTrack: (SieloTrack, List<SieloTrack>) -> Unit,
    onPlayAll: () -> Unit,
    onRenamePlaylist: (String) -> Unit = {},
    onDeletePlaylist: () -> Unit,
    onRemoveTrack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedSongActionsTrack by remember { mutableStateOf<SieloTrack?>(null) }
    val songActionsVm: SongActionsViewModel = hiltViewModel()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 210.dp)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    if (!playlist.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = playlist.coverUrl,
                            contentDescription = playlist.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            PaletteDarkNavy.copy(alpha = 0.7f),
                                            PaletteDarkNavy
                                        )
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PaletteOxfordBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = PaletteSand.copy(alpha = 0.6f),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }

                    // Back, Edit & Delete buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PaletteDarkNavy.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PaletteCream,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { showRenameDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PaletteDarkNavy.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Name",
                                    tint = PaletteSand,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PaletteDarkNavy.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFE63946),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Title & Description overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = playlist.title,
                                color = PaletteCream,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = { showRenameDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Name",
                                    tint = PaletteSand,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (playlist.description.isNotBlank()) {
                            Text(
                                text = playlist.description,
                                color = TextSecondary,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "${playlist.tracks.size} tracks • User Collection",
                            color = PaletteSand,
                            fontFamily = UrbanistFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Play All Action Button
            if (playlist.tracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = onPlayAll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = PaletteDarkNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PLAY PLAYLIST",
                                color = PaletteDarkNavy,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Tracks list
            if (playlist.tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This playlist is currently empty.\nExplore and save tracks from albums & songs.",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(playlist.tracks) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onPlayTrack(track, playlist.tracks) },
                                onLongClick = { selectedSongActionsTrack = track }
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%02d", index + 1),
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
                                .size(44.dp)
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
                        IconButton(
                            onClick = { selectedSongActionsTrack = track },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onRemoveTrack(track.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

        // Song Actions Sheet
        selectedSongActionsTrack?.let { track ->
            SongActionsSheet(
                track = track,
                onDismiss = { selectedSongActionsTrack = null },
                viewModel = songActionsVm
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = {
                    Text(
                        text = "Delete Playlist?",
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete '${playlist.title}'? This action cannot be undone.",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 13.5.sp
                    )
                },
                containerColor = PaletteOxfordBlue,
                shape = RoundedCornerShape(18.dp),
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDeletePlaylist()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = PaletteSand)
                    }
                }
            )
        }

        if (showRenameDialog) {
            EditPlaylistNameDialog(
                currentName = playlist.title,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    onRenamePlaylist(newName)
                    showRenameDialog = false
                }
            )
        }
    }

