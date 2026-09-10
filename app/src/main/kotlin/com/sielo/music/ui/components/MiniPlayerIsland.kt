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
import com.sielo.music.ui.theme.AccentCoral
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SurfaceDark
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
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
 * - Adheres strictly to the application's design system and theme colors.
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

    // Proportional dimensions: Player height ~64-68dp, Disc diameter 112dp (enlarged by 4dp)
    val discDiameter = 112.dp
    val cardStartOffset = 46.dp
    val contentStartPadding = 72.dp // Total content X = 46 + 72 = 118dp (> 112dp disc edge)

    // PlayerContainer: Host for the layered disc and player card
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // LAYER 1 (Bottom): Sleek, compact Rectangular Player Card extending to the right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = cardStartOffset) // Begins behind the disc center
                .zIndex(1f)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x33000000))
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderGlass, RoundedCornerShape(18.dp))
                .clickable { onClick() }
        ) {
            // Little Progress Bar sticking at the top edge, precisely mapped from the disc intersection (0%) to the right edge (100%)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp) // Starts precisely at the disc intersection so progress is visible immediately from 0:00
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(topStart = 1.5.dp, bottomStart = 1.5.dp))
                    .align(Alignment.TopCenter),
                color = AccentCoral,
                trackColor = PaletteSlateBlue.copy(alpha = 0.25f)
            )

            // Content Area: strictly reserved after disc overlap
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = contentStartPadding, end = 14.dp, top = 6.dp, bottom = 6.dp)
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
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.title,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
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
                            .size(26.dp)
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
                                TextSecondary.copy(alpha = 0.45f)
                            } else if (playbackState.isPlaying) {
                                AccentCoral
                            } else {
                                TextSecondary.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(1.dp))

                // 2. Playback Controls (Previous, Play/Pause, Next - enlarged)
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
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play / Pause Button
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentCoral,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onTogglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = AccentCoral,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Next Track Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .border(1.dp, BorderGlass, CircleShape)
                            .clickable { onSkipNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
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
                .shadow(elevation = 12.dp, shape = CircleShape, spotColor = Color(0x4D000000))
                .clip(CircleShape)
                .background(ObsidianBlack)
                .border(2.dp, BorderSubtle, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Spinning Album Artwork
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = "Vinyl Record Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationAngle.value % 360f)
            )

            // Outer vinyl groove ring overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color(0x26000000), CircleShape)
            )

            // Center Spindle Dot (matches theme surface)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.5.dp, BorderGlass, CircleShape)
            )
        }
    }
}


