package com.sielo.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
                    animationSpec = tween(durationMillis = 14000, easing = LinearEasing)
                )
            }
        }
    }

    // Dynamic Artwork Dominant / Vibrant Color for Ambient Glow & Border Light
    val dynamicGlowColor by rememberArtworkDominantColor(
        artworkUrl = artworkUrl,
        title = title,
        artist = artist,
        defaultColor = AccentCoral
    )

    // Smooth tonearm movement: 1f = playing (needle on disc), 0f = paused (needle swings out off the disc)
    val tonearmProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "tonearmProgress"
    )

    val artworkSize = 130.dp

    Box(
        modifier = modifier
            .size(310.dp),
        contentAlignment = Alignment.Center
    ) {
        val currentRotation = rotationAngle.value % 360f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val discCenterX = size.width * 0.50f
            val discCenterY = size.height * 0.50f
            val discCenter = Offset(discCenterX, discCenterY)
            val discRadius = size.minDimension * 0.42f
            val innerArtworkRadius = (artworkSize / 2).toPx()

            // 1. Ambient Atmospheric Glow behind the disc (vibrant artwork color)
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to dynamicGlowColor.copy(alpha = 0.50f),
                    0.55f to dynamicGlowColor.copy(alpha = 0.28f),
                    0.80f to dynamicGlowColor.copy(alpha = 0.10f),
                    1.0f to Color.Transparent,
                    center = discCenter,
                    radius = discRadius * 1.45f
                ),
                radius = discRadius * 1.45f,
                center = discCenter
            )

            // 2. Base Vinyl Disc Body
            drawCircle(
                color = Color(0xFF0C0D12),
                radius = discRadius,
                center = discCenter
            )

            // 3. Concentric Vinyl Micro-Grooves
            val grooveStart = innerArtworkRadius + 3.dp.toPx()
            val grooveEnd = discRadius - 4.dp.toPx()
            val totalGrooves = 26
            for (i in 0 until totalGrooves) {
                val fraction = i.toFloat() / (totalGrooves - 1)
                val r = grooveStart + (grooveEnd - grooveStart) * fraction
                val grooveAlpha = when {
                    i % 5 == 0 -> 0.40f
                    i % 2 == 0 -> 0.25f
                    else -> 0.15f
                }
                drawCircle(
                    color = Color(0xFF282C3A).copy(alpha = grooveAlpha),
                    radius = r,
                    center = discCenter,
                    style = Stroke(width = 1.3f)
                )
            }

            // Outer Vinyl Raised Bevel Rings
            drawCircle(
                color = Color(0xFF1B1D26),
                radius = discRadius - 2.dp.toPx(),
                center = discCenter,
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                color = Color(0xFF14151D),
                radius = discRadius - 4.5.dp.toPx(),
                center = discCenter,
                style = Stroke(width = 1.5f)
            )

            // 4. Anisotropic Disc Shine (Specular Glint Cones)
            // Two dual angular glossy fan/cone reflections matching the reference image
            val shineBrush = Brush.sweepGradient(
                0.00f to Color.Transparent,
                0.09f to Color(0x12FFFFFF),
                0.13f to Color(0x42FFFFFF),
                0.17f to Color(0x12FFFFFF),
                0.26f to Color.Transparent,
                0.50f to Color.Transparent,
                0.59f to Color(0x12FFFFFF),
                0.63f to Color(0x42FFFFFF),
                0.67f to Color(0x12FFFFFF),
                0.76f to Color.Transparent,
                1.00f to Color.Transparent,
                center = discCenter
            )
            drawCircle(
                brush = shineBrush,
                radius = discRadius - 2.dp.toPx(),
                center = discCenter
            )

            // 5. Glowing Border in the Color of the Music Artwork (Enhanced vibrant multi-layer edge glow)
            // Outer wide soft halo
            drawCircle(
                color = dynamicGlowColor.copy(alpha = 0.25f),
                radius = discRadius + 4.dp.toPx(),
                center = discCenter,
                style = Stroke(width = 8.dp.toPx())
            )
            // Mid diffused rim bloom
            drawCircle(
                color = dynamicGlowColor.copy(alpha = 0.55f),
                radius = discRadius + 1.5.dp.toPx(),
                center = discCenter,
                style = Stroke(width = 4.5.dp.toPx())
            )
            // Crisp glowing border stroke with specular highlight
            drawCircle(
                color = dynamicGlowColor.copy(alpha = 0.95f),
                radius = discRadius,
                center = discCenter,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Inner Album Label Bevel Border (Perfect concentric seam)
            drawCircle(
                color = Color(0xFF090A0E),
                radius = innerArtworkRadius + 1.5.dp.toPx(),
                center = discCenter,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Center Album Artwork Sticker (Rotates smoothly and is concentric with the disc)
        Box(
            modifier = Modifier
                .size(artworkSize)
                .align(Alignment.Center)
                .rotate(currentRotation),
            contentAlignment = Alignment.Center
        ) {
            SieloSongArtwork(
                thumbnailUrl = artworkUrl,
                title = title,
                artist = artist,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF101218), CircleShape),
                shape = CircleShape
            )

            // Center Spindle Hole Hub
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF08090D))
                    .border(1.5.dp, Color(0xFF383D4E), CircleShape)
            )
        }

        // 6. Disc Player Tonearm Line (Needle)
        // Stays on the vinyl disc when playing; smoothly swings out off the disc when paused.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val discCenterX = size.width * 0.50f
            val discCenterY = size.height * 0.50f
            val discRadius = size.minDimension * 0.42f

            // Pivot Mount at Top Right
            val pivotX = size.width * 0.92f
            val pivotY = size.height * 0.05f
            val pivot = Offset(pivotX, pivotY)

            // Needle on disc position (active playing groove at ~72% radius on the right)
            val activeStylus = Offset(
                x = discCenterX + discRadius * 0.72f,
                y = discCenterY + discRadius * 0.16f
            )

            // Rotation angle: 0 degrees when playing (needle on groove), -26 degrees when paused (swung out)
            val armSwingAngle = -26f * (1f - tonearmProgress)

            rotate(degrees = armSwingAngle, pivot = pivot) {
                // Top Pivot Mount Base (Chassis Socket)
                drawCircle(
                    color = Color(0xFF1D202A),
                    radius = 9.dp.toPx(),
                    center = pivot
                )
                drawCircle(
                    color = Color(0xFF4A5166),
                    radius = 9.dp.toPx(),
                    center = pivot,
                    style = Stroke(width = 1.8.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFF101218),
                    radius = 4.dp.toPx(),
                    center = pivot
                )

                // Smooth Curved Metallic Tonearm Line
                val armPath = Path().apply {
                    moveTo(pivotX, pivotY)
                    cubicTo(
                        x1 = pivotX - 2.dp.toPx(),
                        y1 = pivotY + (activeStylus.y - pivotY) * 0.42f,
                        x2 = activeStylus.x + (pivotX - activeStylus.x) * 0.28f,
                        y2 = activeStylus.y - (activeStylus.y - pivotY) * 0.32f,
                        x3 = activeStylus.x,
                        y3 = activeStylus.y - 12.dp.toPx()
                    )
                    lineTo(activeStylus.x, activeStylus.y)
                }

                // Shadow under tonearm
                drawPath(
                    path = armPath,
                    color = Color(0x60000000),
                    style = Stroke(
                        width = 4.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Metallic Arm Line
                drawPath(
                    path = armPath,
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF6B7285), Color(0xFF383C48), Color(0xFF555B6E)),
                        start = pivot,
                        end = activeStylus
                    ),
                    style = Stroke(
                        width = 2.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Sleek Cylindrical Cartridge / Headshell
                val cartridgeTop = Offset(activeStylus.x, activeStylus.y - 14.dp.toPx())
                val cartridgeBottom = Offset(activeStylus.x, activeStylus.y)
                drawLine(
                    color = Color(0xFF222632),
                    start = cartridgeTop,
                    end = cartridgeBottom,
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF4B5368),
                    start = cartridgeTop,
                    end = cartridgeBottom,
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF1E212B),
                    start = Offset(activeStylus.x, activeStylus.y - 12.dp.toPx()),
                    end = Offset(activeStylus.x, activeStylus.y - 2.dp.toPx()),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Glowing Stylus Bulb at Tip (warm incandescent amber light from reference)
                // 1. Soft Ambient Halo Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color(0xFFFFE082).copy(alpha = 0.65f),
                        0.50f to Color(0xFFFFB300).copy(alpha = 0.35f),
                        1.0f to Color.Transparent,
                        center = activeStylus,
                        radius = 16.dp.toPx()
                    ),
                    radius = 16.dp.toPx(),
                    center = activeStylus
                )
                // 2. Luminous Warm Bulb Body
                drawCircle(
                    color = Color(0xFFFFE599),
                    radius = 5.5.dp.toPx(),
                    center = activeStylus
                )
                // 3. Intense Brilliant White-Hot Core
                drawCircle(
                    color = Color(0xFFFFFDF5),
                    radius = 3.5.dp.toPx(),
                    center = activeStylus
                )
            }
        }
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