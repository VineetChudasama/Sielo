package com.sielo.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.core.audio.model.PlaybackState
import com.sielo.music.core.lyrics.model.SieloLyrics
import com.sielo.music.core.network.models.SieloTrack
import com.sielo.music.ui.components.AnimatedShuffleIcon
import com.sielo.music.ui.components.RotatingVinylCard
import com.sielo.music.ui.components.SieloSongArtwork
import com.sielo.music.ui.components.TactilePlayButton
import com.sielo.music.ui.components.WaveformProgressBar
import com.sielo.music.ui.components.rememberShuffleAnimationState
import com.sielo.music.ui.theme.AccentCoral
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.SurfaceDark
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily
import com.sielo.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsState()
    val lyricOffsetMs by viewModel.lyricOffsetMs.collectAsState()

    val track = playbackState.currentTrack ?: return
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = screenHeightPx * 0.18f

    val sheetOffsetY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sheetOffsetY.snapTo(0f)
        isDismissing = false
    }

    fun dismissPlayer(velocity: Float = 0f) {
        if (isDismissing) return
        isDismissing = true
        coroutineScope.launch {
            sheetOffsetY.animateTo(
                targetValue = screenHeightPx,
                animationSpec = if (velocity > 0f) {
                    tween(durationMillis = 240, easing = FastOutLinearInEasing)
                } else {
                    tween(durationMillis = 300, easing = FastOutSlowInEasing)
                },
                initialVelocity = velocity.coerceAtLeast(0f)
            )
            onClose()
        }
    }

    fun snapBackToTop() {
        if (isDismissing) return
        coroutineScope.launch {
            sheetOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // 3-Page Root Pager:
    // Page 0: Lyrics View (Swipe Left from Center / Swipe Right on Disc to Open)
    // Page 1: Now Playing Turntable View (Center)
    // Page 2: Queue View (Swipe Right from Center / Swipe Left on Disc to Open)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    // Step-by-step back handling:
    // 1. If on Lyrics (0) or Queue (2) -> return to Now Playing (1)
    // 2. If on Now Playing (1) -> collapse player to mini player
    BackHandler(enabled = pagerState.currentPage != 1) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(1, animationSpec = tween(380, easing = FastOutSlowInEasing))
        }
    }

    BackHandler(enabled = pagerState.currentPage == 1) {
        onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, sheetOffsetY.value.roundToInt().coerceAtLeast(0)) }
            .graphicsLayer {
                alpha = (1f - (sheetOffsetY.value / screenHeightPx) * 0.35f).coerceIn(0.65f, 1f)
            }
            .background(ObsidianBlack)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> {
                    // Page 0: Lyrics View
                    LyricsPageView(
                        viewModel = viewModel,
                        track = track,
                        playbackState = playbackState,
                        lyrics = lyrics,
                        isLyricsLoading = isLyricsLoading,
                        lyricOffsetMs = lyricOffsetMs,
                        onBack = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1, animationSpec = tween(380, easing = FastOutSlowInEasing))
                            }
                        },
                        onClose = { dismissPlayer() },
                        onOpenQueue = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(2, animationSpec = tween(380, easing = FastOutSlowInEasing))
                            }
                        },
                        onReturnToVinyl = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1, animationSpec = tween(380, easing = FastOutSlowInEasing))
                            }
                        },
                        sheetOffsetY = sheetOffsetY,
                        dismissThresholdPx = dismissThresholdPx,
                        onDismissPlayer = { dismissPlayer(it) },
                        onSnapBack = { snapBackToTop() }
                    )
                }
                1 -> {
                    // Page 1: Now Playing Turntable View
                    NowPlayingPageView(
                        viewModel = viewModel,
                        track = track,
                        playbackState = playbackState,
                        onClose = { dismissPlayer() },
                        onOpenLyrics = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0, animationSpec = tween(380, easing = FastOutSlowInEasing))
                            }
                        },
                        onOpenQueue = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(2, animationSpec = tween(380, easing = FastOutSlowInEasing))
                            }
                        },
                        sheetOffsetY = sheetOffsetY,
                        dismissThresholdPx = dismissThresholdPx,
                        onDismissPlayer = { dismissPlayer(it) },
                        onSnapBack = { snapBackToTop() }
                    )
                }
                2 -> {
                    // Page 2: Separated Queue View
                    QueueScreen(
                        viewModel = viewModel,
                        onBack = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1, animationSpec = tween(380, easing = FastOutSlowInEasing))
                            }
                        },
                        onClose = { dismissPlayer() },
                        modifier = Modifier.fillMaxSize(),
                        sheetOffsetY = sheetOffsetY,
                        screenHeightPx = screenHeightPx,
                        dismissThresholdPx = dismissThresholdPx,
                        onDismissPlayer = { dismissPlayer(it) },
                        onSnapBack = { snapBackToTop() }
                    )
                }
            }
        }
    }
}

