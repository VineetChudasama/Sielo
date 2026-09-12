package com.sielo.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sielo.music.ui.theme.AccentCoral
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SurfaceDark
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = AccentCoral
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val anim3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(anim1, anim2, anim3).forEach { heightFraction ->
            val h = if (isPlaying) (18 * heightFraction).dp else 4.dp
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun TactilePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 72
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (isPlaying) AccentCoral else SurfaceElevated)
            .border(1.dp, if (isPlaying) AccentCoral else BorderGlass, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = if (isPlaying) ObsidianBlack else TextPrimary,
            modifier = Modifier.size((size * 0.45).dp)
        )
    }
}

@Composable
fun WaveformSeekbar(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val barCount = 46

    // Using Animatable to preserve phase continuously when paused so waveform freezes in place without jumping or morphing
    val phaseAnimatable = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                phaseAnimatable.animateTo(
                    targetValue = phaseAnimatable.value + (2f * Math.PI.toFloat()),
                    animationSpec = tween(durationMillis = 2800, easing = LinearEasing)
                )
            }
        }
    }

    val currentPhase = phaseAnimatable.value

    // Uniform rhythmic studio audio bars
    val rhythmPattern = remember {
        floatArrayOf(0.42f, 0.68f, 0.92f, 0.60f, 0.85f, 1.0f, 0.72f, 0.52f, 0.88f, 0.65f, 0.78f, 0.48f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val barWidth = width / barCount
        val barDrawWidth = barWidth * 0.58f

        for (i in 0 until barCount) {
            val fraction = i.toFloat() / barCount

            // Symmetrical envelope: balanced and uniform across the seeking line
            val normPos = i.toFloat() / (barCount - 1)
            val distFromCenter = abs(normPos - 0.5f) * 2f
            val envelope = (1f - 0.35f * distFromCenter * distFromCenter).coerceIn(0.6f, 1f)

            // Base structured height from studio rhythm pattern
            val baseRhythm = rhythmPattern[i % rhythmPattern.size]
            val baseFraction = 0.28f + (0.62f * baseRhythm * envelope)

            // Dynamic wave ripple modulation (freezes seamlessly when paused)
            val waveMod = sin(i * 0.40f + currentPhase) * 0.16f
            val totalFraction = (baseFraction + waveMod).coerceIn(0.18f, 1f)

            val waveHeight = (totalFraction * (height - 8.dp.toPx())).coerceAtLeast(6.dp.toPx())
            val isPassed = fraction <= progress

            val x = i * barWidth + (barWidth - barDrawWidth) / 2
            val y = (height - waveHeight) / 2

            drawRoundRect(
                color = if (isPassed) AccentCoral else PaletteSlateBlue.copy(alpha = 0.35f),
                topLeft = Offset(x, y),
                size = Size(barDrawWidth, waveHeight),
                cornerRadius = CornerRadius(barDrawWidth / 2, barDrawWidth / 2)
            )
        }
    }
}

@Composable
fun RotatingVinylCard(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    title: String? = "",
    artist: String? = ""
) {
    val rotationAngle = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                rotationAngle.animateTo(
                    targetValue = rotationAngle.value + 360f,
                    animationSpec = tween(durationMillis = 12000, easing = LinearEasing)
                )
            }
        }
    }

    Box(
        modifier = modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF16161D),
                radius = size.minDimension / 2
            )
            drawCircle(color = Color(0xFF202028), radius = size.minDimension / 2.2f, style = Stroke(2f))
            drawCircle(color = Color(0xFF262632), radius = size.minDimension / 2.6f, style = Stroke(1.5f))
            drawCircle(color = Color(0xFF202028), radius = size.minDimension / 3.2f, style = Stroke(2f))
        }

        SieloSongArtwork(
            thumbnailUrl = artworkUrl,
            title = title,
            artist = artist,
            modifier = Modifier
                .size(130.dp)
                .rotate(rotationAngle.value % 360f)
                .border(2.dp, ObsidianBlack, CircleShape),
            shape = CircleShape
        )

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(ObsidianBlack)
                .border(1.dp, BorderGlass, CircleShape)
        )
    }
}

@Composable
fun HiFiTimeDisplay(
    currentMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatTime(currentMs),
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = formatTime(durationMs),
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}