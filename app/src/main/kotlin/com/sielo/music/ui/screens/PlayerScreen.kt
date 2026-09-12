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

import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.UrbanistFontFamily

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause

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
    val lyricOffsetMs by viewModel.lyricOffsetMs.collectAsState()

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
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar (Balanced & Perfectly Centered Title in Between)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Centered Title: "Lyrics" or "Now Playing"
                Text(
                    text = if (showLyrics) "Lyrics" else "Now Playing",
                    color = TextPrimary,
                    fontFamily = SoraFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Left Action (Close)
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }

                // Right Actions (Lyrics + Queue)
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

            // Sync Fine-Tuning Pill in Lyrics Mode
            if (showLyrics && !lyrics?.lines.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "−0.5s",
                        color = if (lyricOffsetMs < 0) AccentCoral else TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                            .clickable { viewModel.adjustLyricOffset(-500L) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = if (lyricOffsetMs == 0L) "Sync: 0.0s" else "Sync: ${if (lyricOffsetMs > 0) "+" else ""}${lyricOffsetMs / 1000.0}s",
                        color = if (lyricOffsetMs != 0L) AccentCoral else TextSecondary,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.resetLyricOffset() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "+0.5s",
                        color = if (lyricOffsetMs > 0) AccentCoral else TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                            .clickable { viewModel.adjustLyricOffset(500L) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Center Content: Rotating Vinyl Disc or Centered Synced Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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
                    // Centered Synced Lyrics View with Smooth Top/Bottom Edge Fading
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
                                fontFamily = UrbanistFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    } else if (lyrics?.lines.isNullOrEmpty()) {
                        Text(
                            text = lyrics?.plainLyrics ?: "No synchronized lyrics available",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    } else {
                        val lines = lyrics!!.lines
                        val listState = rememberLazyListState()

                        val currentMs = playbackState.currentPositionMs
                        val activeIndex = lines.indexOfLast { (it.timestampMs + lyricOffsetMs) <= currentMs }

                        // Smoothly scroll to keep the currently active lyric line centered
                        LaunchedEffect(activeIndex) {
                            if (lines.isNotEmpty()) {
                                if (activeIndex in lines.indices) {
                                    listState.animateScrollToItem(
                                        index = activeIndex,
                                        scrollOffset = 0
                                    )
                                } else if (activeIndex == -1) {
                                    listState.animateScrollToItem(index = 0, scrollOffset = 0)
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.0f to Color.Transparent,
                                            0.10f to Color.Black,
                                            0.90f to Color.Black,
                                            1.0f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                },
                            contentPadding = PaddingValues(top = 220.dp, bottom = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(lines) { index, line ->
                                val isActive = index == activeIndex
                                Text(
                                    text = line.text,
                                    color = if (isActive) PaletteCream else PaletteSlateBlue.copy(alpha = 0.40f),
                                    fontFamily = if (isActive) SoraFontFamily else UrbanistFontFamily,
                                    fontSize = if (isActive) 24.sp else 17.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    lineHeight = if (isActive) 34.sp else 24.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.seekTo((line.timestampMs + lyricOffsetMs).coerceAtLeast(0L)) }
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Track Info & Tactile Controls
            if (!showLyrics) {
                // Full Screen Vinyl Mode Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title & Artist (Song Title: Urbanist 600, Metadata: Urbanist 400)
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = track.artist,
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // Premium Animated Waveform Progress Bar & Interactive Seeking
                    WaveformProgressBar(
                        currentPositionMs = playbackState.currentPositionMs,
                        durationMs = playbackState.durationMs,
                        isPlaying = playbackState.isPlaying,
                        trackId = track.id,
                        onSeek = { targetMs -> viewModel.seekTo(targetMs) }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

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
            } else {
                // Compact Streamlined Bar in Lyrics Mode (Prevents Overlap & Keeps Maximum Focus on Lyrics)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderGlass, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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
                            Text(
                                text = track.artist,
                                color = TextSecondary,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.skipPrevious() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(PaletteCream)
                                    .clickable { viewModel.togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = ObsidianBlack,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.skipNext() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    WaveformProgressBar(
                        currentPositionMs = playbackState.currentPositionMs,
                        durationMs = playbackState.durationMs,
                        isPlaying = playbackState.isPlaying,
                        trackId = track.id,
                        onSeek = { targetMs -> viewModel.seekTo(targetMs) }
                    )
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