/**
 * Main Center Page: Now Playing Vinyl Turntable Screen
 */
@Composable
private fun NowPlayingPageView(
    viewModel: PlayerViewModel,
    track: SieloTrack,
    playbackState: PlaybackState,
    onClose: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    sheetOffsetY: Animatable<Float, AnimationVector1D>,
    dismissThresholdPx: Float,
    onDismissPlayer: (velocity: Float) -> Unit,
    onSnapBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val shuffleAnimState = rememberShuffleAnimationState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                    },
                    onDragEnd = {
                        val velocityY = tracker.calculateVelocity().y
                        if (sheetOffsetY.value > dismissThresholdPx || velocityY > 1000f) {
                            onDismissPlayer(velocityY)
                        } else {
                            onSnapBack()
                        }
                    },
                    onDragCancel = {
                        onSnapBack()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (dragAmount > 0 || sheetOffsetY.value > 0) {
                            val newOffset = (sheetOffsetY.value + dragAmount).coerceAtLeast(0f)
                            coroutineScope.launch {
                                sheetOffsetY.snapTo(newOffset)
                            }
                            change.consume()
                        }
                    }
                )
            }
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Close / Down Arrow
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

            // Centered Title
            Text(
                text = "Now Playing",
                color = TextPrimary,
                fontFamily = SoraFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Top-right Repositioned Queue Button
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Queue",
                    tint = TextSecondary
                )
            }
        }

        // Center Vinyl Turntable
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            RotatingVinylCard(
                artworkUrl = track.thumbnailUrl,
                title = track.title,
                artist = track.artist,
                isPlaying = playbackState.isPlaying
            )
        }

        // Bottom Track Info & Full Tactile Controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title & Artist
            Text(
                text = track.title,
                color = TextPrimary,
                fontFamily = UrbanistFontFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = track.artist,
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Waveform Progress Bar
            WaveformProgressBar(
                currentPositionMs = playbackState.currentPositionMs,
                durationMs = playbackState.durationMs,
                isPlaying = playbackState.isPlaying,
                trackId = track.id,
                onSeek = { targetMs -> viewModel.seekTo(targetMs) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Tactile Controls Row: Lyrics, Prev, Play/Pause, Next, Shuffle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lyrics Button (Navigates to Left Page)
                IconButton(
                    onClick = onOpenLyrics,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Lyrics",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                Box(
                    modifier = Modifier
                        .size(54.dp)
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
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Tactile Center Play/Pause Button
                TactilePlayButton(
                    isPlaying = playbackState.isPlaying,
                    isBuffering = playbackState.isBuffering,
                    onClick = { viewModel.togglePlayPause() },
                    size = 72
                )

                // Next
                Box(
                    modifier = Modifier
                        .size(54.dp)
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
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Loop Button (Toggles single song looping on/off)
                IconButton(
                    onClick = {
                        viewModel.toggleLoop()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    val isLooping = playbackState.isLooping
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = if (isLooping) "Loop enabled" else "Loop disabled",
                        tint = if (isLooping) AccentCoral else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * Left Page: Dedicated Synchronized Lyrics Screen with Streamlined Bottom Bar
 */
@Composable
private fun LyricsPageView(
    viewModel: PlayerViewModel,
    track: SieloTrack,
    playbackState: PlaybackState,
    lyrics: SieloLyrics?,
    isLyricsLoading: Boolean,
    lyricOffsetMs: Long,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onReturnToVinyl: () -> Unit,
    sheetOffsetY: Animatable<Float, AnimationVector1D>,
    dismissThresholdPx: Float,
    onDismissPlayer: (velocity: Float) -> Unit,
    onSnapBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar (with swipe-down to dismiss)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            val velocityY = tracker.calculateVelocity().y
                            if (sheetOffsetY.value > dismissThresholdPx || velocityY > 1000f) {
                                onDismissPlayer(velocityY)
                            } else {
                                onSnapBack()
                            }
                        },
                        onDragCancel = {
                            onSnapBack()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (dragAmount > 0 || sheetOffsetY.value > 0) {
                                val newOffset = (sheetOffsetY.value + dragAmount).coerceAtLeast(0f)
                                coroutineScope.launch {
                                    sheetOffsetY.snapTo(newOffset)
                                }
                                change.consume()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Back to Vinyl
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to player",
                    tint = TextPrimary
                )
            }

            // Centered Title
            Text(
                text = "Lyrics",
                color = TextPrimary,
                fontFamily = SoraFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Right Queue Button
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Queue",
                    tint = TextSecondary
                )
            }
        }

        // Sync Fine-Tuning Pill
        AnimatedVisibility(
            visible = !lyrics?.lines.isNullOrEmpty(),
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
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

                val isLyricOffsetSaved by viewModel.isLyricOffsetSaved.collectAsState()

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLyricOffsetSaved && lyricOffsetMs != 0L) AccentCoral.copy(alpha = 0.15f) else SurfaceDark)
                        .border(
                            1.dp,
                            if (isLyricOffsetSaved && lyricOffsetMs != 0L) AccentCoral.copy(alpha = 0.6f) else BorderGlass,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.saveCurrentLyricOffset() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLyricOffsetSaved && lyricOffsetMs != 0L) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Saved",
                            tint = AccentCoral,
                            modifier = Modifier
                                .size(13.dp)
                                .padding(end = 2.dp)
                        )
                    }
                    Text(
                        text = if (lyricOffsetMs == 0L) {
                            if (isLyricOffsetSaved) "Sync: 0.0s" else "Save: 0.0s"
                        } else {
                            val sign = if (lyricOffsetMs > 0) "+" else ""
                            if (isLyricOffsetSaved) "Synced: ${sign}${lyricOffsetMs / 1000.0}s" else "Save: ${sign}${lyricOffsetMs / 1000.0}s"
                        },
                        color = if (lyricOffsetMs != 0L) AccentCoral else TextSecondary,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }

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

        // Center Lyrics Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
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
                val rawActiveIndex = lines.indexOfLast { (it.timestampMs + lyricOffsetMs) <= currentMs }
                val activeIndex = remember(rawActiveIndex, lines) {
                    if (rawActiveIndex in lines.indices && rawActiveIndex > 0) {
                        val currLine = lines[rawActiveIndex]
                        val prevLine = lines[rawActiveIndex - 1]
                        val timeDiff = kotlin.math.abs(currLine.timestampMs - prevLine.timestampMs)
                        if (timeDiff <= 2500L && isEnglishTranslationPair(candidateTranslation = currLine.text, candidateOriginal = prevLine.text)) {
                            rawActiveIndex - 1
                        } else {
                            rawActiveIndex
                        }
                    } else {
                        rawActiveIndex
                    }
                }

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
                    contentPadding = PaddingValues(top = 180.dp, bottom = 220.dp),
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

        // Compact Streamlined Bottom Bar
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
                // Mini Artwork + Song Metadata (Clicking smoothly takes back to Vinyl)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onReturnToVinyl() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SieloSongArtwork(
                        thumbnailUrl = track.thumbnailUrl,
                        title = track.title,
                        artist = track.artist,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = track.title,
                            color = TextPrimary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 14.sp,
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
                }

                // Compact Playback Buttons
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

private val HINGLISH_INDIC_WORDS = setOf(
    "dil", "ishq", "pyaar", "pyar", "tere", "teri", "tera", "mera", "meri", "mere",
    "hum", "tum", "tu", "hai", "ho", "hain", "hoon", "hun", "raat", "raatan",
    "naina", "aankhon", "aankhein", "ankhiyan", "akhiyan", "akhiyaan", "ankhiyaan",
    "zindagi", "sanam", "jaan", "khuda", "sukoon", "shiddat", "kasam", "duniya",
    "rooh", "apna", "apne", "apni", "saath", "chal", "chalo", "chale", "chalein",
    "kabhi", "kuch", "koi", "dard", "yaad", "yaadon", "yaadan", "rang", "soch",
    "ve", "de", "te", "yeh", "woh", "wo", "ye", "nahin", "nahi", "kyun", "kyon",
    "bol", "rabba", "khoya", "waqt", "mohabbat", "ehsaas", "mann", "man", "piya",
    "paas", "kudi", "munda", "yaari", "pind", "jatt", "sohneya", "sohna", "sohni",
    "wakhra", "gabru", "akh", "yaar", "taare", "vich", "khaali", "khali", "aa",
    "ke", "ajj", "aaj", "bharde", "haaye", "haye", "kalli", "kalla", "hoyi", "hoya",
    "gaiyan", "gaiyaan", "saari", "saare", "deed", "kar", "karde", "raahan", "rahan",
    "langh", "channa", "maahi", "mahi", "dhol", "bhangra", "jind", "jinde", "leja",
    "aaja", "jaana", "diwana", "deewana", "baatein", "baat", "chup", "reh", "raha",
    "rahi", "rahe", "hona", "tha", "thi", "the", "gaya", "gayi", "gaye", "karta",
    "karti", "karte", "karun", "karein", "kaun", "kahan", "yahan", "wahan", "jahan",
    "sab", "ek", "do", "teen", "chaar", "paanch", "din", "pal", "lamha", "lamhe",
    "suno", "dekho", "jao", "aao", "mil", "mila", "mile", "milke", "judai",
    "tujhe", "mujhe", "hamein", "tumhein", "unhein", "inhein", "ka", "ki", "ko",
    "se", "me", "mein", "par", "pe", "tak", "taaj", "taj", "choo", "lo", "choolo",
    "musafir", "muskurane", "alvida", "kasoor", "kesariya", "ghungroo", "bekhayali",
    "dhundhta", "bematlab", "parinda", "intezaar", "junoon", "dastaan", "nazrein",
    "hawayein", "badal", "saaya", "tamanna", "khushboo", "umeed", "jahan", "shikwa",
    "ranjha", "sawariya", "dooriyan", "chaand", "chand", "sitare", "khamoshi", "mishri"
)

private val ENGLISH_GRAMMAR_WORDS = setOf(
    "the", "a", "an", "is", "are", "was", "were", "of", "to", "in", "for", "on", "with",
    "at", "by", "from", "that", "this", "these", "those", "my", "your", "his", "her",
    "our", "their", "have", "has", "had", "will", "would", "shall", "should", "can",
    "could", "all", "please", "bless", "glimpse", "passed", "paths", "empty", "space",
    "fill", "today", "yesterday", "tomorrow", "heart", "alone", "become", "started",
    "loving", "come", "you", "me", "and", "or", "but", "so", "if", "not", "no", "yes",
    "what", "when", "where", "who", "why", "how", "give", "take", "make", "see", "hear"
)

private fun isEnglishTranslationPair(candidateTranslation: String, candidateOriginal: String): Boolean {
    val hasIndicScript = candidateOriginal.any { it in '\u0900'..'\u0D7F' }
    val origWords = candidateOriginal.lowercase()
        .replace(Regex("""[^\w\s]"""), " ")
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() }
    val origHinglishCount = origWords.count { it in HINGLISH_INDIC_WORDS }

    if (!hasIndicScript && origHinglishCount == 0) return false

    val transWords = candidateTranslation.lowercase()
        .replace(Regex("""[^\w\s]"""), " ")
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() }
    val transHinglishCount = transWords.count { it in HINGLISH_INDIC_WORDS }

    if (transHinglishCount > origHinglishCount) return false

    val transEnglishCount = transWords.count { it in ENGLISH_GRAMMAR_WORDS }
    return transEnglishCount >= 1
}



