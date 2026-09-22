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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sielo.music.ui.components.SongActionsSheet
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
import com.sielo.music.viewmodel.PlaylistCardItem

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistCardItem,
    tracks: List<SieloTrack>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (SieloTrack, List<SieloTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    var selectedSongActionsTrack by remember { mutableStateOf<SieloTrack?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 210.dp)
        ) {
            // Header Image & Gradient Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    if (playlist.isLiked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            PaletteOxfordBlue,
                                            PaletteDarkNavy
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(PaletteSand)
                                    .border(1.dp, BorderGlass, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Liked",
                                    tint = PaletteDarkNavy,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = playlist.coverUrl,
                            contentDescription = playlist.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            PaletteDarkNavy.copy(alpha = 0.8f),
                                            PaletteDarkNavy
                                        )
                                    )
                                )
                        )
                    }

                    // Floating Back Button
                    Box(
                        modifier = Modifier
                            .padding(top = 48.dp, start = 20.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PaletteDarkNavy.copy(alpha = 0.65f))
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
            }

            // Playlist Info Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = playlist.title,
                        color = PaletteCream,
                        fontFamily = com.sielo.music.ui.theme.SoraFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = playlist.subtitle,
                        color = PaletteSand,
                        fontFamily = com.sielo.music.ui.theme.UrbanistFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${tracks.size} tracks curated for you",
                        color = TextSecondary,
                        fontFamily = com.sielo.music.ui.theme.UrbanistFontFamily,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // "Play All" Action Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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
                                    text = "PLAY ALL",
                                    color = PaletteDarkNavy,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Playlist Tracks List
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
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
                            .padding(horizontal = 20.dp, vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (playlist.isLiked) "No liked tracks yet in your vault." else "No songs found for this playlist.",
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
                        onMoreClick = { selectedSongActionsTrack = track }
                    )
                }
            }
        }
    }

    selectedSongActionsTrack?.let { tr ->
        SongActionsSheet(
            track = tr,
            onDismiss = { selectedSongActionsTrack = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistTrackRow(
    index: Int,
    track: SieloTrack,
    onPlay: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = { onMoreClick?.invoke() }
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%02d".format(index),
                color = PaletteSlateBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(30.dp)
            )

            com.sielo.music.ui.components.SieloSongArtwork(
                thumbnailUrl = track.thumbnailUrl,
                title = track.title,
                artist = track.artist,
                modifier = Modifier
                    .size(46.dp)
                    .border(1.dp, BorderGlass, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = track.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${track.artist} • ${track.formattedDuration}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteSand,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (onMoreClick != null) {
                IconButton(onClick = onMoreClick) {
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
