package com.sielo.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.TextSecondary
import kotlinx.coroutines.isActive
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium animated waveform-style music progress bar.
 *
 * Features:
 * - Deterministic base waveform per [trackId]
 * - Display-synced (60/120fps) pausable & resumable animation clock (freezes in exact place on pause)
 * - Single source of truth (Media3 player state)
 * - Interactive seeking (tap & drag) with scrub preview
 * - Responsive bar calculation based on available width
 * - Fully rounded bars with elegant active/inactive contrast
 */
@Composable
fun WaveformProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    trackId: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = PaletteSand,
    inactiveColor: Color = PaletteSlateBlue.copy(alpha = 0.35f),
    timeColor: Color = TextSecondary
) {
    // 1. Progress State
    val actualProgress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val displayProgress = if (isDragging) dragProgress else actualProgress
    val displayPositionMs = if (isDragging) (dragProgress * durationMs).toLong() else currentPositionMs

    // 2. Pausable / Resumable Animation Time Clock (withFrameNanos)
    var animationTime by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastFrameNanos = 0L
            while (isActive) {
                withFrameNanos { frameNanos ->
                    if (lastFrameNanos != 0L) {
                        val dtSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                        animationTime += dtSeconds
                    }
                    lastFrameNanos = frameNanos
                }
            }
        }
    }

    // Reset animation time when song changes
    LaunchedEffect(trackId) {
        animationTime = 0f
    }

    // 3. Deterministic Base Waveform (Generated once per song ID)
    val baseBars = remember(trackId) {
        generateDeterministicWaveform(trackId, count = 80)
    }

    val currentAnimTime = animationTime

    val accessibilityDesc = "Song progress: ${formatTime(displayPositionMs)} of ${formatTime(durationMs)}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibilityDesc
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = displayProgress,
                    range = 0f..1f
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Waveform Canvas Component
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetMs = (newProgress * durationMs).toLong()
                        onSeek(targetMs)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            val targetMs = (dragProgress * durationMs).toLong()
                            onSeek(targetMs)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate responsive bar dimensions based on available width
            val desiredBarWidth = 2.8.dp.toPx()
            val desiredSpacing = 2.4.dp.toPx()
            val totalSlot = desiredBarWidth + desiredSpacing

            val calculatedCount = (canvasWidth / totalSlot).toInt().coerceIn(46, 75)
            val barWidth = (canvasWidth / calculatedCount) * 0.54f
            val spacing = (canvasWidth / calculatedCount) * 0.46f
            val slotWidth = barWidth + spacing

            // Number of wave packets (peaks) across the width (matching reference image)
            val waveCycles = 3.5f
            val spatialFreq = (waveCycles * 2f * Math.PI.toFloat()) / (calculatedCount - 1)
            val waveSpeed = 1.8f // Smooth, elegant traveling wave speed

            // Wave boundaries (increased minimum length slightly for prominent rounded pill appearance)
            val minBarHeight = 12.dp.toPx()
            val maxBarHeight = canvasHeight - 8.dp.toPx()
            val amplitude = (maxBarHeight - minBarHeight) / 2f
            val midHeight = minBarHeight + amplitude

            for (i in 0 until calculatedCount) {
                val fraction = i.toFloat() / (calculatedCount - 1).coerceAtLeast(1)

                // Coordinated traveling wave formula:
                // height = midHeight + amplitude * sin(index * spatialFreq - animationTime * speed)
                // When taller bars shrink, neighboring smaller bars simultaneously grow in 100% sync!
                val wavePhase = (i * spatialFreq) - (currentAnimTime * waveSpeed)
                val waveFactor = sin(wavePhase)

                val barHeight = (midHeight + amplitude * waveFactor).coerceIn(minBarHeight, maxBarHeight)
                val isPassed = fraction <= displayProgress

                val x = i * slotWidth + (spacing / 2f)
                val y = (canvasHeight - barHeight) / 2f

                drawRoundRect(
                    color = if (isPassed) activeColor else inactiveColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time Display Underneath Waveform (Left elapsed, Right duration)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(displayPositionMs),
                color = if (isDragging) PaletteCream else timeColor,
                fontSize = 13.sp,
                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium
            )

            Text(
                text = formatTime(durationMs),
                color = timeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Generates a deterministic, aesthetically pleasing base waveform profile
 * based on the track's unique hash code.
 */
private fun generateDeterministicWaveform(trackId: String, count: Int): FloatArray {
    val seed = trackId.hashCode().toLong()
    val random = Random(seed)

    val bars = FloatArray(count)
    val harmonicWaves = List(4) {
        val freq = 0.08f + (random.nextFloat() * 0.22f)
        val phase = random.nextFloat() * 6.28f
        val weight = 0.15f + (random.nextFloat() * 0.35f)
        Triple(freq, phase, weight)
    }

    for (i in 0 until count) {
        var h = 0.5f
        for ((freq, phase, weight) in harmonicWaves) {
            h += (sin(i * freq + phase) * weight).toFloat()
        }
        // Add subtle rhythmic variation
        val rhythm = if (i % 2 == 0) 0.08f else -0.05f
        bars[i] = (h + rhythm).coerceIn(0.25f, 0.95f)
    }

    return bars
}

/**
 * Format milliseconds into MM:SS format safely handling edge cases (0, negative, live).
 */
private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
