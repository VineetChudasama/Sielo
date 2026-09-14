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
import androidx.compose.material.icons.filled.Shuffle
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
import com.sielo.music.ui.components.AnimatedShuffleIcon
import com.sielo.music.ui.components.rememberShuffleAnimationState
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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onClose: () -> Unit = onBack,
    modifier: Modifier = Modifier,
    sheetOffsetY: Animatable<Float, AnimationVector1D>? = null,
    screenHeightPx: Float = 2000f,
    dismissThresholdPx: Float = 350f,
    onDismissPlayer: ((velocity: Float) -> Unit)? = null,
    onSnapBack: (() -> Unit)? = null
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val track = playbackState.currentTrack ?: return
    val queue = playbackState.queue
    val currentIndex = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: playbackState.queueIndex
    val upcomingTracks = if (queue.isNotEmpty() && currentIndex >= 0 && currentIndex + 1 < queue.size) {
        val currentTitleClean = track.title.trim()
        val currentArtistClean = track.artist.trim()
        queue.drop(currentIndex + 1).filter { qTrack ->
            qTrack.id != track.id &&
            !(qTrack.title.trim().equals(currentTitleClean, ignoreCase = true) &&
              qTrack.artist.trim().equals(currentArtistClean, ignoreCase = true))
        }.distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
    } else {
        emptyList()
    }

    val queueListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val shuffleAnimState = rememberShuffleAnimationState()
    var listScrolledInCurrentGesture by remember { mutableStateOf(false) }

    // Connect nested scroll to sheetOffsetY:
    // 1. Fast flings in the list NEVER dismiss the player.
    // 2. Long swipes that scroll list items NEVER dismiss the player.
    // 3. Only an intentional downward pull when ALREADY sitting at item 0 offset 0 pulls the sheet.
    // 4. If sheet was pulled down and user scrolls up, pull sheet back towards 0 first.
    val queueNestedScrollConnection = remember(sheetOffsetY, dismissThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val currentOffset = sheetOffsetY?.value ?: 0f
                if (currentOffset > 0f && available.y < 0f) {
                    val consumed = maxOf(available.y, -currentOffset)
                    coroutineScope.launch {
                        sheetOffsetY?.snapTo(currentOffset + consumed)
                    }
                    return Offset(0f, consumed)
                }
                // If the user is scrolling while not at the top, flag that this gesture belongs to the list
                if (queueListState.canScrollBackward) {
                    listScrolledInCurrentGesture = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // If any list scrolling occurred in this gesture, mark it so leftover drag never pulls the sheet
                if (kotlin.math.abs(consumed.y) > 0.5f) {
                    listScrolledInCurrentGesture = true
                }

                // ONLY pull sheet down if:
                // 1. Direct user finger input (NOT a fling / momentum!)
                // 2. The list was NOT scrolled during this gesture (user was already at the top from the start)
                // 3. The list is at the very top (!canScrollBackward)
                if (source == NestedScrollSource.UserInput &&
                    !listScrolledInCurrentGesture &&
                    !queueListState.canScrollBackward &&
                    available.y > 0f
                ) {
                    val currentOffset = sheetOffsetY?.value ?: 0f
                    coroutineScope.launch {
                        sheetOffsetY?.snapTo(currentOffset + available.y * 0.75f)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                listScrolledInCurrentGesture = false
                val currentOffset = sheetOffsetY?.value ?: 0f
                if (currentOffset > 0f) {
                    if (currentOffset > dismissThresholdPx) {
                        onDismissPlayer?.invoke(available.y) ?: onClose()
                    } else {
                        onSnapBack?.invoke()
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                listScrolledInCurrentGesture = false
                // Fast list flings hitting the top must NEVER dismiss the player!
                val currentOffset = sheetOffsetY?.value ?: 0f
                if (currentOffset > 0f) {
                    if (currentOffset > dismissThresholdPx) {
                        onDismissPlayer?.invoke(available.y) ?: onClose()
                    } else {
                        onSnapBack?.invoke()
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .nestedScroll(queueNestedScrollConnection)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header + Now Playing Card drag zone (pointer input for 1:1 downward drag)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onDragEnd = {
                                val velocityY = tracker.calculateVelocity().y
                                val currentOffset = sheetOffsetY?.value ?: 0f
                                if (currentOffset > dismissThresholdPx || velocityY > 1000f) {
                                    onDismissPlayer?.invoke(velocityY) ?: onClose()
                                } else {
                                    onSnapBack?.invoke()
                                }
                            },
                            onDragCancel = { onSnapBack?.invoke() },
                            onVerticalDrag = { change, dragAmount ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val currentOffset = sheetOffsetY?.value ?: 0f
                                if (dragAmount > 0 || currentOffset > 0) {
                                    val newOffset = (currentOffset + dragAmount).coerceAtLeast(0f)
                                    coroutineScope.launch {
                                        sheetOffsetY?.snapTo(newOffset)
                                    }
                                    change.consume()
                                }
                            }
                        )
                    }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UP NEXT (${upcomingTracks.size} SONGS)",
                        color = TextSecondary,
                        fontFamily = SoraFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (shuffleAnimState.highlight.value > 0.05f) AccentCoral.copy(alpha = 0.2f * shuffleAnimState.highlight.value)
                                else SurfaceDark
                            )
                            .border(
                                1.dp,
                                if (shuffleAnimState.highlight.value > 0.05f) AccentCoral.copy(alpha = 0.7f * shuffleAnimState.highlight.value)
                                else BorderGlass,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    viewModel.shuffleQueue()
                                    shuffleAnimState.playAnimation(durationMs = 1150)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeColor = androidx.compose.ui.graphics.lerp(TextSecondary, AccentCoral, shuffleAnimState.highlight.value)
                        AnimatedShuffleIcon(
                            size = 18.dp,
                            tint = activeColor,
                            progress = shuffleAnimState.progress.value
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shuffle",
                            color = activeColor,
                            fontFamily = SoraFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (upcomingTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark.copy(alpha = 0.3f))
                        .pointerInput(Unit) {
                            val tracker = VelocityTracker()
                            detectVerticalDragGestures(
                                onDragStart = { tracker.resetTracking() },
                                onDragEnd = {
                                    val velocityY = tracker.calculateVelocity().y
                                    val currentOffset = sheetOffsetY?.value ?: 0f
                                    if (currentOffset > dismissThresholdPx || velocityY > 1000f) {
                                        onDismissPlayer?.invoke(velocityY) ?: onClose()
                                    } else {
                                        onSnapBack?.invoke()
                                    }
                                },
                                onDragCancel = { onSnapBack?.invoke() },
                                onVerticalDrag = { change, dragAmount ->
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val currentOffset = sheetOffsetY?.value ?: 0f
                                    if (dragAmount > 0 || currentOffset > 0) {
                                        val newOffset = (currentOffset + dragAmount).coerceAtLeast(0f)
                                        coroutineScope.launch {
                                            sheetOffsetY?.snapTo(newOffset)
                                        }
                                        change.consume()
                                    }
                                }
                            )
                        }
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
                    state = queueListState,
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
                                text = qTrack.formattedDuration,
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
