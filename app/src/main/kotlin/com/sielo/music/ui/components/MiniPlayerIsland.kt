package com.sielo.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.sielo.music.core.audio.model.PlaybackState
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import kotlinx.coroutines.isActive
import kotlin.math.sin

/**
 * Structural Music Player Component
 *
 * Architecture:
 * - Oversized rotating vinyl disc on the LEFT (Z-Index Top, D > H, extends beyond top/bottom/left).
 * - Rectangular Player Card extending to the RIGHT (Z-Index Bottom, starts behind disc center).
 * - Internal Content Area padded past the disc overlap (Song Info -> Controls).
 * - Continuous pausable/resumable rotation synced with playback state.
 * - Glowing luminous border & elevation adhering strictly to Sielo palette.
 */
@Composable
fun MiniPlayerIsland(
    playbackState: PlaybackState,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
    onToggleMute: () -> Unit = {}
) {
    val track = playbackState.currentTrack ?: return

    val progress = if (playbackState.durationMs > 0) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // 1. Continuous Pausable/Resumable Disc Rotation
    val rotationAngle = remember { Animatable(0f) }
    LaunchedEffect(playbackState.isPlaying) {
        if (playbackState.isPlaying) {
            while (isActive) {
                rotationAngle.animateTo(
                    targetValue = rotationAngle.value + 360f,
                    animationSpec = tween(durationMillis = 8000, easing = LinearEasing)
                )
            }
        }
    }

    // Proportional dimensions: Player height ~68dp, Disc diameter 116dp (increased by 4dp)
    val discDiameter = 116.dp
    val cardStartOffset = 46.dp
    val contentStartPadding = 74.dp // Total content X = 46 + 74 = 120dp (> 116dp disc edge)

    // PlayerContainer: Host for the layered disc and player card
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // LAYER 1 (Bottom): Glowing Rectangular Player Card extending to the right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = cardStartOffset) // Begins behind the disc center
                .zIndex(1f)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = PaletteSand.copy(alpha = 0.3f),
                    spotColor = PaletteSand.copy(alpha = 0.2f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            PaletteOxfordBlue,
                            Color(0xFF202E44),
                            PaletteOxfordBlue
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            PaletteSlateBlue.copy(alpha = 0.45f),
                            PaletteSand.copy(alpha = 0.55f),
                            PaletteSlateBlue.copy(alpha = 0.45f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { onClick() }
        ) {
            // Custom Progress Bar: starts 0.4dp behind (41.6dp), no end dots
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 41.6.dp, end = 20.dp, top = 2.dp)
                    .height(2.5.dp)
                    .align(Alignment.TopCenter)
            ) {
                val barWidth = size.width
                val barHeight = size.height
                val cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)

                // 1. Inactive background track (clean, uniform, no end dots)
                drawRoundRect(
                    color = PaletteSlateBlue.copy(alpha = 0.35f),
                    topLeft = Offset.Zero,
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )

                // 2. Active played progress bar (advances immediately from 0:00 right from the disc intersection)
                if (progress > 0f) {
                    val activeWidth = (barWidth * progress).coerceIn(barHeight, barWidth)
                    drawRoundRect(
                        color = PaletteSand,
                        topLeft = Offset.Zero,
                        size = Size(activeWidth, barHeight),
                        cornerRadius = cornerRadius
                    )
                }
            }

            // Content Area: strictly reserved after disc overlap
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = contentStartPadding, end = 16.dp, top = 8.dp, bottom = 8.dp)
            ) {
                // 1. Song Information (Artist & Title with Marquee & Sound Mute Toggle Button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = track.artist,
                            color = PaletteSageGreen, // #778D7A
                            fontFamily = com.sielo.music.ui.theme.UrbanistFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.title,
                            color = PaletteCream, // #F4F1DE
                            fontFamily = com.sielo.music.ui.theme.UrbanistFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    velocity = 35.dp,
                                    initialDelayMillis = 1200,
                                    repeatDelayMillis = 1200
                                )
                        )
                    }

                    // Sound / Mute Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onToggleMute() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (playbackState.isMuted) "Unmute" else "Mute",
                            tint = if (playbackState.isMuted) {
                                PaletteSlateBlue.copy(alpha = 0.45f)
                            } else if (playbackState.isPlaying) {
                                PaletteSand
                            } else {
                                PaletteSlateBlue
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 2. Playback Controls (Previous, Play/Pause, Next)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onSkipPrevious() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = PaletteCream, // #F4F1DE
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play / Pause Button (Luminous Cream filled circle with dark icon matching reference)
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = PaletteSand,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = PaletteSand.copy(alpha = 0.4f))
                                .clip(CircleShape)
                                .background(PaletteCream) // #F4F1DE
                                .clickable { onTogglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = PaletteDarkNavy, // #0D1B2A
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Next Track Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onSkipNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = PaletteCream, // #F4F1DE
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // LAYER 2 (Top): Oversized Rotating Vinyl Disc on the Left
        Box(
            modifier = Modifier
                .size(discDiameter)
                .offset(x = 0.dp)
                .zIndex(2f) // Visually sits ABOVE the player box
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = PaletteSand.copy(alpha = 0.25f),
                    spotColor = PaletteDarkNavy.copy(alpha = 0.8f)
                )
                .clip(CircleShape)
                .background(PaletteDarkNavy) // #0D1B2A
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            PaletteSageGreen.copy(alpha = 0.7f),
                            PaletteSand.copy(alpha = 0.6f),
                            PaletteSageGreen.copy(alpha = 0.7f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Spinning Album Artwork
            SieloSongArtwork(
                thumbnailUrl = track.thumbnailUrl,
                title = track.title,
                artist = track.artist,
                contentDescription = "Vinyl Record Artwork",
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationAngle.value % 360f),
                shape = CircleShape
            )

            // Outer vinyl groove ring overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, PaletteDarkNavy.copy(alpha = 0.35f), CircleShape)
            )

            // Center Vinyl Label & Spindle Dot
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(PaletteDarkNavy.copy(alpha = 0.85f))
                    .border(1.dp, PaletteSlateBlue.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Center Spindle Dot
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(PaletteSand) // #D4C4A8
                )
            }
        }
    }
}


