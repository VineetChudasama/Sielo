package com.sielo.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sielo.music.R

/**
 * Custom celestial Echo navigation icon implementing the radiant dual-state soundwave artwork:
 * - Active State: Radiant gold and cyan celestial soundwave star emblem with subtle atmospheric glow pulse.
 * - Inactive State: Ethereal ice-blue soundwave star emblem.
 * Features a seamless morphing cross-fade transition between states.
 */
@Composable
fun EchoNavIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val activeProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "echoTransition"
    )

    val pulseAlpha = if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "EchoPulse")
        val p by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        p
    } else {
        1.0f
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Inactive Layer: Icy-blue soundwave star emblem
        if (activeProgress < 0.99f) {
            Image(
                painter = painterResource(id = R.drawable.ic_echo_inactive),
                contentDescription = "Echo Inactive",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - activeProgress)
                        scaleX = 0.92f + 0.08f * (1f - activeProgress)
                        scaleY = 0.92f + 0.08f * (1f - activeProgress)
                    }
            )
        }

        // Active Layer: Radiant golden-cyan celestial soundwave star emblem
        if (activeProgress > 0.01f) {
            Image(
                painter = painterResource(id = R.drawable.ic_echo_active),
                contentDescription = "Echo Active",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = activeProgress * pulseAlpha
                        scaleX = 0.92f + 0.08f * activeProgress
                        scaleY = 0.92f + 0.08f * activeProgress
                    }
            )
        }
    }
}

/**
 * Backward compatibility alias for OrbitNavIcon
 */
@Composable
fun OrbitNavIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    EchoNavIcon(isActive = isActive, modifier = modifier)
}

val EchoImageVector: ImageVector by lazy { OrbitImageVector }

val OrbitImageVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "EchoIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).build()
}
