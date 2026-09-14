package com.sielo.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sielo.music.ui.theme.MacondoFontFamily
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteSand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SieloLaunchReveal(
    onFinish: () -> Unit
) {
    val brandAlpha = remember { Animatable(0f) }
    val brandScale = remember { Animatable(0.86f) }
    val bgAlpha = remember { Animatable(1f) }
    var eTransformOrigin by remember { mutableStateOf(TransformOrigin(0.51f, 0.54f)) }

    LaunchedEffect(Unit) {
        // Phase 1: Appear smoothly in the center
        launch {
            brandAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
        launch {
            brandScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 650, easing = EaseOutCubic)
            )
        }

        // Phase 2: Brief elegant hold to display the brand name
        delay(750)

        // Phase 3: Cinematic Zoom towards the center of 'e' & dissolve to reveal the app
        launch {
            // Zoom forward into the center of 'e'
            brandScale.animateTo(
                targetValue = 4.2f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
        }
        launch {
            // Brand text fades away as it expands past the screen
            brandAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 520, delayMillis = 40, easing = FastOutSlowInEasing)
            )
        }
        launch {
            // Background veil dissolves away, revealing the app
            bgAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 650, delayMillis = 60, easing = FastOutSlowInEasing)
            )
        }

        delay(720)
        onFinish()
    }

    // Full-screen overlay that gracefully fades out
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(bgAlpha.value)
            .background(ObsidianBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to skip if desired
                onFinish()
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind the brand name, zooming along with 'e'
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = brandAlpha.value * 0.45f
                    scaleX = brandScale.value
                    scaleY = brandScale.value
                    transformOrigin = eTransformOrigin
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PaletteSand.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Centered Brand Name in Macondo font, targeted on 'e'
        Text(
            text = "Sielo",
            color = PaletteCream,
            fontSize = 62.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = MacondoFontFamily,
            letterSpacing = 2.5.sp,
            onTextLayout = { textLayoutResult ->
                // "Sielo" has 'e' at index 2
                if (textLayoutResult.layoutInput.text.length > 2) {
                    val eBox = textLayoutResult.getBoundingBox(2)
                    val w = textLayoutResult.size.width.toFloat()
                    val h = textLayoutResult.size.height.toFloat()
                    if (w > 0f && h > 0f) {
                        eTransformOrigin = TransformOrigin(
                            pivotFractionX = eBox.center.x / w,
                            pivotFractionY = eBox.center.y / h
                        )
                    }
                }
            },
            modifier = Modifier.graphicsLayer {
                alpha = brandAlpha.value
                scaleX = brandScale.value
                scaleY = brandScale.value
                transformOrigin = eTransformOrigin
            }
        )
    }
}
