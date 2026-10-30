package com.sielo.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand

/**
 * A sleek, high-fidelity animated progress bar with smooth spring interpolation,
 * a multi-tone gradient fill, continuous light-sweep shimmer, and a luminous leading-edge glow.
 */
@Composable
fun SieloAnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    trackColor: Color = PaletteDarkNavy,
    borderColor: Color = BorderGlass,
    gradientColors: List<Color> = listOf(
        PaletteSand,
        Color(0xFFE9C46A),
        PaletteSageGreen
    )
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "sieloProgressBarProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "sieloProgressShimmer")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sieloProgressShimmerPhase"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sieloProgressPulseGlow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, borderColor, CircleShape)
    ) {
        if (animatedProgress > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(gradientColors))
                    .drawWithContent {
                        drawContent()
                        // Moving shimmer highlight beam across the filled portion
                        val barWidth = size.width
                        val shimmerX = barWidth * shimmerPhase
                        val beamWidth = (barWidth * 0.45f).coerceAtLeast(40f)

                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.35f * pulseGlow),
                                    Color.White.copy(alpha = 0.55f * pulseGlow),
                                    Color.White.copy(alpha = 0.35f * pulseGlow),
                                    Color.Transparent
                                ),
                                startX = shimmerX - beamWidth / 2f,
                                endX = shimmerX + beamWidth / 2f
                            ),
                            size = size
                        )
                    }
            ) {
                // Luminous glowing head dot at the leading edge
                if (animatedProgress in 0.04f..0.98f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                            .size(height - 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f * pulseGlow))
                    )
                }
            }
        }
    }
}

/**
 * An elegant indeterminate progress bar with a continuous harmonic wave sweep.
 * Ideal for metadata fetching and connection states.
 */
@Composable
fun SieloIndeterminateProgressBar(
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    trackColor: Color = PaletteDarkNavy,
    borderColor: Color = BorderGlass,
    accentColors: List<Color> = listOf(
        PaletteSand,
        Color(0xFFE9C46A),
        PaletteSageGreen
    )
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sieloIndeterminate")
    val sweepPhase by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sieloIndeterminatePhase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, borderColor, CircleShape)
            .drawWithContent {
                drawContent()
                val barWidth = size.width
                val centerX = barWidth * sweepPhase
                val waveWidth = barWidth * 0.5f

                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accentColors[0].copy(alpha = 0.3f),
                            accentColors[1].copy(alpha = 0.85f),
                            accentColors[2].copy(alpha = 0.95f),
                            accentColors[1].copy(alpha = 0.85f),
                            accentColors[0].copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        startX = centerX - waveWidth / 2f,
                        endX = centerX + waveWidth / 2f
                    ),
                    size = size
                )
            }
    )
}
