package com.sielo.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.ui.components.RotatingVinylCard
import com.sielo.music.ui.components.TactilePlayButton
import com.sielo.music.ui.components.WaveformProgressBar
import com.sielo.music.ui.components.resolveAlbumArt
import com.sielo.music.ui.theme.AccentCoral
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SurfaceDark
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsState()

    val track = playbackState.currentTrack ?: return
    var showLyrics by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }

    // Step-by-step back handling:
    // 1. If separated Queue screen is open -> close Queue screen and return to Player screen
    // 2. Else if Lyrics view is active -> dismiss Lyrics view (return to Vinyl)
    // 3. Else -> collapse the player screen to the persistent mini player
    BackHandler(enabled = isQueueOpen) {
        isQueueOpen = false
    }

    BackHandler(enabled = !isQueueOpen && showLyrics) {
        showLyrics = false
    }

    BackHandler(enabled = !isQueueOpen && !showLyrics) {
        onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Main Music Player Display Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }

                Text(
                    text = if (showLyrics) "Synchronized Lyrics" else "Now Playing",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Lyrics Toggle Button
                    IconButton(onClick = {
                        showLyrics = !showLyrics
                    }) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) AccentCoral else TextSecondary
                        )
                    }

                    // Separated Up Next Queue Button
                    IconButton(onClick = {
                        isQueueOpen = true
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (isQueueOpen) AccentCoral else TextSecondary
                        )
                    }
                }
            }

            // Center Content: Rotating Vinyl Disc or Centered Synced Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!showLyrics) {
                    RotatingVinylCard(
                        artworkUrl = track.thumbnailUrl,
                        title = track.title,
                        artist = track.artist,
                        isPlaying = playbackState.isPlaying
                    )
                } else {
                    // Centered Synced Lyrics View
                    if (isLyricsLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = AccentCoral,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Finding lyrics...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else if (lyrics?.lines.isNullOrEmpty()) {
                        Text(
                            text = lyrics?.plainLyrics ?: "No synchronized lyrics available",
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    } else {
                        val lines = lyrics!!.lines
                        val listState = rememberLazyListState()

                        val activeIndex = lines.indexOfLast { it.timestampMs <= playbackState.currentPositionMs }
                            .coerceAtLeast(0)

                        // Automatically keep the playing lyric line centered
                        LaunchedEffect(activeIndex) {
                            if (lines.isNotEmpty() && activeIndex in lines.indices) {
                                listState.animateScrollToItem(
                                    index = activeIndex,
                                    scrollOffset = -180
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(lines) { index, line ->
                                val isActive = index == activeIndex
                                Text(
                                    text = line.text,
                                    color = if (isActive) PaletteCream else PaletteSlateBlue.copy(alpha = 0.5f),
                                    fontSize = if (isActive) 22.sp else 16.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.seekTo(line.timestampMs) }
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Track Info & Tactile Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title & Artist
                Text(
                    text = track.title,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track.artist,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Premium Animated Waveform Progress Bar & Interactive Seeking
                WaveformProgressBar(
                    currentPositionMs = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs,
                    isPlaying = playbackState.isPlaying,
                    trackId = track.id,
                    onSeek = { targetMs -> viewModel.seekTo(targetMs) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark)
                            .border(1.dp, BorderGlass, CircleShape)
                            .clickable { viewModel.skipPrevious() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    TactilePlayButton(
                        isPlaying = playbackState.isPlaying,
                        onClick = { viewModel.togglePlayPause() },
                        size = 72
                    )

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark)
                            .border(1.dp, BorderGlass, CircleShape)
                            .clickable { viewModel.skipNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Clean Separated Queue Screen (Opens as a full dedicated screen)
        AnimatedVisibility(
            visible = isQueueOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            QueueScreen(
                viewModel = viewModel,
                onBack = { isQueueOpen = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}


