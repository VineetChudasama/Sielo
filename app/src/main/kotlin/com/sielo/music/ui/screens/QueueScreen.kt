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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sielo.music.ui.components.AnimatedEqualizerBars
import com.sielo.music.ui.theme.AccentCoral
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.SurfaceDark
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.UrbanistFontFamily
import com.sielo.music.viewmodel.PlayerViewModel

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val track = playbackState.currentTrack ?: return
    val queue = playbackState.queue
    val currentIndex = playbackState.queueIndex
    val upcomingTracks = if (queue.isNotEmpty() && currentIndex >= 0 && currentIndex + 1 < queue.size) {
        queue.drop(currentIndex + 1)
    } else {
        emptyList()
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Player",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Up Next Queue",
                        color = TextPrimary,
                        fontFamily = SoraFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${upcomingTracks.size} upcoming songs",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. NOW PLAYING CARD
            Text(
                text = "NOW PLAYING",
                color = AccentCoral,
                fontFamily = SoraFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                PaletteOxfordBlue,
                                PaletteDarkNavy
                            )
                        )
                    )
                    .border(1.dp, AccentCoral.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.sielo.music.ui.components.SieloSongArtwork(
                    thumbnailUrl = track.thumbnailUrl,
                    title = track.title,
                    artist = track.artist,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = PaletteSand,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedEqualizerBars(
                    isPlaying = playbackState.isPlaying,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    barColor = AccentCoral
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. UP NEXT SECTION
            Text(
                text = "UP NEXT (${upcomingTracks.size} SONGS)",
                color = TextSecondary,
                fontFamily = SoraFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            if (upcomingTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark.copy(alpha = 0.3f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No more songs in queue",
                            color = TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Search or select more songs to continue playing",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    itemsIndexed(upcomingTracks) { offsetIndex, qTrack ->
                        val queuePosition = offsetIndex + 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark.copy(alpha = 0.6f))
                                .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.playTrack(qTrack, queue)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Queue Index Badge
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(PaletteOxfordBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$queuePosition",
                                    color = PaletteCream,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            com.sielo.music.ui.components.SieloSongArtwork(
                                thumbnailUrl = qTrack.thumbnailUrl,
                                title = qTrack.title,
                                artist = qTrack.artist,
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = qTrack.title,
                                    color = TextPrimary,
                                    fontFamily = UrbanistFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = qTrack.artist,
                                    color = TextSecondary,
                                    fontFamily = UrbanistFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = qTrack.durationText ?: "",
                                color = TextMuted,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
